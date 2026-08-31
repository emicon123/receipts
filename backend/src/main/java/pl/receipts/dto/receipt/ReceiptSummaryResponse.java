package pl.receipts.dto.receipt;

import pl.receipts.dto.common.Meta;

public record ReceiptSummaryResponse(ReceiptSummary data, Meta meta) {
    public ReceiptSummaryResponse(ReceiptSummary data) {
        this(data, Meta.now());
    }
}
