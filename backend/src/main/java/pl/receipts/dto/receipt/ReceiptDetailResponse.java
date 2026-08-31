package pl.receipts.dto.receipt;

import pl.receipts.dto.common.Meta;

public record ReceiptDetailResponse(ReceiptDetail data, Meta meta) {
    public ReceiptDetailResponse(ReceiptDetail data) {
        this(data, Meta.now());
    }
}
