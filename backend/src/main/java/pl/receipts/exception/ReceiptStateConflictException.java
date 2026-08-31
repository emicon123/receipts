package pl.receipts.exception;

/** Maps to 409 — reprocessing a PROCESSED/PROCESSING receipt without {@code force: true}. */
public class ReceiptStateConflictException extends RuntimeException {
    public ReceiptStateConflictException(String message) {
        super(message);
    }
}
