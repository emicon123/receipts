package pl.receipts.exception;

/**
 * Maps to 422 — used by the strict (human-input) line-item correction path when a category
 * string doesn't parse. Distinct from the classification-batch path, which validates the same
 * way but never throws (see ClassificationApplierService) — an invalid category there routes
 * the owning receipt to FAILED instead.
 */
public class InvalidLineItemException extends RuntimeException {
    private final String field;

    public InvalidLineItemException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
