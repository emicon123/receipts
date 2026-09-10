-- V2__line_item_subcategories.sql
-- Adds two nullable, free-text classification fields to receipt_line_items: subcategory and
-- sub_subcategory. Unlike spend_category_enum (see ADR-005), these are deliberately NOT a
-- fixed enum/lookup table — Claude assigns them per line item at classification time
-- (infra/classify/prompt.md) and is only asked to stay *consistent* within a batch, not to
-- draw from a closed list. See docs/adr/ADR-010-line-item-subcategories.md for the full
-- reasoning, including why existing rows are left NULL rather than backfilled.

ALTER TABLE receipt_line_items
    ADD COLUMN subcategory     VARCHAR(100) NULL,
    ADD COLUMN sub_subcategory VARCHAR(100) NULL;

COMMENT ON COLUMN receipt_line_items.subcategory IS
    'Free-text, classifier-assigned grouping one level finer than category (e.g. "Slodycze" '
    'under JEDZENIE_PIERDOLOWATE). No enum/check constraint and no validation (ADR-010) — '
    'Claude is asked to reuse recurring labels within a batch, but this is advisory only. '
    'NULL for line items predating this column (no backfill in scope) and for any '
    'classification-batch/manual entry that omits it.';

COMMENT ON COLUMN receipt_line_items.sub_subcategory IS
    'Free-text, classifier-assigned grouping one level finer than subcategory (e.g. "zelki" '
    'under "Slodycze"). Same nullability and no-validation rules as subcategory (ADR-010).';

-- No index: same reasoning already recorded in V1 for receipt_line_items.category — a
-- single-user dataset stays in the low thousands of rows, and the one query that reads these
-- columns (GET /api/spending/line-items) is already scoped to one month + one category before
-- it ever looks at subcategory/sub_subcategory, so a sequential scan over that small slice is
-- cheap. Revisit only if the data-scale assumption changes.
