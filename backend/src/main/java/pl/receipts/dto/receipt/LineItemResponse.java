package pl.receipts.dto.receipt;

import pl.receipts.dto.common.Meta;

public record LineItemResponse(LineItem data, Meta meta) {
    public LineItemResponse(LineItem data) {
        this(data, Meta.now());
    }
}
