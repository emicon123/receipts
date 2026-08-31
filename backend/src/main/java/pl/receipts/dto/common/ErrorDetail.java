package pl.receipts.dto.common;

public record ErrorDetail(String code, String field, String message) {
    public ErrorDetail(String code, String message) {
        this(code, null, message);
    }
}
