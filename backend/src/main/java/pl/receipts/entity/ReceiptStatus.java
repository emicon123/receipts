package pl.receipts.entity;

/**
 * Mirrors the Postgres {@code receipt_status_enum} type (see V1__init.sql).
 * Lifecycle: PENDING -&gt; PROCESSING (transient, held only for the duration of one
 * classification-batch write) -&gt; PROCESSED | FAILED. See docs/architecture/03-receipt-lifecycle.md.
 */
public enum ReceiptStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
