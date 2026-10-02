# Screenshot analysis (paste or pick a screenshot, analyzed like a receipt photo)

User request (PL): "Musi być nowa funkcjonalność. Mogę wkleić lub wyszukać screen do analizy
(analogicznie do zdjęcia paragonu)."

Add a second image-capture path next to the camera: the user can **paste** a screenshot from the
clipboard, or **pick/search** an existing image file (gallery / file picker) — no camera involved.
Typical content: an e-receipt from a store app (Żabka, Biedronka, Lidl Plus…), an online-order
summary (Allegro, Glovo, Wolt…), a bank-app/BLIK confirmation, a PDF/webpage invoice capture.
From that point on it behaves **exactly like a photographed receipt**: stored as `PENDING`, picked
up by the daily 06:00 `classify-receipts.sh` batch, itemized + categorized by the Claude Code CLI,
visible in the receipts list/detail, correctable line-by-line, counted in the dashboard.

## Requirements

1. **Capture UI (frontend)**
   - Paste: a global `paste` listener on the capture screen (Ctrl/Cmd+V, and long-press → Paste
     on mobile where it fires) plus an explicit "Wklej ze schowka" button using the async
     Clipboard API (`navigator.clipboard.read()`) where available, with a graceful fallback hint
     where it isn't (iOS Safari permission prompt, insecure context, Firefox).
   - Pick/search: a file input **without** `capture` (so the OS opens gallery/file picker, not the
     camera), `accept="image/png,image/jpeg,image/webp"`. Drag-and-drop onto the screen on desktop
     is a nice-to-have, not required.
   - Same confirm step as the photo flow (preview → "Powtórz" / "Zatwierdź") before anything is
     uploaded; same error handling; on success navigate to `/receipts`.
   - Reachable from the existing capture screen AND the bottom nav in a way that doesn't make the
     4-item nav unusable (architect/frontend decide: tab/segmented control on the capture screen
     vs. a new route `/screenshot`; do not remove the camera as the default home action).
   - A pasted clipboard blob has no filename — build a `File` with a sensible name + its real MIME
     type (screenshots are almost always `image/png`).
2. **Source of truth for "where did this come from"**: new `ReceiptSource.SCREENSHOT` (alongside
   `CAMERA`, `MANUAL`; `BANK_IMPORT` is design-only per ADR-007). Receipt list/detail label it
   distinctly (e.g. fallback title "Screenshot" instead of "Paragon"). Architect confirms vs.
   reusing `CAMERA`; recommended is a distinct value because the classifier needs a different
   hint and the UI wants a different label.
   - **Flyway gotcha**: `ALTER TYPE receipt_source_enum ADD VALUE` cannot be *used* in the same
     transaction that adds it, and V1's `receipts_image_path_matches_source` CHECK must be extended
     (`SCREENSHOT` requires `image_path IS NOT NULL`). Split into separate versioned migrations
     (or otherwise make it provably safe) and verify against real PostgreSQL via Testcontainers.
3. **API**: new upload path for screenshots (architect picks: `POST /api/receipts/screenshot`
   mirroring `/manual`, vs. an optional `source` form field on `POST /api/receipts`). Existing
   camera clients/contract must keep working unchanged. Same 20 MB multipart limit, same
   JPEG/PNG/WebP allow-list (already supported by `FilesystemImageStorageService`), same
   `UnsupportedImageTypeException` handling. `capturedAt` defaults to now.
   `GET /receipts/pending` must expose the source so the script can tell the cases apart.
4. **Classifier (prompt + script)**
   - `infra/classify/prompt.md` (architect-owned): add a "Screenshots" section — a screenshot is a
     digital capture, not a printed paragon: no VAT letters (the VAT cross-check is unavailable —
     fall back to product names), may include delivery/service fees, discounts/coupons, tips,
     partial/scrolled captures, status-bar/UI chrome to ignore, amounts that may be shown as
     `12,99 zł` or `PLN 12.99`. Same output contract, same line-total self-check, same
     "always guess, never hedge" policy as photo receipts (a screenshot id never goes in
     `uncertainCategory`). Non-receipt screenshots (chat, meme, nothing purchase-related) → `failures`.
   - `infra/classify/classify-receipts.sh` (devops-owned): today every image is saved as
     `receipt-<id>.jpg` regardless of its real type — PNG screenshots must be saved with the
     correct extension (derive from the response `Content-Type`, or from the pending payload) so
     the `Read` tool decodes them correctly. Manifest line should also carry the source
     (e.g. `source=SCREENSHOT`) so the prompt can key on it. Keep all existing failure semantics
     (any CLI/network failure → everything stays `PENDING`).
5. **Docs**: update `docs/architecture/` (00 overview map if needed, 02 schema, 03 lifecycle,
   04 classification flow, 05 API contract), `docs/openapi.yaml`, and add an ADR for the
   `SCREENSHOT` source decision. `infra/classify/README.md` if the manifest/script changes.

## Out of scope

- PWA **Web Share Target** ("Share → Receipts" from the phone gallery) — needs a service-worker
  POST handler; note it as a follow-up in the ADR, don't build it.
- Multi-image / stitched long screenshots (one image = one receipt entry).
- Any OCR outside the Claude Code CLI; any API key (CLAUDE.md hard constraints still apply).

## Acceptance

- Pasting a PNG screenshot on the capture screen shows the preview; "Zatwierdź" creates a
  `PENDING` receipt with `source = SCREENSHOT`; picking an image from the file picker does the same.
- Receipt list/detail show it with a screenshot-specific label and the image renders.
- `classify-receipts.sh` downloads it with a `.png` extension, tags it in the manifest, and the
  batch result is applied through the existing `classification-batch` endpoint unchanged.
- Backend: unit + Testcontainers integration tests for the new upload path and the migration;
  `mvn verify` green. Frontend: `tsc`/lint/build green, paste + file-pick flows tested.
- App rebuilt and redeployed per CLAUDE.md (Docker) if the environment allows it.
