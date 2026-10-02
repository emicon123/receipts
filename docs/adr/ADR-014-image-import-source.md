# ADR-014: Imported Images as a Distinct `IMAGE_IMPORT` Receipt Source (Phone-First Gallery Import)

**Date:** 2026-10-02
**Status:** Accepted

**Context:** The user wants a second way to feed an image into the daily classification batch
besides taking a photo in the app: **import an image that already exists on the device**. The app
is used almost exclusively on a phone (an iPhone Safari PWA and an Android Chrome PWA), so the
primary path is **picking from the phone gallery** ("zdjęcie z galerii"); pasting a screenshot
from the clipboard is secondary, and desktop paste/drag-and-drop is a nice-to-have. From the moment
it is uploaded the image behaves exactly like a photographed receipt: stored `PENDING`, classified
by the single 06:00 `classify-receipts.sh` run, correctable line by line, counted in the dashboard.

Two facts shape the design:
- **An imported image is not necessarily a screenshot.** The gallery holds screenshots of digital
  receipts (store-app e-receipts, Allegro/Glovo/Wolt order summaries, BLIK confirmations, captured
  invoices) *and* photos of paper receipts that were taken earlier. Naming the source after one of
  them would be wrong for the other, and the classifier cannot rely on the source to know which
  rules apply.
- **Phone gallery pickers differ by platform.** iOS Safari hands over Photo Library images as JPEG
  (it transcodes HEIC for a file input), but a HEIC chosen via the Files app can arrive untouched;
  Android pickers can return HEIC/HEIF (notably some Samsung devices), and a restrictive `accept`
  list hides those files or routes the user to the generic Files UI instead of the gallery. The
  backend accepts only JPEG, PNG and WebP.

Six questions needed answers: how to record the origin, what the upload endpoint looks like, how
the wrapper script learns the origin and the file type, what the PWA entry point looks like, how to
cope with image formats the backend rejects, and how to migrate the Postgres enum safely.

**Decision:**

### 1. A new enum value `receipt_source_enum = IMAGE_IMPORT`, named for the channel, not the content

`source` is already this schema's "where did this come from" field (`CAMERA`, `MANUAL`;
`BANK_IMPORT` design-only, ADR-007). `CAMERA` means "captured live by the camera input";
`IMAGE_IMPORT` means "an existing image the user imported from the gallery, file picker or
clipboard". It is **image-backed like `CAMERA`** (always has an `image_path`), is created
`PENDING`, and follows the identical `PENDING → PROCESSING → PROCESSED | FAILED` lifecycle,
reprocess and delete rules. Nothing in the backend branches on it except the upload factory and the
pending list; it exists to carry the origin to the classifier and to the UI. It is deliberately
**a hint about content, not a guarantee**: the classifier prompt tells Claude to look at the image —
a screenshot of a digital receipt gets the "Screenshots" rules; a photographed paper paragon, even
when imported, gets the normal photo-receipt rules including the VAT-letter cross-check.

| Candidate | Verdict | Why |
|---|---|---|
| Reuse `CAMERA` for imports | rejected | The origin cannot be recovered afterwards once rows are mixed, and the classifier hint and UI label both need to tell live captures from imports. |
| `SCREENSHOT` (an earlier draft of this ADR) | rejected | Names a content type the value does not guarantee: the main phone path imports photos of paper receipts from the gallery too. A later "show my screenshots" filter or analytics would silently be wrong. |
| `IMAGE_IMPORT` | **adopted** | Names the channel (and is parallel to the design-only `BANK_IMPORT`), is always true, and still gives the classifier the one hint it needs: "not a live camera shot, check what it is". |
| Bare `IMPORT` | rejected | Ambiguous next to `BANK_IMPORT`. |
| Separate `capture_kind` column / boolean | rejected | A second column that duplicates what `source` already says; two fields to keep consistent. |
| Infer content from file type (PNG) or filename | rejected | Unreliable (Android screenshots may be JPEG, gallery photos can be PNG/WebP), and a pasted blob has no meaningful filename. |

In the UI, a receipt without a store name shows a fallback title by source: `CAMERA → "Paragon"`,
`IMAGE_IMPORT → "Zaimportowany obraz"`, `MANUAL → "Wpis ręczny"`.

### 2. A dedicated `POST /api/receipts/image-import`; not a `source` field on `POST /api/receipts`

Same multipart shape (`image` part, optional `capturedAt`), same 20 MB limit, same JPEG/PNG/WebP
allow-list, same `UnsupportedImageTypeException` → `422`, same `201 ReceiptSummaryResponse`. The
only difference is the stored `source`. It mirrors `POST /receipts/manual` (one path per origin).

| Candidate | Verdict | Why |
|---|---|---|
| `POST /receipts/image-import` | **adopted** | Existing camera contract stays byte-for-byte unchanged; each endpoint creates exactly one source, so there is no client-supplied enum to validate; `operationId`s stay unambiguous. |
| Optional `source` form field on `POST /receipts` | rejected | The client could submit `MANUAL`/`BANK_IMPORT`, forcing a "which values are legal here" rule; no upside beyond one fewer route. |
| Strategy / pluggable per-source upload handlers | rejected | YAGNI. The two paths differ by one enum constant: `ReceiptService.uploadImageReceipt(image, capturedAt, source)` plus a `Receipt.newImageUpload(source, …)` factory cover both (`02-domain-model-and-schema.md` § Image Upload Paths). |

`capturedAt` is optional and the PWA omits it for imports, so the server defaults it to the upload
time. An imported image is often days old, so the classifier overrides the date part with the
purchase date shown in the image when one is legible (the existing `classification-batch` rule,
unchanged).

### 3. How the script learns the origin and the file type

- **Origin:** `GET /receipts/pending` returns `{id, source}` for every entry. `source` was already
  *required* in the OpenAPI `PendingReceiptRef` schema; the backend DTO returned `id` only, so this
  change makes the implementation match the spec. The script writes it onto the image's manifest
  line as `source=<CAMERA|IMAGE_IMPORT>` (assuming `CAMERA` if absent), and
  `infra/classify/prompt.md` uses it as a hint — it still tells Claude to decide from what the
  image shows.
- **File type:** the script derives the temp file's extension from the **`Content-Type` of the
  `GET /receipts/{id}/image` response** (`image/jpeg → .jpg`, `image/png → .png`,
  `image/webp → .webp`), not from the pending payload. Today every image is saved as
  `receipt-<id>.jpg` regardless of its real type, which would hand a PNG screenshot to the `Read`
  tool under a `.jpg` name. The backend already returns the correct media type (derived from the
  stored file's extension).
- Rejected: adding `imageContentType`/`imageExtension` to the pending payload. It would duplicate
  what the image response already states authoritatively and widen a deliberately lean endpoint.

### 4. Frontend entry point: actions on the existing capture screen — no tabs, no new route

`/` stays the camera home action; the big camera button is unchanged. Directly beneath it the
capture screen gets a **prominent full-width "Wybierz z galerii" button** (the primary import path
on a phone), and a smaller secondary **"Wklej ze schowka"** action plus a one-line hint that
Ctrl/Cmd+V also works. No new route and no fifth bottom-nav item (the nav is a full 4-column grid).
All paths — camera, gallery, clipboard button, `paste` event, optional drag-and-drop — converge on
the same preview → "Powtórz" / "Zatwierdź" confirm step and one upload mutation; only the endpoint
differs. Full structure in `04-classification-flow.md` § Capture Entry Points.

| Candidate | Verdict | Why |
|---|---|---|
| Inline actions on `/` (camera button + gallery button + paste) | **adopted** | The gallery is the main phone path and must be one tap away, not behind a second control; shares the preview/confirm/upload/error UI; zero nav churn. |
| Segmented control / tabs ("Aparat" \| "Zrzut ekranu") — the earlier draft | rejected | Hides the primary phone path behind a second tap, and adds mode state for no benefit now that gallery and camera sit side by side. |
| New route `/import` + 5th nav item | rejected | Crowds the 4-item bottom nav; duplicates the shared confirm UI behind a second route. |
| New route reached by a link from `/` | rejected | An extra hop for no gain. Revisit only if the Web Share Target follow-up needs a deep-linkable landing route. |

### 5. `accept="image/*"` plus client-side normalisation to JPEG; no backend conversion

- The gallery input is `<input type="file" accept="image/*">` with **no `capture` attribute** (the
  camera input keeps `accept="image/*" capture="environment"`). `image/*` — not a
  `png,jpeg,webp` list — because on iOS it yields the Photo Library / Choose File sheet with
  automatic JPEG transcoding for library photos, and on Android it opens the system
  gallery/photo picker *and shows HEIC/HEIF files* instead of hiding them.
- **Normalisation helper** (frontend, pure + unit-testable) runs on every imported file, whether
  from the picker, the clipboard or a drop: if `file.type` is `image/jpeg`, `image/png` or
  `image/webp` it passes through **unchanged** (original bytes, no recompression). Anything else
  — HEIC/HEIF, GIF, BMP, AVIF, or an empty/unknown type — is decoded with `createImageBitmap`
  (EXIF orientation applied), scaled so its longer side is at most 4096 px, painted on a canvas
  over a white background and re-encoded with `canvas.toBlob("image/jpeg", 0.92)`, then renamed
  `*.jpg`. If decoding or encoding fails, show a Polish error ("Nie udało się odczytać tego
  formatu obrazu (np. HEIC). Wybierz zdjęcie JPEG/PNG lub zrób zrzut ekranu.") and create no
  draft. A file larger than 20 MB after this step gets a Polish size error client-side instead of
  a server failure. A `422` from the upload is mapped to the same format message as a last-resort
  guard.
- **Backend: no extra defence, deliberately.** Server-side HEIC→JPEG conversion is not trivial or
  free here (it needs native libheif or an extra imaging plugin in the ARM64 backend image) — out
  of scope, listed as a follow-up. A magic-byte check of the declared type was considered and
  rejected for now: it would break the existing upload tests (they send placeholder bytes under
  `image/jpeg`), and a mislabelled or undecodable file already degrades safely — the classifier
  reports it in `failures` and the receipt lands in `FAILED` with a reason, recoverable by delete
  or reprocess. The backend's existing allow-list + `422` remains the authoritative gate.

### 6. Two Flyway migrations, not one

`ALTER TYPE … ADD VALUE` can run inside a transaction, but PostgreSQL refuses to *use* the new
value (here: in a CHECK constraint) until that transaction has committed (`55P04 unsafe use of new
value`). Flyway runs each versioned migration in its own transaction, so:

- `V3__receipt_source_image_import.sql` — only `ALTER TYPE receipt_source_enum ADD VALUE IF NOT EXISTS 'IMAGE_IMPORT'`.
- `V4__receipt_image_path_image_import_check.sql` — drops and re-adds `receipts_image_path_matches_source`
  as `(source IN ('CAMERA','IMAGE_IMPORT') AND image_path IS NOT NULL) OR (source = 'MANUAL' AND image_path IS NULL)`
  (same constraint name), plus a `COMMENT ON COLUMN receipts.source`.

`V1`/`V2` are not edited (their Flyway checksums are recorded in the deployed database).
`spring.flyway.group` must stay at its default `false`; grouping V3 and V4 into one transaction
would break V4. Verified by applying V1–V4 in order, each in its own transaction, to a real
PostgreSQL 16 server (including a negative control showing the combined single-transaction form
fails); the Backend agent's Testcontainers run against PostgreSQL 17 is the authoritative check.

**Consequences:**
- Existing `CAMERA` rows are untouched; there is no backfill and none is wanted.
- The classifier prompt grows by one section and now has to look before applying rules. The daily
  run is still exactly one `claude -p` invocation covering the whole batch; imports only add
  images to it.
- `classification-batch` needs no change: imported images arrive as `items`/`failures` like photos,
  and an `IMAGE_IMPORT` id in `uncertainCategory` is invalid classifier output, tolerated (skipped,
  reported) exactly like a `CAMERA` id (ADR-007's rule is "any non-`BANK_IMPORT` source").
- Spending summary/trend/line-items endpoints are unaffected; the dashboard counts imported
  receipts like any other `PROCESSED` receipt.
- The same purchase captured twice (a photo and a screenshot) is not de-duplicated; ADR-007's dedup
  is bank-transaction-vs-receipt only, and `IMAGE_IMPORT` receipts are included in its match set
  alongside `CAMERA`/`MANUAL`. A user who imports both deletes one (`DELETE /receipts/{id}`).
- Non-purchase images (chat, meme) land in `FAILED` with a reason and can be deleted or re-queued
  from the receipt detail screen like any other failure.
- The `GET /receipts?source=` filter is declared in the OpenAPI spec but not yet implemented by the
  backend; it is not needed by this feature (`IMAGE_IMPORT` is simply one more valid value once it
  is).
- **Scope:** one image is one receipt entry. Multi-select from the gallery (`multiple` on the
  input, one upload per file) and stitched long screenshots are follow-ups, not built here.
- **Follow-up, deferred: PWA Web Share Target** ("Share → Paragony" straight from the phone
  gallery). On a phone it is the natural flow, but it is deferred because (a) it works on Android
  Chrome only — iOS Safari does not support Web Share Target — and the user is on both; (b) it
  needs a `share_target` entry in the web-app manifest plus a service-worker `fetch` handler that
  accepts the multipart POST and forwards it to `POST /receipts/image-import`, which means moving
  `vite-plugin-pwa` to `injectManifest` mode and getting the service worker's scope, the manifest's
  `share_target.action` and the nginx routing right under the `/paragony/` prefix and
  investing-app's shared nginx; and (c) the gallery picker already covers both platforms. The
  dedicated import endpoint is already the right target for it.
- **Follow-up, deferred: server-side HEIC/HEIF conversion**, should the client-side
  normalisation prove insufficient on real devices (see decision 5).
