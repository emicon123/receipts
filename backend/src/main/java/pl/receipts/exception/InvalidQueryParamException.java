package pl.receipts.exception;

/** Maps to 400 — an invalid query-param combination (e.g. month without year). */
public class InvalidQueryParamException extends RuntimeException {
    private final String field;

    public InvalidQueryParamException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
