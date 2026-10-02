-- V4__receipt_image_path_image_import_check.sql
-- Extends V1's receipts_image_path_matches_source CHECK so IMAGE_IMPORT behaves like CAMERA: an
-- image-backed source always has an image on disk (image_path IS NOT NULL); MANUAL never does.
-- See docs/adr/ADR-014-image-import-source.md.
--
-- Must be a separate migration from V3: this constraint references the 'IMAGE_IMPORT' literal,
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
    (source IN ('CAMERA', 'IMAGE_IMPORT') AND image_path IS NOT NULL) OR
    (source = 'MANUAL' AND image_path IS NULL)
);

COMMENT ON COLUMN receipts.source IS
    'Where the receipt came from: CAMERA (captured live by the in-app camera input), IMAGE_IMPORT '
    '(an existing image picked from the gallery/file picker, pasted from the clipboard or dropped '
    '— a screenshot of a digital receipt OR a photo of a paper one; the classifier decides by '
    'looking), MANUAL (typed in, no image, created PROCESSED). CAMERA and IMAGE_IMPORT are both '
    'image-backed and classified by the daily batch; the classifier is told which one it is '
    'reading (infra/classify/prompt.md). Immutable after insert.';
