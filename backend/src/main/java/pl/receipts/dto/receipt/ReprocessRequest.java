package pl.receipts.dto.receipt;

public record ReprocessRequest(Boolean force) {
    public boolean forceOrDefault() {
        return Boolean.TRUE.equals(force);
    }
}
