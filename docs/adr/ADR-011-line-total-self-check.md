# ADR-011: `amount` = Line Total, and a Receipt-Total Self-Check in Classification

**Date:** 2026-09-10
**Status:** Accepted

> **Numbering note:** live-checked against `docs/adr/` at write time (2026-09-10). The latest
> merged ADR was ADR-009. The still-queued `.claude/tasks/04-category-drilldown.task.md` also
> reserved "ADR-010" for its own unrelated topic (subcategory/sub-subcategory drill-down); that
> task's Architect work landed concurrently with this one and claimed ADR-010
> (`ADR-010-line-item-subcategories.md`) first, so this ADR takes the next free number, ADR-011,
> per that task's own instruction to check at execution time rather than assume.

**Context:** A user-reported bug (2026-09-10): a Kaufland receipt printed a total of 23.94 zł. Its
San Pellegrino Rosa line was actually 6 units at 3.49 zł/unit (`6 x 3,49` → a 20.94 zł line total),
but Claude's classification reported it as 1 unit at 3.49 zł — undercounting that line, and the
whole receipt, by roughly 17.45 zł.

Root cause, confirmed by reading the code:
- `ClassificationApplierService.applyItem()` computes `receipts.total_amount` as a plain
  `SUM(receipt_line_items.amount)` — never `amount * quantity`. So `amount` has always been the
  wire contract's *authoritative line total*, and `quantity` has always been display-only
  metadata the backend never applies as a multiplier.
- `infra/classify/prompt.md` never stated that semantic. It described `amount` as "the item's
  price, as a plain number" — genuinely ambiguous between unit price and line total — and its own
  worked example (`{"amount": 4.20, "quantity": 2}`) did nothing to disambiguate, and in fact
  reads naturally as a unit price. Claude evidently read the unit price printed next to the
  product name and didn't apply the receipt's quantity-multiplier notation, emitting the unit
  price as if it were the whole line.
- Nothing in the pipeline cross-checks a receipt's extracted line items against its own printed
  grand total, at any stage — Claude has no self-verification instruction, the wrapper script does
  no sum check, and the backend just sums whatever it's given and stores it as truth. Nothing would
  have caught this class of error before it reached the database.

**Decision:**

1. **Clarify `amount`'s semantics in `infra/classify/prompt.md`.** It is now stated explicitly:
   `amount` is the line's total contribution to the receipt — already multiplied out for
   quantity/weight — never a unit price; `quantity` is the printed unit count, display-only,
   never applied as a multiplier downstream. The prompt names the specific "quantity × unit price
   → line total" pattern common on Polish paragony (sometimes one line: `6 x 3,49` followed by the
   computed total; sometimes split across lines: unit price/quantity above or below a separate
   line-total figure) as a concrete thing to watch for, and the worked example was fixed so it's
   unambiguous under this rule (`"Piwo Tyskie 0,5l"` printed as `2 x 4,20` now shows
   `amount: 8.40, quantity: 2`, not `amount: 4.20`).
2. **New `total` field, per photo-receipt entry, verification-only.** Each `items[]` entry for a
   photo receipt (`path=` id) may now carry `total`: the receipt's own printed grand total, if
   legible. If illegible, Claude omits the field rather than guessing — a guessed total would
   defeat the point of the check. It does not apply to bank-transaction entries, whose single
   line item already is the given transaction amount by construction.
3. **In-prompt self-check, tolerance ±0.05 zł.** Before finalizing a photo receipt's entry, Claude
   sums its `lineItems[].amount`, compares to its own extracted `total`, and — if they disagree by
   more than 0.05 zł — re-examines the image specifically for a missed quantity/weight multiplier,
   a skipped line, or a misread digit, correcting before emitting final output. 0.05 zł was chosen
   as large enough to absorb ordinary grosz-level rounding across several lines, but far smaller
   than the failure this is meant to catch (the reported bug undercounted by ~17 zł — over 300×
   the tolerance). The prompt also instructs that a mismatch surviving re-examination (e.g. a
   whole-receipt discount deducted once rather than broken out per line) is not necessarily an
   extraction error — the check exists to catch missed multipliers/lines/digits, not to force an
   artificial reconciliation.
4. **Independent script-side sanity check, non-blocking.** `classify-receipts.sh` (DevOps-owned;
   this ADR is the contract it implements against) re-runs the same sum-vs-`total` comparison via
   `jq` on each `items[]` entry that carries a `total`, after JSON extraction/validation (ADR-008)
   and before POSTing. A mismatch beyond the same ±0.05 zł tolerance logs a WARNING (receipt id,
   computed sum, printed total, delta) — this is a diagnostic safety net catching this bug class
   going forward (the user explicitly suggested "może nawet być odpalany jakiś skrypt"), **not** a
   gate: the batch still submits as-is. This matches the job's existing philosophy (CLAUDE.md
   "Quality gates": never crash or withhold the whole batch over one item's issue) and the same
   reasoning behind treating this as heuristic rather than authoritative — a legitimate mismatch
   can occur (a whole-receipt discount not broken out per line, receipt-level rounding). It is
   never grounds to mark a receipt `FAILED`; that stays reserved for genuinely unreadable/
   unparseable content Claude itself reports.
5. **`total` is stripped before the batch is POSTed — script-side only, no backend change.**
   `ClassificationBatchItem` (`backend/.../dto/classification/ClassificationBatchItem.java`) has
   no field for `total`, and — verified directly in `application.yml` — this app never sets
   `spring.jackson.deserialization.fail-on-unknown-properties: false`, so Jackson's own default
   (`FAIL_ON_UNKNOWN_PROPERTIES = true`) applies unmodified. Concretely, this means an unstripped
   `total` would not be silently ignored — it would cause Spring to reject the **entire**
   `classification-batch` request with a 400, taking down every receipt in that run, not just
   degrade gracefully. Stripping it (`jq 'del(.total)'`, after the sanity check, before the POST)
   is therefore not just the lighter option but the only correct one that doesn't regress the
   whole pipeline.

   The heavier alternative — extending `ClassificationBatchItem`/`docs/openapi.yaml`, adding a
   migration column, and surfacing mismatches in the UI — was considered and rejected for now:
   `total` has no read side today (no UI to show it, no review workflow beyond what the log line
   already gives a human tailing `classify-receipts.log`), so persisting it would be exactly the
   kind of premature abstraction YAGNI exists to block. If mismatches turn out to be frequent
   enough that a persistent, queryable review surface is worth building, that's a follow-up task
   with its own Backend/Frontend scope — not a reason to widen this fix now.

**Consequences:**
- No DB migration, no `ClassificationBatchItem`/OpenAPI change. The wire contract to
  `POST /api/receipts/classification-batch` is byte-for-byte unchanged; only what
  `classify-receipts.sh` sends is now guaranteed to omit `total`, matching ADR-008's precedent of
  putting the actual correctness guarantee in the script, not in prompt wording alone.
  `docs/architecture/04-classification-flow.md`'s Happy Path diagram/notes were updated in place
  to show Claude's self-check step and the script's sanity-check + strip steps — see that doc for
  the exact sequencing (between "defensively extract JSON" and "POST classification-batch").
- `classify-receipts.sh` must implement the sanity check and the `jq 'del(.total)'` strip exactly
  as described in point 4/5 above, additively, alongside — not replacing — ADR-008's existing
  defensive-parsing and `set -e`-safety behavior.
- This does not retroactively fix any already-misclassified receipt (including the specific
  Kaufland receipt from the bug report) — that requires the existing manual `reprocess` endpoint,
  triggered by the user, once this fix is deployed. No bulk reclassification is part of this ADR.
- Both the prompt-side self-check and the script-side sanity check are best-effort/heuristic, not
  guarantees — same posture as ADR-008 toward prompt compliance generally. A future recurrence of
  undercounted totals should prompt inspection of the script's WARNING log first (it now has the
  computed-sum/printed-total/delta for every flagged receipt), not another isolated prompt tweak.
- No new patterns/classes were introduced (per `software-design-excellence`'s YAGNI tie-break):
  this is a contract clarification plus a diagnostic field on an already-existing script/prompt
  pipeline, not new domain structure — the existing Happy Path `sequenceDiagram` was extended in
  place rather than forking a new diagram.
