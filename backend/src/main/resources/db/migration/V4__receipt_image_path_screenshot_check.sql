-- V4__receipt_image_path_screenshot_check.sql
-- Extends V1's receipts_image_path_matches_source CHECK so SCREENSHOT behaves like CAMERA: an
-- image-backed source always has an image on disk (image_path IS NOT NULL); MANUAL never does.
-- See docs/adr/ADR-014-screenshot-source.md.
--
-- Must be a separate migration from V3: this constraint references the 'SCREENSHOT' literal,
-- which is unusable until V3's transaction (the one that ran ALTER TYPE ... ADD VALUE) has
-- committed. Flyway's one-transaction-per-versioned-migration default guarantees that ordering.
--
-- The constraint name is kept, so docs and tests that refer to it by name stay valid. Existing
-- rows are all CAMERA-with-path or MANUAL-without-path, so re-validation on ADD CONSTRAINT passes;
-- at this table's size the brief ACCESS EXCLUSIVE lock is irrelevant.
--
-- (When BANK_IMPORT lands, docs/architecture/06-bank-integration.md's follow-up migration must
-- extend this same constraint again, and its own ADD VALUE must likewise live in a migration
-- of its own, ahead of the one that uses it.)

ALTER TABLE receipts DROP CONSTRAINT receipts_image_path_matches_source;

ALTER TABLE receipts ADD CONSTRAINT receipts_image_path_matches_source CHECK (
    (source IN ('CAMERA', 'SCREENSHOT') AND image_path IS NOT NULL) OR
    (source = 'MANUAL' AND image_path IS NULL)
);

COMMENT ON COLUMN receipts.source IS
    'Where the receipt came from: CAMERA (photo of a paper receipt), SCREENSHOT (pasted/picked '
    'image of a digital receipt/order summary), MANUAL (typed in, no image, created PROCESSED). '
    'CAMERA and SCREENSHOT are both image-backed and classified by the daily batch; the '
    'classifier is told which one it is reading (infra/classify/prompt.md). Immutable after insert.';
