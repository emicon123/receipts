-- V3__receipt_source_screenshot.sql
-- Adds the SCREENSHOT value to receipt_source_enum (docs/adr/ADR-014-screenshot-source.md): a
-- receipt created from a pasted/picked screenshot instead of a camera photo. It is image-backed
-- exactly like CAMERA and follows the same PENDING -> PROCESSING -> PROCESSED | FAILED lifecycle.
--
-- THIS FILE MUST CONTAIN ONLY THE ALTER TYPE. PostgreSQL refuses to *use* a freshly added enum
-- value (e.g. in a CHECK constraint, an index predicate, or an INSERT) until the transaction
-- that added it has committed (SQLSTATE 55P04 "unsafe use of new value"). Flyway runs each
-- versioned migration in its own transaction (spring.flyway.group is left at its default of
-- false — do NOT enable it, or V3 and V4 would share one transaction and V4 would fail), so the
-- constraint that references 'SCREENSHOT' lives in V4, which runs after this one has committed.
--
-- IF NOT EXISTS keeps a manual re-run harmless; Flyway itself never re-applies a recorded version.
-- Existing rows are untouched — no backfill is possible or wanted (every pre-existing image
-- receipt is a camera photo as far as the system knows, and stays CAMERA).

ALTER TYPE receipt_source_enum ADD VALUE IF NOT EXISTS 'SCREENSHOT';
