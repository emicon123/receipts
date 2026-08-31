-- V1__init.sql
-- Initial schema for the receipts app: two tables (receipts, receipt_line_items) and three
-- fixed enums. See docs/architecture/02-domain-model-and-schema.md for the authoritative
-- narrative version of everything below (this file is the executable source of truth for
-- the shape; that doc explains *why*).

-- ---------------------------------------------------------------------------------------
-- Enums
-- ---------------------------------------------------------------------------------------

CREATE TYPE receipt_status_enum AS ENUM ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED');

CREATE TYPE receipt_source_enum AS ENUM ('CAMERA', 'MANUAL');

-- The 11 fixed spending categories. Canonical source of truth for the rules behind each
-- value is CLAUDE.md § Categories at the repo root — this type is a direct translation of
-- that table, not a redefinition of it. Do not add/remove values without updating CLAUDE.md
-- first (and infra/classify/prompt.md, which mirrors the same 11 values for the classifier).
CREATE TYPE spend_category_enum AS ENUM (
    'ALKO',
    'JEDZENIE_KONIECZNE',
    'JEDZENIE_SREDNIE',
    'JEDZENIE_PIERDOLOWATE',
    'RZECZY_PALIWO_INNE_ROZNE',
    'RZECZY_LUKSUSOWE',
    'MYCIE_CHEMIA',
    'ROZRYWKA_RESTAURACJE',
    'RACHUNKI',
    'BOBINEK',
    'SUPLE'
);

-- ---------------------------------------------------------------------------------------
-- receipts
-- ---------------------------------------------------------------------------------------

CREATE TABLE receipts (
    id              BIGSERIAL           PRIMARY KEY,
    status          receipt_status_enum NOT NULL DEFAULT 'PENDING',
    source          receipt_source_enum NOT NULL,
    image_path      TEXT                NULL,
    captured_at     TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    store_name      VARCHAR(200)        NULL,
    total_amount    NUMERIC(10,2)       NOT NULL DEFAULT 0,
    failure_reason  TEXT                NULL,
    processed_at    TIMESTAMPTZ         NULL,
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),

    -- A CAMERA receipt always has an image on disk; a MANUAL entry (e.g. a RACHUNKI bill
    -- typed in by hand) never does. Enforced at the DB level, not just in the service layer.
    CONSTRAINT receipts_image_path_matches_source CHECK (
        (source = 'CAMERA' AND image_path IS NOT NULL) OR
        (source = 'MANUAL' AND image_path IS NULL)
    ),

    -- failure_reason is set if and only if status = 'FAILED'. A quota-exhaustion retry
    -- never touches this row at all (see CLAUDE.md § Daily classification job), and a
    -- reprocess clears both status and failure_reason back to PENDING/NULL together.
    CONSTRAINT receipts_failure_reason_matches_status CHECK (
        (status = 'FAILED') = (failure_reason IS NOT NULL)
    ),

    CONSTRAINT receipts_total_amount_non_negative CHECK (total_amount >= 0)
);

COMMENT ON COLUMN receipts.total_amount IS
    'Derived: SUM(receipt_line_items.amount) for this receipt. Recomputed in the same '
    'transaction as any line-item write (insert/replace on classification, correction, '
    'or manual entry) — never written independently of that sum.';

COMMENT ON COLUMN receipts.processed_at IS
    'Set when the receipt reaches a terminal state (PROCESSED or FAILED) — at classification '
    '-batch time for CAMERA receipts, at creation time for MANUAL receipts (which are '
    'created directly into PROCESSED). Cleared back to NULL by reprocess.';

CREATE INDEX idx_receipts_status ON receipts (status);
CREATE INDEX idx_receipts_captured_at ON receipts (captured_at);

-- ---------------------------------------------------------------------------------------
-- receipt_line_items
-- ---------------------------------------------------------------------------------------

CREATE TABLE receipt_line_items (
    id            BIGSERIAL            PRIMARY KEY,
    receipt_id    BIGINT               NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    product_name  VARCHAR(300)         NOT NULL,
    category      spend_category_enum  NOT NULL,
    amount        NUMERIC(10,2)        NOT NULL,
    quantity      NUMERIC(10,3)        NULL,
    corrected     BOOLEAN              NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ          NOT NULL DEFAULT NOW(),

    CONSTRAINT receipt_line_items_amount_non_negative CHECK (amount >= 0),
    CONSTRAINT receipt_line_items_quantity_positive CHECK (quantity IS NULL OR quantity > 0)
);

COMMENT ON COLUMN receipt_line_items.corrected IS
    'Sticky: set true when a user hand-edits this item''s category/amount/etc via '
    'PUT /api/receipts/{id}/line-items/{itemId}. A later classification-batch replace '
    '(reprocess) deletes and re-inserts only the UNCORRECTED line items for a receipt — '
    'a corrected=true row is never touched by that path.';

CREATE INDEX idx_receipt_line_items_receipt_id ON receipt_line_items (receipt_id);

-- No index on receipt_line_items(category): spending summary/trend queries aggregate over
-- a single user's receipts (low thousands of rows at most) — a sequential scan on a page
-- view is cheap at this scale, and a rarely-hit index would be pure YAGNI overhead here.
