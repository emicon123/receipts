# Fix quantity mis-extraction via receipt-total self-check

User bug report (2026-09-10): Kaufland receipt, 09.09.2026, printed total 23.94 zł. San Pellegrino
Rosa is actually **6 units** on the receipt (6 × 3.49 = 20.94 zł), but Claude's classification
reported it as **1 unit at 3.49 zł**, undercounting that line by ~17.45 zł and therefore
undercounting the whole receipt. User's explicit ask (paraphrased): the prompt must look at the
receipt's own printed PLN total and check whether the extracted line items sum to it — "może
nawet być odpalany jakiś skrypt" (a script could even be run to do this check).

## Root cause (confirmed by reading the code, not guessed)

- `backend/src/main/java/pl/receipts/service/ClassificationApplierService.java`'s
  `applyItem()` computes the receipt's `totalAmount` as
  `lineItemRepository.sumAmountByReceiptId(receipt.getId())` — a **plain SUM of the `amount`
  column**, never `amount * quantity`. So the wire contract's `amount` field
  (`ClassificationLineItemInput.amount`, `infra/classify/prompt.md`'s per-line-item `amount`) is
  authoritatively **the line's total contribution to the receipt**, and `quantity` is
  informational/display-only metadata, not a multiplier the backend ever applies.
- `infra/classify/prompt.md` never states this semantic. It says `amount` is "the item's price,
  as a plain number" — genuinely ambiguous between unit price and line total — and its own
  example (`{"amount": 4.20, "quantity": 2}`) does nothing to disambiguate. For the San Pellegrino
  line, Claude evidently read the unit price (3.49) printed next to the product name and did not
  notice/apply the receipt's quantity-multiplier notation for that line (Polish receipts commonly
  print something like `6 x 3,49` and/or a separate line-total figure), so it emitted the unit
  price as if it were the whole line.
- There is currently **no cross-check against the receipt's own printed grand total anywhere** in
  the pipeline — nothing catches this class of error today, at any stage (Claude has no
  instruction to self-verify; the script does no sum check; the backend just sums whatever it's
  given and stores it as truth).

## Scope for this task

**Architect** (must run first — this is a contract change to `infra/classify/prompt.md`'s output
schema):

- Rewrite the line-item extraction section of `infra/classify/prompt.md` to state explicitly,
  unambiguously, that `amount` is **the line's total price as it contributes to the receipt's
  grand total** (i.e. already accounting for quantity/weight), not a per-unit price — and that
  `quantity` is the printed unit count, kept for display/record purposes only. Update the
  worked example so it's unambiguous under this rule (e.g. an item explicitly printed as
  `6 x 3,49` should show `amount: 20.94, quantity: 6`, not `amount: 3.49`).
- Add explicit guidance to actively look for the "quantity × unit price → line total" pattern
  common on Polish paragony (sometimes on one line, sometimes the unit price/quantity printed
  above or below the line total) — this is the specific failure mode from the bug report, so name
  it concretely as a thing to watch for, not just a generic "be careful" instruction.
- Add a new **`total`** field to each `items[]` entry (sibling to `storeName`/`capturedAt`) —
  the receipt's own printed grand total, if legible. Instruct Claude to, before finalizing output
  for a receipt: sum all of that receipt's `lineItems[].amount`, compare to the extracted `total`,
  and if they don't match (beyond ordinary rounding — pick and state a small tolerance, e.g.
  ≤0.05 zł), re-examine the image specifically for a missed quantity multiplier, a missed line, or
  a misread digit, and correct before emitting the final JSON — rather than silently emitting a
  total that doesn't reconcile. If the receipt's own total is illegible, omit `total` rather than
  guessing it.
- This `total` field is for verification only — it is **not** part of
  `ClassificationBatchItem`'s existing wire schema to the backend
  (`backend/src/main/java/pl/receipts/dto/classification/ClassificationBatchItem.java` has no
  slot for it, and Spring's default Jackson config rejects unknown JSON properties — verify this
  assumption still holds before relying on it). Decide and document explicitly in the ADR whether
  the wrapper script strips this field before POSTing to
  `/api/receipts/classification-batch` (recommended — keeps the backend contract/OpenAPI spec
  completely unchanged, no new persisted column, no migration) or whether it's worth a small
  backend/OpenAPI extension to accept and store it for later UI surfacing (only pick this heavier
  option if it's clearly low-cost; default recommendation is script-side-only, out of backend
  scope, since the user's ask was about catching the error, not building a new review UI).
- Update `docs/architecture/04-classification-flow.md`'s Happy Path diagram/notes to document the
  new self-check step inside Claude's box, and the new script-side sanity-check step (see DevOps
  below) between "defensively extract JSON" and "POST classification-batch."
- Write a new ADR documenting this (root cause, the `amount`-is-line-total clarification, the
  `total` field + tolerance, and the script-strip decision). **Check `docs/adr/` at execution
  time for the next free number** — `docs/adr/ADR-009-store-name-suggestion-ranking.md` is the
  latest merged one, but the still-queued `.claude/tasks/04-category-drilldown.task.md` also
  reserves "ADR-010" for its own unrelated topic; whichever task actually runs first claims
  ADR-010 and the other must take the next number after that at execution time — do not assume
  ADR-010 is free without checking `docs/adr/` first.

**DevOps** (after Architect's prompt.md/ADR land — needs the exact `total` field name/semantics
and tolerance the Architect settles on):

- In `infra/classify/classify-receipts.sh`, after `extract_batch_json` succeeds and before the
  `POST .../classification-batch` call: for each entry in `batch.items[]` that has a `total`
  field, compute `sum(lineItems[].amount)` via `jq` and compare to `total` using the Architect's
  tolerance. On a mismatch, `log` a WARNING (receipt id, computed sum, printed total, delta) —
  **non-blocking**: still submit the batch as-is. This is a diagnostic/observability aid for
  catching this bug class going forward (per the user's "może być odpalany jakiś skrypt" ask), not
  a hard gate — matches this job's existing philosophy (see CLAUDE.md "Quality gates": never
  crash/withhold the whole batch over one item's issue; a human reviews results afterward).
  Don't mark anything FAILED for a sum mismatch — `FAILED` is reserved for genuine
  unreadable/unparseable content per CLAUDE.md, and a mismatch here is a heuristic flag, not a
  confirmed content failure (legitimate mismatches can happen: receipt-level rounding, a
  whole-receipt discount not broken out per line, etc.).
- If the Architect's ADR says to strip `total` before POSTing (the default recommendation above),
  do that with `jq 'del(.total)'` (or equivalent) on each item after the sum check, so the
  outgoing payload matches the backend's existing `ClassificationBatchItem` schema exactly,
  unchanged.
- Keep the existing ADR-008 defensive-parsing behavior and `set -e`-safety conventions in this
  script exactly as they are (see the existing `extract_batch_json` comment block) — this is an
  additive step, not a rewrite.

## Out of scope / do not do

- No Backend or Frontend agent work unless the Architect's ADR explicitly decides the heavier
  "persist `total`/mismatch for UI review" option — default expectation is this stays
  prompt.md + classify-receipts.sh only.
- No bulk reclassification of past receipts (including the specific Kaufland 09.09.2026 receipt
  from the bug report) as part of this task — that's a manual follow-up the user can trigger via
  the existing reprocess endpoint once the fix is deployed, not something to script here.
- Do not commit, push, or redeploy without the user's go-ahead — leave the working tree for
  review, per this session's Auto Mode guidance (state changes are fine to make directly, but
  git/deploy actions still get confirmed).
