package pl.receipts.exception;

/**
 * Maps to 400 — the one case where classification-batch rejects the whole request: a
 * structurally malformed body (missing {@code items}/{@code failures} keys entirely). Every
 * other per-entry problem (unknown receiptId, invalid category) is tolerated, never thrown as
 * this.
 */
public class MalformedBatchRequestException extends RuntimeException {
    public MalformedBatchRequestException(String message) {
        super(message);
    }
}
