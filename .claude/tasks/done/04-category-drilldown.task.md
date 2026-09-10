# Category drill-down in the Wydatki (Expenses) dashboard

User request (2026-09-10): clicking a category in the Wydatki panel (e.g. "Jedzenie pierdołowate")
should show a detailed breakdown of what was actually bought — grouped into subcategories (e.g.
"Słodycze") and sub-subcategories (e.g. "żelki, batony, czekolady").

Clarified with the user before scoping this task:
- **Subcategory/sub-subcategory source**: free-text labels assigned by Claude during the daily
  classification batch job (like `productName` already is), not a second fixed enum the user has
  to maintain. Claude should aim to stay *consistent* across days/batches (reuse recurring labels
  rather than inventing near-duplicates), but this is a soft prompt-engineering goal, not a
  validated closed list — do **not** add DB-level enum constraints for these two fields, unlike
  the main `spend_category_enum` (see ADR-005, which is about the main category only and is not
  being revisited here).
- **Scope**: applies to **all 11 categories**, not just food — every line item gets a subcategory
  + sub-subcategory going forward, regardless of which of the 11 fixed categories it's in.

## Architect (must run first — sequencing gate)

- Design the DB schema change: two new nullable free-text columns on `receipt_line_items`
  (e.g. `subcategory`, `sub_subcategory` — short strings, no enum/check constraint, existing rows
  stay NULL since there's no bulk-reclassification of historical receipts in scope). New Flyway
  migration (`V2__...sql`), keep `V1__init.sql` as-is.
- Update `infra/classify/prompt.md`: extend the per-line-item JSON schema Claude returns to
  include `subcategory` and `subSubcategory` (both short, Polish, consistent casing/style similar
  to the Polish labels already in the CLAUDE.md category table). Add prompt guidance to reuse
  recurring subcategory/sub-subcategory labels within a batch rather than inventing near-duplicate
  variants (e.g. always "Słodycze", never also "Słodkości" for the same concept) — this is
  advisory, not enforced by validation, matching the existing defensive-parsing posture (ADR-008)
  where the script does not hard-fail on classifier quirks.
- Design the API shape for the drill-down: does the existing `/api/spending/summary` response grow
  an optional nested breakdown, or is there a new endpoint (e.g.
  `GET /api/spending/line-items?year&month&category=X`) returning line items for one category/month
  with their subcategory/sub-subcategory, for the frontend to group client-side? Prefer whichever
  keeps `/api/spending/summary` fast/light for the common case (dashboard load) — drill-down data
  should be fetched lazily only when the user clicks a category, not eagerly with the summary.
- Update `docs/openapi.yaml` (new/changed schema + endnoint), `docs/architecture/02-domain-model-and-schema.md`
  and `05-api-contract.md`, and write a new ADR (`docs/adr/ADR-010-...md`) covering: why free-text
  (not a fixed enum) for these two fields, why nullable/no-backfill for historical rows, and the
  chosen API shape for lazy drill-down fetch.
- Confirm with backend/frontend context whether `corrected`-style manual-edit tracking (existing
  sticky-correction behavior on `category`/`productName`) needs to extend to the two new fields —
  default recommendation: not in this task's scope (no UI to hand-edit subcategory yet), but call
  it out explicitly in the ADR so it's not silently forgotten.

## Backend

- Flyway migration per architect's design.
- `ReceiptLineItem` entity: add `subcategory`, `subSubcategory` fields (nullable String).
- Update the classification-batch endpoint's request DTO + persistence to accept and store the two
  new fields when present in a submitted batch (they're optional per architect's schema — a batch
  missing them for older prompt versions must not fail validation).
- Implement whatever new/changed stats endpoint the architect specifies for per-category line-item
  drill-down (scoped by year+month+category), returning line items with productName, amount,
  quantity, subcategory, subSubcategory.
- MapStruct DTO mapping updates; regenerate/update OpenAPI-derived types if applicable.
- JUnit + Testcontainers coverage: migration applies cleanly, batch submission with/without the new
  fields, new drill-down endpoint returns correct scoped/grouped data, and idempotent resubmission
  behavior (existing rule: never duplicate line items, never clobber a user-corrected line item)
  still holds with the new fields present.

## Frontend

- `CategoryBreakdownChart.tsx`: add a click handler on each category bar (cursor pointer, hover
  affordance) that navigates to (or opens, per architect/UX judgment) a drill-down view for that
  category + the currently selected year/month.
- New drill-down view: fetch line items for that category/month via the new/changed endpoint,
  group by `subcategory` → `subSubcategory` (falling back gracefully — e.g. an "Inne"/ungrouped
  bucket — for older line items where these fields are NULL, since historical data won't have
  them), show item-level rows (productName, amount, quantity) reusing the existing list-row visual
  pattern from `LineItemRow.tsx`/`ReceiptCard.tsx` per the Explore survey done for this task.
  Include a way back to the dashboard, and ideally a link from a line item to its parent receipt
  (`/receipts/:id`).
- Loading/empty states via TanStack Query, consistent with existing dashboard data-fetching
  patterns (`useSpendingSummary`).
- Manually verify in a browser: click a category with data, click one with none, click one where
  some line items predate this feature (NULL subcategory) to confirm the fallback grouping doesn't
  break.

## DevOps

Architect's design (see `docs/adr/ADR-010-line-item-subcategories.md` §5) added a required
cross-batch consistency mechanism after the user flagged that "stay consistent" guidance is
useless across separate stateless `claude -p` runs. `infra/classify/classify-receipts.sh` needs a
new step, spelled out precisely in ADR-010 §5 points 1-6:

1. Right after the existing pending-count check, only on a non-empty run: `curl -sf` to
   `GET {API_BASE}/receipts/subcategory-labels`, same reachability-failure-aborts-the-run handling
   as the adjacent `/receipts/pending` call.
2. Expect `SubcategoryLabelsResponse` (see `docs/openapi.yaml`), `data` may be `[]`.
3. Transform the response into the nested-bullet manifest text format ADR-010 §5 point 4 specifies
   exactly (including the exact literal fallback line for the empty-`data` case) — a `jq` filter
   sketch is given there, illustrative not mandated.
4. Splice that text into `infra/classify/prompt.md` at the literal `{{KNOWN_LABELS_MANIFEST}}`
   placeholder (mid-file, appears exactly once) — **not** a single-line `sed`, since the
   replacement is multi-line (ADR-010 §5 point 5 suggests `awk`/`csplit` head+tail concatenation or
   `perl -0777` slurp substitution). This happens *before* the script's existing final step of
   appending the `id → path` receipts manifest, which stays unchanged at the end of the file.
5. No other behavior change to the script — same single `claude -p` invocation, same
   `--allowedTools "Read"`, same ADR-008 defensive-parsing/failure handling.

Depends on the backend's new `GET /receipts/subcategory-labels` endpoint existing to test against
end-to-end; can implement the script logic in parallel against the OpenAPI contract and verify
once backend is deployed locally.

## Out of scope / do not do

- No bulk reclassification of historical receipts to backfill subcategory/sub-subcategory.
- No DB-level enum/check constraint on the two new fields, no frontend picker/dropdown for them
  (they're read-only display data from the classifier in this task).
- Do not commit, push, or redeploy without the user's go-ahead — leave the working tree for review.
