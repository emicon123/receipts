# ADR-014: Screenshots as a Distinct `SCREENSHOT` Receipt Source

**Date:** 2026-10-02
**Status:** Accepted

**Context:** The user wants a second way to feed an image into the daily classification batch
besides the camera: **paste** a screenshot from the clipboard or **pick** an existing image file
(gallery / file picker) — a store-app e-receipt, an online-order summary (Allegro, Glovo, Wolt),
a bank-app/BLIK confirmation, a captured PDF/webpage invoice. From the moment it is uploaded it
must behave exactly like a photographed receipt: stored `PENDING`, classified by the single
06:00 `classify-receipts.sh` run, correctable line by line, counted in the dashboard.

A screenshot is not a printed paragon, so the classifier needs a different hint (no VAT letters,
delivery/service fees, discounts, UI chrome, partial captures, non-receipt images) and the UI
wants a different label. Five questions needed answers: how to record the origin, what the upload
endpoint looks like, how the wrapper script learns the origin and the file type, where the entry
point lives in the PWA, and how to migrate the Postgres enum safely.

**Decision:**

### 1. A new enum value `receipt_source_enum = SCREENSHOT`; do not reuse `CAMERA`

`source` is already this schema's "where did this come from" field (`CAMERA`, `MANUAL`;
`BANK_IMPORT` design-only, ADR-007). A screenshot is a new origin, so it gets a new value.
`SCREENSHOT` is **image-backed like `CAMERA`** (always has an `image_path`), is created `PENDING`,
and follows the identical `PENDING → PROCESSING → PROCESSED | FAILED` lifecycle, reprocess and
delete rules. Nothing in the backend branches on it except the upload factory and the pending
list; it exists to carry the origin to the classifier and to the UI.

| Candidate | Verdict | Why |
|---|---|---|
| Reuse `CAMERA` for screenshots | rejected | The origin cannot be recovered afterwards (no backfill is possible once rows are mixed), and the classifier prompt and UI both need to tell the two apart. |
| New `SCREENSHOT` enum value | **adopted** | One enum value and one CHECK extension; reuses the field that already means "origin". |
| Separate `capture_kind` column / boolean `is_screenshot` | rejected | A second column that duplicates what `source` already says; two fields to keep consistent. |
| Infer "screenshot" from the file type (PNG) or filename | rejected | Unreliable (Android screenshots may be JPEG, a camera can emit WebP/PNG), and a pasted blob has no meaningful filename. |

### 2. A dedicated `POST /api/receipts/screenshot`; not a `source` field on `POST /api/receipts`

Same multipart shape (`image` part, optional `capturedAt`), same 20 MB limit, same JPEG/PNG/WebP
allow-list, same `UnsupportedImageTypeException` → `422`, same `201 ReceiptSummaryResponse`.
The only difference is the stored `source`. It mirrors `POST /receipts/manual` (one path per
origin).

| Candidate | Verdict | Why |
|---|---|---|
| `POST /receipts/screenshot` | **adopted** | Existing camera contract stays byte-for-byte unchanged; each endpoint creates exactly one source, so there is no client-supplied enum to validate; `operationId`s stay unambiguous. |
| Optional `source` form field on `POST /receipts` | rejected | The client could submit `MANUAL`/`BANK_IMPORT`, forcing a "which values are legal here" validation rule and a conditional `capturedAt`/`image` contract; no upside beyond one fewer route. |
| Strategy / pluggable per-source upload handlers | rejected | YAGNI. The two paths differ by one enum value. In the backend, `ReceiptService.uploadImageReceipt(image, capturedAt, source)` and a `Receipt.newImageUpload(source, …)` factory cover both (see `02-domain-model-and-schema.md` § Image Upload Paths). |

`capturedAt` is optional and the PWA omits it for screenshots, so the server defaults it to the
upload time. A screenshot is often taken from an order placed days earlier, so the classifier
overrides it with the purchase date shown in the image when one is legible (the existing
`classification-batch` rule for `capturedAt`, unchanged).

### 3. How the script learns the origin and the file type

- **Origin:** `GET /receipts/pending` returns `{id, source}` for every entry. `source` was already
  *required* in the OpenAPI `PendingReceiptRef` schema; the backend DTO returned `id` only, so
  this change makes the implementation match the spec. The script writes it onto the image's
  manifest line as `source=<CAMERA|SCREENSHOT>`, and `infra/classify/prompt.md` keys its
  "Screenshots" rules on `source=SCREENSHOT`.
- **File type:** the script derives the temp file's extension from the **`Content-Type` of the
  `GET /receipts/{id}/image` response** (`image/jpeg → .jpg`, `image/png → .png`,
  `image/webp → .webp`), not from the pending payload. Today every image is saved as
  `receipt-<id>.jpg` regardless of its real type, which would hand a PNG to the `Read` tool under
  a `.jpg` name. The backend already returns the correct media type (derived from the stored
  file's extension).
- Rejected: adding `imageContentType`/`imageExtension` to the pending payload. It would duplicate
  what the image response already states authoritatively and widen a deliberately lean endpoint.

### 4. Frontend entry point: a segmented control on the existing capture screen

`/` (the camera home action) stays the default. The capture screen gains a two-segment control —
**"Aparat" | "Zrzut ekranu"** — built on the existing `components/ui/tabs`. No new route and no
fifth bottom-nav item (the nav is a 4-column grid and is already full). The screenshot segment
offers a "Wklej ze schowka" button (async Clipboard API), a "Wybierz plik" file picker (no
`capture` attribute, `accept="image/png,image/jpeg,image/webp"`), and a page-level `paste`
listener; all three land in the same preview → "Powtórz" / "Zatwierdź" confirm step as the camera
flow, then upload via `POST /receipts/screenshot` and navigate to `/receipts`. Full structure in
`04-classification-flow.md` § Capture Entry Points.

| Candidate | Verdict | Why |
|---|---|---|
| Segmented control on `/` | **adopted** | Both modes share the preview/confirm/upload/error UI; zero nav churn; camera stays the default. |
| New route `/screenshot` + 5th nav item | rejected | Crowds the 4-item bottom nav; duplicates the shared confirm UI behind a second route. |
| New route `/screenshot` reached from a link on `/` | rejected | An extra navigation hop for no gain. Revisit only if the Web Share Target follow-up needs a deep-linkable landing route. |

### 5. Two Flyway migrations, not one

`ALTER TYPE … ADD VALUE` can run inside a transaction, but PostgreSQL refuses to *use* the new
value (here: in a CHECK constraint) until that transaction has committed (`55P04 unsafe use of new
value`). Flyway runs each versioned migration in its own transaction, so:

- `V3__receipt_source_screenshot.sql` — only `ALTER TYPE receipt_source_enum ADD VALUE IF NOT EXISTS 'SCREENSHOT'`.
- `V4__receipt_image_path_screenshot_check.sql` — drops and re-adds `receipts_image_path_matches_source`
  as `(source IN ('CAMERA','SCREENSHOT') AND image_path IS NOT NULL) OR (source = 'MANUAL' AND image_path IS NULL)`
  (same constraint name), plus a `COMMENT ON COLUMN receipts.source`.

`V1`/`V2` are not edited (their Flyway checksums are recorded in the deployed database).
`spring.flyway.group` must stay at its default `false`; grouping V3 and V4 into one transaction
would break V4. Verified by applying V1–V4 in order, each in its own transaction, to a real
PostgreSQL 16 server (including a negative control showing the combined single-transaction form
fails); the Backend agent's Testcontainers run against PostgreSQL 17 is the authoritative check.

**Consequences:**
- Existing `CAMERA` rows are untouched; there is no backfill and none is wanted.
- The classifier prompt grows by one section. The daily run is still exactly one `claude -p`
  invocation covering the whole batch; screenshots only add images to it.
- `classification-batch` needs no change: screenshots arrive as `items`/`failures` like photos,
  and a screenshot id in `uncertainCategory` is invalid classifier output, tolerated (skipped,
  reported) exactly like a `CAMERA` id (ADR-007's rule is "any non-`BANK_IMPORT` source").
- Spending summary/trend/line-items endpoints are unaffected; the dashboard counts screenshot
  receipts like any other `PROCESSED` receipt.
- The same purchase captured twice (a photo and a screenshot) is not de-duplicated; ADR-007's
  dedup is bank-transaction-vs-receipt only, and `SCREENSHOT` receipts are included in its
  match set alongside `CAMERA`/`MANUAL`. A user who uploads both deletes one (`DELETE /receipts/{id}`).
- Non-purchase screenshots (chat, meme) land in `FAILED` with a reason and can be deleted or
  re-queued from the receipt detail screen like any other failure.
- The `GET /receipts?source=` filter is declared in the OpenAPI spec but not yet implemented by
  the backend; it is not needed by this feature (`SCREENSHOT` is simply one more valid value once
  it is).
- **Follow-up, not built here: PWA Web Share Target** ("Share → Receipts" straight from the phone
  gallery). It needs a `share_target` entry in the web-app manifest plus a service-worker `fetch`
  handler that accepts the multipart POST and forwards it to `POST /receipts/screenshot`
  (`vite-plugin-pwa` in `injectManifest` mode), and it works on Android Chrome only (iOS Safari
  does not support Web Share Target). The dedicated screenshot endpoint is already the right
  target for it.
- Multi-image / stitched long screenshots remain out of scope: one image is one receipt entry.
