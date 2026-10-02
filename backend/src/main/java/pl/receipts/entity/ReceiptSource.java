package pl.receipts.entity;

/**
 * Mirrors the Postgres {@code receipt_source_enum} type (V1__init.sql, extended by
 * V3__receipt_source_image_import.sql). {@code CAMERA} and {@code IMAGE_IMPORT} are both
 * image-backed and created {@code PENDING}; {@code IMAGE_IMPORT} names the channel (an existing
 * image from the gallery, file picker or clipboard), not the content — see ADR-014.
 */
public enum ReceiptSource {
    CAMERA,
    MANUAL,
    IMAGE_IMPORT
}
