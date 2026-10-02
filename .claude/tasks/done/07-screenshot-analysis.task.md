# Screenshot analysis (import a screenshot or gallery image, analyzed like a receipt photo)

User request (PL): "Musi być nowa funkcjonalność. Mogę wkleić lub wyszukać screen do analizy
(analogicznie do zdjęcia paragonu)."

> **Requirement change (user, 2026-10-02) and architect outcome.** The user uses the app almost
> exclusively **on a phone** (iPhone Safari PWA and Android Chrome PWA), so **importing from the
> phone gallery is the PRIMARY path**; paste is secondary; desktop paste/drag-and-drop is a
> nice-to-have only. A gallery pick may be a screenshot **or** a photo of a paper receipt taken
> earlier ("zdjęcie z galerii"), so the source is named for the *channel*, not the content:
> **`IMAGE_IMPORT`** (not `SCREENSHOT`), endpoint **`POST /api/receipts/image-import`**. Where the
> original wording below says `SCREENSHOT` / `/receipts/screenshot`, it has been updated to match;
> the "## Architect decisions" section at the end is the authoritative contract and wins on any
> conflict.

Add a second image-capture path next to the camera: the user can **import an existing image** —
pick it from the phone **gallery / file picker** (primary), or paste a screenshot from the
clipboard (secondary) — no live camera involved. Typical content: an e-receipt screenshot from a
store app (Żabka, Biedronka, Lidl Plus…), an online-order summary (Allegro, Glovo, Wolt…), a
bank-app/BLIK confirmation, a PDF/webpage invoice capture, or an earlier photo of a paper receipt.
From that point on it behaves **exactly like a photographed receipt**: stored as `PENDING`, picked
up by the daily 06:00 `classify-receipts.sh` batch, itemized + categorized by the Claude Code CLI,
visible in the receipts list/detail, correctable line-by-line, counted in the dashboard.

## Requirements

1. **Capture UI (frontend) — phone-first**
   - **Gallery picker is first-class on iOS and Android and gets a prominent button** on the
     capture screen (the camera stays the default home action; the gallery button sits directly
     under it): a file input **without** `capture`, so the OS opens the Photo Library / Choose File
     (iOS) or the gallery / Files (Android), not the camera. Exact `accept` and format handling in
     "Architect decisions" (`accept="image/*"` + client-side normalisation to JPEG).
   - **Paste is secondary**: a smaller "Wklej ze schowka" action using the async Clipboard API
     (`navigator.clipboard.read()`) where available (it is absent over plain HTTP, in Firefox, and
     may be permission-gated on iOS — degrade gracefully), plus a global `paste` listener on the
     capture screen (Ctrl/Cmd+V, and long-press → Paste on mobile where it fires). Desktop
     drag-and-drop onto the screen is a nice-to-have, not required.
   - Same confirm step as the photo flow (preview → "Powtórz" / "Zatwierdź") before anything is
     uploaded; same error handling; on success navigate to `/receipts`.
   - Reachable from the existing capture screen without making the 4-item bottom nav unusable and
     without removing the camera as the default home action (decided: inline actions on the
     capture screen — no tabs, no new route, no new nav item).
   - A pasted clipboard blob has no filename — build a `File` with a sensible name + its real MIME
     type (screenshots are almost always `image/png`).
2. **Source of truth for "where did this come from"**: new `ReceiptSource.IMAGE_IMPORT` (alongside
   `CAMERA`, `MANUAL`; `BANK_IMPORT` is design-only per ADR-007). Receipt list/detail label it
   distinctly (fallback title "Zaimportowany obraz" instead of "Paragon"). The source is a **hint**
   for the classifier (an imported image may be a screenshot or a photo of a paper paragon — the
   prompt tells Claude to look at the image), and a distinct UI label.
   - **Flyway gotcha**: `ALTER TYPE receipt_source_enum ADD VALUE` cannot be *used* in the same
     transaction that adds it, and V1's `receipts_image_path_matches_source` CHECK must be extended
     (`IMAGE_IMPORT` requires `image_path IS NOT NULL`). Done as two separate versioned migrations
     (`V3`, `V4` — already written by the architect, see below); verify against real PostgreSQL via
     Testcontainers.
3. **API**: new upload path `POST /api/receipts/image-import` mirroring `/manual` (decided; not a
   `source` form field on `POST /api/receipts`). Existing camera clients/contract must keep working
   unchanged. Same 20 MB multipart limit, same JPEG/PNG/WebP allow-list (already supported by
   `FilesystemImageStorageService`), same `UnsupportedImageTypeException` handling. `capturedAt`
   defaults to now. `GET /receipts/pending` must expose the source so the script can tell the cases
   apart.
4. **Classifier (prompt + script)**
   - `infra/classify/prompt.md` (architect-owned, **done**): a "Screenshots and imported images"
     section — look at the image first; a screen capture gets the digital-capture rules (no VAT
     letters, delivery/service fees, discounts/coupons, tips, partial captures, UI chrome to ignore,
     `12,99 zł` / `PLN 12.99` amounts); a photographed paper paragon, even when imported, gets the
     normal photo-receipt rules incl. the VAT cross-check. Same output contract, same line-total
     self-check, same "always guess, never hedge" policy (an imported image id never goes in
     `uncertainCategory`). Non-receipt images (chat, meme, nothing purchase-related) → `failures`.
   - `infra/classify/classify-receipts.sh` (devops-owned): today every image is saved as
     `receipt-<id>.jpg` regardless of its real type — PNG screenshots must be saved with the correct
     extension (derived from the image response's `Content-Type`) so the `Read` tool decodes them
     correctly. The manifest line also carries the source (`source=IMAGE_IMPORT`). Keep all existing
     failure semantics (any CLI/network failure → everything stays `PENDING`).
5. **Docs** (architect, **done**): `docs/architecture/` (00, 01, 02, 03, 04, 05, 06 forward
   pointers), `docs/openapi.yaml`, `docs/adr/ADR-014-image-import-source.md`.
   `infra/classify/README.md` needs updating by DevOps for the script/manifest change.

## Out of scope

- PWA **Web Share Target** ("Share → Paragony" from the phone gallery) — needs a service-worker POST
  handler and does not work on iOS Safari; documented as a follow-up in ADR-014, not built.
- **Multi-select** from the gallery (one image = one receipt entry; follow-up: `multiple` + one
  upload per file) and stitched long screenshots.
- **Server-side HEIC/HEIF conversion** (no trivial free option on the ARM64 backend image; the PWA
  normalises to JPEG instead) and server-side magic-byte sniffing (see decisions).
- Any OCR outside the Claude Code CLI; any API key (CLAUDE.md hard constraints still apply).

## Acceptance

- Picking an image from the phone gallery on the capture screen shows the preview; "Zatwierdź"
  creates a `PENDING` receipt with `source = IMAGE_IMPORT`. Pasting a PNG screenshot (clipboard
  button or Ctrl/Cmd+V) does the same. A HEIC gallery file is converted to JPEG client-side, or a
  clear Polish error is shown.
- Receipt list/detail show it with the "Zaimportowany obraz" fallback label and the image renders.
- `classify-receipts.sh` downloads a PNG import with a `.png` extension, tags it
  `source=IMAGE_IMPORT` in the manifest, and the batch result is applied through the existing
  `classification-batch` endpoint unchanged.
- Backend: unit + Testcontainers integration tests for the new upload path and the migration;
  `mvn verify` green. Frontend: `tsc`/lint/build green, gallery-pick, paste and normalisation flows
  tested.
- App rebuilt and redeployed per CLAUDE.md (Docker) if the environment allows it.

## Architect decisions

> Authoritative hand-off for Backend, Frontend and DevOps. Reasoning: `docs/adr/ADR-014-image-import-source.md`.
> Living docs: `docs/architecture/02` (class diagram, § Image Upload Paths), `03` (lifecycle),
> `04` (§ Capture Entry Points flowchart + sequence), `05` (§ Image upload endpoints),
> `docs/openapi.yaml` (`/receipts/image-import`, `ReceiptSource`, `PendingReceiptRef`).
> **Already written by the architect — do not rewrite, only consume:** `V3`/`V4` migrations,
> `docs/openapi.yaml`, `infra/classify/prompt.md`, all architecture docs and the ADR.

### Decisions

| # | Decision |
|---|---|
| 1 | **New source `IMAGE_IMPORT`; do not reuse `CAMERA`, do not name it `SCREENSHOT`.** The brief recommended `SCREENSHOT`; overturned because the primary phone path imports gallery photos of paper receipts as well as screenshots, so a content-typed name would be wrong half the time. `IMAGE_IMPORT` names the *channel* ("existing image imported from gallery / file picker / clipboard / drop"), is parallel to the design-only `BANK_IMPORT`, and is image-backed + `PENDING`-created exactly like `CAMERA`. It is a **hint** for the classifier, never a guarantee about content. |
| 2 | **Endpoint: `POST /api/receipts/image-import`** (multipart, mirrors `/manual`'s one-path-per-origin), not a `source` field on `POST /api/receipts`. The camera endpoint is byte-for-byte unchanged. |
| 3 | **`GET /receipts/pending` returns `{id, source}`** for every entry (`source` was already *required* in the OpenAPI `PendingReceiptRef`; the backend DTO returned `id` only). No image path / content type in the payload. |
| 4 | **Frontend: inline actions on the existing capture screen at `/`** — no tabs/segmented control, no new route, no 5th nav item. Camera button unchanged and still the default; a prominent full-width "Wybierz z galerii" button directly beneath it; a smaller secondary "Wklej ze schowka"; Ctrl/Cmd+V hint on desktop. A tab would hide the main phone path behind a second tap. |
| 5 | **Gallery input `accept="image/*"` (no `capture`) + client-side normalisation to JPEG.** `image/*` because on iOS it gives Photo Library / Choose File with automatic JPEG transcoding of library photos, and on Android it opens the system gallery and *shows* HEIC/HEIF (a `png,jpeg,webp` list hides them or routes to the generic Files UI). JPEG/PNG/WebP files pass through **unchanged**; anything else (HEIC/HEIF, GIF, BMP, AVIF, empty type) is decoded (`createImageBitmap`), capped at 4096 px on the long side, re-encoded `image/jpeg` @ 0.92 over a white background. Decode failure → Polish error, no draft. **Backend gets no extra defence**: no HEIC conversion (needs native libheif / a plugin on the ARM64 image — not trivial, not free), no magic-byte sniffing (would break the existing upload tests that send placeholder bytes under `image/jpeg`; a mislabelled file already degrades to a `FAILED` receipt with a reason). The existing allow-list + `422` stays the gate. |
| 6 | **Classifier semantics.** `source=IMAGE_IMPORT` is a hint: the prompt tells Claude to *look first* — screen capture → "Screenshots" rules; photo of a paper paragon (even when imported) → unchanged photo-receipt rules incl. the VAT-letter cross-check; neither → `failures`. A `source=CAMERA` image that clearly shows a screen gets the screenshot rules too. An image id is never in `uncertainCategory`. |
| 7 | **Migrations: two files** because PostgreSQL cannot use a new enum value in the transaction that adds it. Verified on a real PostgreSQL 16.14 (V1→V4 each in its own transaction, pre-existing CAMERA/MANUAL rows survive, CHECK accepts/rejects the right combinations, negative control for the single-file form fails with `55P04`). **Not run on PG 17 / Testcontainers** — that is Backend's authoritative check. |
| 8 | **Deferred, documented in ADR-014:** Web Share Target (Android-only, needs SW + `injectManifest` + `/paragony/` prefix routing), multi-select, stitched screenshots, server-side HEIC conversion. |

### Exact contract

**Enum / DB**
- Postgres: `receipt_source_enum` values `CAMERA`, `MANUAL`, **`IMAGE_IMPORT`** (appended by `V3`).
- Migrations (already in the repo, **do not edit `V1`/`V2`**, keep `spring.flyway.group` at its default `false`):
  - `backend/src/main/resources/db/migration/V3__receipt_source_image_import.sql` — `ALTER TYPE … ADD VALUE IF NOT EXISTS 'IMAGE_IMPORT'` only.
  - `backend/src/main/resources/db/migration/V4__receipt_image_path_image_import_check.sql` — re-creates `receipts_image_path_matches_source`: `(source IN ('CAMERA','IMAGE_IMPORT') AND image_path IS NOT NULL) OR (source = 'MANUAL' AND image_path IS NULL)`; adds a `COMMENT ON COLUMN receipts.source`.
- Java: `ReceiptSource { CAMERA, MANUAL, IMAGE_IMPORT }`. TypeScript: `RECEIPT_SOURCES = ["CAMERA", "MANUAL", "IMAGE_IMPORT"]` (leave `BANK_IMPORT` out of the TS union until it is implemented).

**HTTP**
- `POST /api/receipts/image-import` — `multipart/form-data`; part `image` (required, JPEG/PNG/WebP by part content type, ≤ 20 MB), part `capturedAt` (optional ISO date-time; the PWA omits it). `201 { data: ReceiptSummary{status: PENDING, source: IMAGE_IMPORT, imageUrl: "/api/receipts/{id}/image"}, meta }`; `400` malformed; `422` unsupported image type (`UnsupportedImageTypeException`). `operationId: importReceiptImage`. Client-side name for a pasted blob: `import-<yyyyMMdd-HHmmss>.<png|jpg|webp>`; the server ignores the filename.
- `POST /api/receipts` — unchanged (`source = CAMERA`).
- `GET /api/receipts/pending` → `{ "data": [ { "id": 42, "source": "CAMERA" }, { "id": 44, "source": "IMAGE_IMPORT" } ], "meta": {…} }`. Pure read, ordering unchanged. `source` always present.
- `GET /api/receipts/{id}/image` — unchanged, but its `Content-Type` (`image/jpeg` | `image/png` | `image/webp`, derived from the stored file's extension by `FilesystemImageStorageService.mediaTypeFor`) is now **load-bearing** for the script.
- `POST /api/receipts/classification-batch` — **unchanged**. `IMAGE_IMPORT` ids arrive in `items`/`failures` like `CAMERA`.
- `GET /api/receipts?source=` is declared in the spec but not implemented in the backend; **not required** by this feature.

**Manifest line (script → prompt)** — one line per pending image receipt, appended after `prompt.md` (unchanged mechanism), **exactly**:

```
- id=<id> path=<absolute temp path> source=<CAMERA|IMAGE_IMPORT>
```

e.g. `- id=44 path=/tmp/tmp.XXXX/receipt-44.png source=IMAGE_IMPORT`. Field order `id`, `path`, `source`; values unquoted (the path has no spaces — it comes from `mktemp -d`). If the pending payload has no `source` (older backend), emit `source=CAMERA`. The prompt treats a `path=` line with no `source=` as `CAMERA`, so mixed old/new scripts are safe. `{{KNOWN_LABELS_MANIFEST}}` splice is untouched.

**Image extension mapping for `classify-receipts.sh`** (from the `Content-Type` response header of `GET /receipts/{id}/image`; strip any `;charset=…` parameter and lower-case before matching):

| Content-Type | Extension |
|---|---|
| `image/jpeg` | `.jpg` |
| `image/png` | `.png` |
| `image/webp` | `.webp` |
| anything else | treat as a failed download: log, `fail`/exit without submitting anything (every receipt stays `PENDING`, same as today's "could not download image" path) |

Because the extension is only known after the response arrives, download to a neutral name first (e.g. `${TMP_DIR}/receipt-${id}.download` using `curl -sf -o … -w '%{content_type}'`), then `mv` to `receipt-${id}.${ext}` and put the final path in the manifest.

**Prompt (done)**: `infra/classify/prompt.md` — intro explains `source=`; new `## Screenshots and imported images` section (look-first, 3 cases, screenshot rules, not-a-purchase → `failures`, never `uncertainCategory`); the manifest example at the bottom shows `source=` on every `path=` line.

### Backend work

1. Add `IMAGE_IMPORT` to `ReceiptSource`. Replace `Receipt.newCameraUpload(path, capturedAt)` with `Receipt.newImageUpload(ReceiptSource source, String imagePath, Instant capturedAt)` (status `PENDING`; throw `IllegalArgumentException` for `MANUAL`); `newManualEntry` unchanged.
2. Replace `ReceiptService.uploadCameraReceipt` with `uploadImageReceipt(MultipartFile image, Instant capturedAt, ReceiptSource source)` (store image → build receipt → save; `capturedAt` defaults to now). `ReceiptController`: existing `POST` passes `CAMERA`; new `@PostMapping(value = "/image-import", consumes = "multipart/form-data")` method `importImage(...)` passes `IMAGE_IMPORT`, identical parameters/response (`201 ReceiptSummaryResponse`). The controller decides the source — never accept it from the client. Diagram: `02-domain-model-and-schema.md` § Image Upload Paths.
3. `PendingReceiptRef` → `record PendingReceiptRef(Long id, ReceiptSource source)`; `ReceiptService.listPending()` maps it. (Do not add other fields.)
4. Generalise the `getImage` not-found message ("no image (MANUAL entry)") — wording only.
5. No new validation or conversion in `FilesystemImageStorageService`; no `spring.flyway.group`; no edits to `V1`/`V2`.
6. Tests: update existing tests that call `uploadCameraReceipt`/`newCameraUpload`; unit/WebMvc for `POST /image-import` (201, `source = IMAGE_IMPORT`, 422 on an unsupported type, 400 on missing part) and `uploadImageReceipt` for both sources; Testcontainers: migrations apply on a database that already has `CAMERA` + `MANUAL` rows, `IMAGE_IMPORT` insert with `image_path` succeeds and without it violates the CHECK, `MANUAL` with a path still violates it; `GET /pending` returns `source` for both kinds; a PNG-stored import is served as `image/png`; `classification-batch` processes an `IMAGE_IMPORT` receipt (an `items` and a `failures` case); `reprocess` and `DELETE` work for it. `mvn verify` green.

### Frontend work

Follow `04-classification-flow.md` § Capture Entry Points exactly (flowchart + legend). Summary:
1. `CaptureRoute`: keep the camera button/input as is; add the gallery button + second hidden input (`accept="image/*"`, **no** `capture`, **no** `multiple`; reset `value` after each pick); the secondary "Wklej ze schowka" button only when `typeof navigator.clipboard?.read === "function"`; Ctrl/Cmd+V hint only when `matchMedia("(pointer: fine)")` matches; `document` `paste` listener while no draft is shown; optional drag-and-drop. Page title "Dodaj paragon". Draft = `{ file, source: "CAMERA" | "IMAGE_IMPORT" }` + object URL; shared `CapturePreview` (neutral `alt`, e.g. "Podgląd obrazu paragonu").
2. Pure helper `normalizeImageFile(file)`: pass-through for `image/jpeg|png|webp`; otherwise `createImageBitmap` → ≤ 4096 px → white-backed canvas → `toBlob("image/jpeg", 0.92)` → `*.jpg`; typed failure → "Nie udało się odczytać tego formatu obrazu (np. HEIC). Wybierz zdjęcie JPEG/PNG lub zrób zrzut ekranu."; > 20 MB after normalisation → "Plik jest za duży (maks. 20 MB)."; clipboard without an image → "W schowku nie ma obrazu."; a `422` from the upload maps to the format message. Applied to every path, camera included (pass-through for its JPEG, so no behaviour change).
3. `lib/api.ts`: `importReceiptImage(image)` → `POST /receipts/image-import` (multipart, no `capturedAt`) via the existing `apiClient` (prefix handling is automatic). `useUploadReceipt` takes `{ image, capturedAt?, source }` and routes by source; same query invalidation; on success navigate to `/receipts`.
4. `lib/types.ts`: add `"IMAGE_IMPORT"` to `RECEIPT_SOURCES`. One helper `fallbackReceiptTitle(source)` (`IMAGE_IMPORT → "Zaimportowany obraz"`, `MANUAL → "Wpis ręczny"`, else `"Paragon"`) used by `ReceiptCard` and `ReceiptDetailRoute` (title and image `alt` too). `CategoryLineItemRow` keeps `"Paragon"` (no `source` on `SpendingLineItem`).
5. Tests: normalisation helper (pass-through, HEIC-like type → JPEG with mocked `createImageBitmap`/canvas, decode failure, oversize), gallery-pick flow, paste flow (event + clipboard button incl. unavailable API), source routing to the right endpoint, labels. `tsc`/lint/build green. **Please sanity-check on a real iPhone (Safari PWA) and an Android device** if at all possible: the iOS/Android picker behaviours in decision 5 are documented platform behaviour, not something the architect could test here. Note the app may be served over plain HTTP on the tailnet, where `navigator.clipboard` does not exist — the paste event, the gallery picker and the camera must work without it.

### DevOps work (`infra/classify/classify-receipts.sh` + `infra/classify/README.md`)

1. Parse pending as `{id, source}`: e.g. `jq -r '.data[] | [.id, (.source // "CAMERA")] | @tsv'`; iterate ids as today.
2. Download each image with `curl -sf -o "${TMP_DIR}/receipt-${id}.download" -w '%{content_type}'`; map `Content-Type` → extension per the table above; `mv` to `receipt-${id}.${ext}`; unknown type or any curl failure → existing `fail` path (nothing submitted, all receipts stay `PENDING`). Suggested log line per image: `Downloaded receipt ${id} (${source}, ${ext}).`
3. Manifest line exactly `- id=${id} path=${img} source=${source}`.
4. Everything else unchanged: single `claude -p` call, `--allowedTools "Read"`, ADR-008 extraction, ADR-011 sanity check/`total` strip, `classification-batch` POST, uniform "any failure → stay `PENDING`".
5. README: update step 2 (extension by `Content-Type`, `source=` on manifest lines) and "Testing manually" (upload a PNG via `POST /api/receipts/image-import`, run the script, confirm the `.png` download + `source=IMAGE_IMPORT`). The README's crontab block still lists five same-day slots although CLAUDE.md says a single 06:00 run — pre-existing drift, please reconcile while you are in that file.
6. No `compose.yml`/nginx change needed (generic `/api/` proxy, `client_max_body_size 20m` already). Confirm investing-app's `/paragony/` location allows ≥ 20 MB bodies (gallery images can be several MB) — it already carries camera photos, so most likely nothing to do.

### Notes for the orchestrator

- `docs/architecture/00-overview.md`'s status column carries "contract done, implementation pending" annotations for the image-import work — remove them once Backend/Frontend/DevOps land (per the docs convention).
- `CLAUDE.md` (not touched by the architect) describes the camera as the only capture path; consider a one-line mention of image import (gallery/clipboard → `IMAGE_IMPORT`) and of the `source=` hint in § Daily classification job.
- Pre-existing doc fixes made in passing: two Mermaid sequence-diagram lines in `04-classification-flow.md` contained `;` (it terminates statements in Mermaid, so the happy-path diagram did not parse) and `05-api-contract.md`'s endpoints table was split by a stray paragraph; both repaired.
