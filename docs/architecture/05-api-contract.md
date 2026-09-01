# 05 — API Contract Summary

> **Audience:** Backend and Frontend agents.
> **Source of truth:** `docs/openapi.yaml` (single file — no split source tree/bundler; see
> ADR-001). This document is a navigable summary; if it and the spec ever disagree, the spec
> wins — update this doc, don't treat it as authoritative on its own.

---

## Response Envelope

- Paginated collections (`GET /receipts`): `{ data: [...], meta: {...}, page: {...} }`.
- Small, inherently unpaginated collections (`GET /receipts/pending`, `GET /categories`, the
  arrays inside spending summary/trend): `{ data: [...] | {...}, meta: {...} }` — no `page`
  block, since there's nothing to paginate.
- Single-resource endpoints: `{ data: {...}, meta: {...} }`.
- Errors: `{ errors: [{ code, field, message }], meta: {...} }`.

Matches investing-app's convention exactly, scaled down (no separate `Meta`/`PageInfo` schema
files — everything lives in `docs/openapi.yaml#/components/schemas`).

## Endpoints

| Method | Path | Caller | Notes |
|---|---|---|---|
| POST | `/api/receipts` | PWA | Multipart image upload → `PENDING`/`CAMERA` receipt. Returns immediately, no classification. |
| POST | `/api/receipts/manual` | PWA | No image, direct line-item entry (e.g. `RACHUNKI` bills) → `PROCESSED`/`MANUAL` receipt straight away. Strict `422` validation (this is direct human input). |
| GET | `/api/receipts` | PWA | Paginated list, filterable by `year`, `month`, `status`, `source`. Default sort `capturedAt` desc. |
| GET | `/api/receipts/pending` | **classify-receipts.sh** | List of everything `PENDING`. Pure read — never mutates status (see `03-receipt-lifecycle.md`). Unpaginated by design. Lean `{id}` for `CAMERA`; inline transaction fields (no image to fetch) for `BANK_IMPORT` — design-only, ADR-007, see `06-bank-integration.md`. |
| GET | `/api/receipts/{id}` | PWA | Full detail incl. `imageUrl` + line items. |
| GET | `/api/receipts/{id}/image` | PWA, **classify-receipts.sh** | Raw image bytes. 404 for a `MANUAL`/`BANK_IMPORT` receipt (no image) or unknown id. |
| POST | `/api/receipts/classification-batch` | **classify-receipts.sh** | Body is Claude's raw `{items, failures}` output (design-only: gains `uncertainCategory` — ADR-007), forwarded unchanged. Idempotent per receipt; replaces only uncorrected line items; tolerant of an unknown `receiptId` or an out-of-enum `category` per entry (routed to `FAILED`/`skipped`, never a whole-request 400) — see below. |
| PUT | `/api/receipts/{id}/line-items/{itemId}` | PWA | User correction. Sets `corrected = true`; never touched by a later classification-batch replace. |
| PUT | `/api/receipts/{id}/category` | PWA | **Design-only, ADR-007.** Resolves a `NEEDS_CATEGORY_REVIEW` (`BANK_IMPORT`-only) receipt by hand — creates its single line item, `corrected = true` immediately. Not reachable via `reprocess`. |
| POST | `/api/receipts/{id}/reprocess` | PWA | Resets `FAILED` → `PENDING` freely; `PROCESSED`/`PROCESSING` → `PENDING` requires `force: true`. Pure status reset — no classification logic here. Not valid from `NEEDS_CATEGORY_REVIEW`. |
| DELETE | `/api/receipts/{id}` | PWA | Removes the row (cascade) and the image file. Also how the backend performs the late-match dedup cleanup described in `06-bank-integration.md`. |
| GET | `/api/spending/summary?year=&month=` | PWA | All 11 categories, zero-filled. |
| GET | `/api/spending/trend?year=` | PWA | All 12 months, each zero-filled per category. |
| GET | `/api/categories` | PWA | Static list (Polish label + gloss), canonical order. |
| POST | `/api/bank/consent` | PWA (Settings) | **Design-only, ADR-007.** Initiates the PSD2 consent flow — returns a redirect URL. |
| GET | `/api/bank/consent/callback` | PKO (browser redirect) | **Design-only, ADR-007.** OAuth2 authorization-code callback — not called by the PWA's JS. |
| GET | `/api/bank/connection` | PWA (Settings) | **Design-only, ADR-007.** Connection status; never returns raw tokens. |
| DELETE | `/api/bank/connection` | PWA (Settings) | **Design-only, ADR-007.** Disconnect / clear stored tokens. |
| POST | `/api/bank/sync` | **bank-sync cron trigger** | **Design-only, ADR-007.** Daily sync-job entry point, mirrors `classification-batch`'s shape — always `200`, degrades to a no-op on expired consent. See `06-bank-integration.md`. |

## `classification-batch` Tolerance Rules (worth calling out explicitly)

This is the one endpoint whose caller (Claude, via the wrapper script) is not fully trustworthy
input — a model can occasionally emit an unexpected value despite the prompt's instructions.
CLAUDE.md's quality gate is explicit: *"Never crash the whole batch over one bad receipt."* The
contract handles this per-entry, not per-request:

- **Unknown `receiptId`** (typo, already-deleted receipt) → that entry is skipped, reported in
  the response's `skipped[]`, rest of the batch still applies. Not a 400.
- **`category` outside the fixed 11-value enum** → that receipt is routed to `FAILED` with an
  explanatory `failureReason`, exactly as if it had been in `failures[]` — never silently
  coerced to some default category, per CLAUDE.md's gate. Not a 400.
- **Structurally malformed body** (not valid JSON, missing `items`/`failures` keys entirely) →
  this *is* a normal `400` — it's a request-shape problem, not a content-quality problem, and
  every other endpoint in this API treats a malformed body the same way.

**Design-only addition (ADR-007):** an `uncertainCategory[]` entry naming a receipt whose
`source` is not `BANK_IMPORT` is invalid classifier output — tolerated the same way an unknown
`receiptId` already is (skipped, reported, never aborts the batch), never a 400. See
`06-bank-integration.md`.

## Categories

`GET /api/categories` returns the 11 fixed values from CLAUDE.md § Categories, each with its
Polish `label` and English `gloss`. This is the single rendering of that table anywhere in the
running system's API surface — the frontend's dropdowns/legend consume this endpoint rather than
hard-coding the list a second time, and `infra/classify/prompt.md` mirrors the same 11 values
for the classifier (kept in sync by convention against the same CLAUDE.md source — see ADR-005).

## Money and Dates

- Amounts: JSON `number`/`format: double`, backed by Postgres `NUMERIC(10,2)` — matches
  investing-app's `deposits.amount` convention (see `02-domain-model-and-schema.md`).
- `capturedAt` on receipts: full `date-time` (when the photo was taken, or the manual entry's
  date). The classifier's own `capturedAt` field in a `classification-batch` item is a plain
  `date` (`YYYY-MM-DD`) — that's the granularity a receipt actually prints; the backend combines
  it with a time component (or keeps the existing upload-time value) when applying it.
