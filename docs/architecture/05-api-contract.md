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
| GET | `/api/receipts/store-names` | PWA (manual-entry combobox) | Ranked, deduplicated `storeName` suggestions across all receipts/sources/statuses. Unpaginated, capped at 20. See `02-domain-model-and-schema.md` § Store-Name Suggestions and ADR-009 for the ranking/dedup rules. |
| GET | `/api/receipts/subcategory-labels` | **classify-receipts.sh** | Known `subcategory`/`subSubcategory` labels, grouped by category, ranked/deduped the same way as `store-names` but capped at 30 `subSubcategories` per subcategory rather than a flat top-20. Spliced into `prompt.md`'s `{{KNOWN_LABELS_MANIFEST}}` placeholder so the classifier reuses a label across daily runs instead of drifting into near-duplicates. See `02-domain-model-and-schema.md` § Known Subcategory/Sub-Subcategory Labels and ADR-010 § Cross-batch label consistency. |
| GET | `/api/receipts/{id}` | PWA | Full detail incl. `imageUrl` + line items. |
| GET | `/api/receipts/{id}/image` | PWA, **classify-receipts.sh** | Raw image bytes. 404 for a `MANUAL`/`BANK_IMPORT` receipt (no image) or unknown id. |

`imageUrl` (on `ReceiptSummary`/`ReceiptDetail`) is a server-root-relative path such as
`/api/receipts/42/image` — the backend has no notion of the app's `/paragony/` deployment
prefix and must not bake one in (see CLAUDE.md's "Deployment" section: prefix handling is
frontend-owned, centralized through `import.meta.env.BASE_URL`, same as `vite.config.ts`'s
`base` and the router `basename`). Any place the PWA renders `imageUrl` as an asset `src`
resolves it through that same mechanism rather than using the backend's path verbatim —
`apiClient`'s own `baseURL` already does this for JSON calls; `resolveApiUrl()` in
`frontend/src/lib/api.ts` extends the same resolution to non-axios asset URLs like this one.
| POST | `/api/receipts/classification-batch` | **classify-receipts.sh** | Body is Claude's raw `{items, failures}` output (design-only: gains `uncertainCategory` — ADR-007), forwarded unchanged. Idempotent per receipt; replaces only uncorrected line items; tolerant of an unknown `receiptId` or an out-of-enum `category` per entry (routed to `FAILED`/`skipped`, never a whole-request 400) — see below. |
| PUT | `/api/receipts/{id}/line-items/{itemId}` | PWA | User correction. Sets `corrected = true`; never touched by a later classification-batch replace. |
| PUT | `/api/receipts/{id}/category` | PWA | **Design-only, ADR-007.** Resolves a `NEEDS_CATEGORY_REVIEW` (`BANK_IMPORT`-only) receipt by hand — creates its single line item, `corrected = true` immediately. Not reachable via `reprocess`. |
| POST | `/api/receipts/{id}/reprocess` | PWA | Resets `FAILED` → `PENDING` freely; `PROCESSED`/`PROCESSING` → `PENDING` requires `force: true`. Pure status reset — no classification logic here. Not valid from `NEEDS_CATEGORY_REVIEW`. |
| DELETE | `/api/receipts/{id}` | PWA | Removes the row (cascade) and the image file. Also how the backend performs the late-match dedup cleanup described in `06-bank-integration.md`. |
| GET | `/api/spending/summary?year=&month=` | PWA | All 11 categories, zero-filled. |
| GET | `/api/spending/trend?year=` | PWA | All 12 months, each zero-filled per category. |
| GET | `/api/spending/line-items?year=&month=&category=` | PWA (drill-down, on click only) | Flat list of that category/month's line items (`productName`, `amount`, `quantity`, `subcategory`, `subSubcategory`, plus `receiptId`/`storeName`/`capturedAt` for display and linking back to the receipt). Unpaginated. A separate, lazily-fetched endpoint by design, not a nested field on `summary` — see ADR-010. Client groups by `subcategory` → `subSubcategory` itself; either may be `null`. |
| GET | `/api/spending/subcategory-summary?year=&month=` | PWA (Wydatki "Szczegóły" mode, lazy) | All 11 categories (zero-filled, canonical order), each with `totalAmount`, `unlabeledAmount` (NULL/blank subcategory) and the **full** `subcategories[]` list of `{subcategory, amount}` sorted by amount desc, grouped by `lower(trim())`. Per-category totals equal `/spending/summary`'s. Top-N + "Reszta" is client-side. See § Subcategory breakdown below and ADR-013. |
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

## `subcategory` / `subSubcategory` (ADR-010)

Both fields live on `LineItem`/`LineItemInput` (used by `ReceiptDetail`, `classification-batch`,
manual entry, and the new `SpendingLineItem` drill-down entry alike — one shared pair of fields,
not a parallel schema per endpoint). Contract points that matter to Backend/Frontend:

- **Free-text, optional, nullable — no enum, no validation.** Unlike `category`, an unexpected
  or inconsistent value is never rejected and never routes a receipt to `FAILED`; there is
  nothing to validate against (see ADR-005 for why `category` is the one field that *is* a
  closed enum, and ADR-010 for why these two deliberately are not).
- **Omission is always valid**, on any endpoint that accepts `LineItemInput` — an older
  `classify-receipts.sh`/`prompt.md` version, or a manual entry, may send a batch/request with
  neither field present. Both simply persist as `NULL`.
- **No backfill.** Every line item created before `V2__line_item_subcategories.sql` has both
  fields `NULL` forever, unless that specific receipt is reprocessed.
- **`corrected` does not cover these two fields.** A reprocess replaces them freely even on a
  line item whose `category`/`amount`/`productName` the user has hand-corrected — there is no
  edit UI for `subcategory`/`subSubcategory` in this API version, so there is nothing yet to
  protect from being overwritten. If a future task adds that UI, extending `corrected`'s
  protection to these fields is a deliberate follow-up decision, not an oversight (ADR-010 calls
  this out explicitly).
- **Cross-batch consistency is enforced by feeding known labels back into the prompt, not by
  server-side validation.** `GET /receipts/subcategory-labels` returns every distinct label
  already used, grouped by category; `classify-receipts.sh` fetches it once per non-empty run and
  splices it into `infra/classify/prompt.md`'s `{{KNOWN_LABELS_MANIFEST}}` placeholder, so each
  day's otherwise-stateless `claude -p` invocation can reuse an existing label instead of
  inventing a near-duplicate ("Batony" vs. "Batony czekoladowe" for the same concept). This is
  still advisory — nothing rejects a batch over a label that ignores the known list — it only
  changes what Claude is told, not what the backend accepts. See ADR-010 § Cross-batch label
  consistency.

## Subcategory breakdown — `GET /spending/subcategory-summary` (ADR-013)

Feeds the Wydatki dashboard's "Szczegóły" (stacked-bar) mode. Contract points that matter to
Backend/Frontend:

- **Lazy, second-tier call.** `GET /spending/summary` stays unchanged and is still the only
  eager dashboard call ("Podsumowanie" mode). This endpoint is fetched only once the user
  switches to "Szczegóły" — never on dashboard load, never 11× `/spending/line-items`.
- **Same scope as `/spending/summary`** (`PROCESSED` only, same UTC month window), and the
  totals must reconcile exactly: per category, `totalAmount == unlabeledAmount +
  Σ subcategories[].amount == /spending/summary`'s amount for that category. Backend tests this
  equality directly against the summary endpoint.
- **All 11 categories, zero-filled, canonical order** — same convention as `/spending/summary`,
  so the two responses zip index-for-index.
- **Normalization reuses ADR-010 §5:** group by `lower(trim(subcategory))`, display the
  most-used exact-cased variant *within this month's slice* (tie: most recent `capturedAt`, then
  variant string asc). NULL/empty/whitespace-only subcategories go to `unlabeledAmount`, never to
  a `subcategories[]` entry — so `subcategories[].subcategory` is always a non-blank string.
- **Server returns the full, sorted list; the client buckets.** No top-N cut or "Reszta" entry
  server-side. The frontend takes the top 3 labeled entries as shaded segments of one hue and
  folds everything else, plus `unlabeledAmount`, into one grey "Reszta" segment (if the
  remainder is exactly one labeled subcategory and `unlabeledAmount == 0`, that one is shown as a
  4th shade instead of a one-item "Reszta"). The full list also feeds the accessible table
  fallback. Rationale: ADR-013.
- **No `subSubcategory` level** here — `/spending/line-items` remains the drill-down for that.

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
