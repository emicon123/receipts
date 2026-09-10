package pl.receipts.dto.spending;

import java.util.List;
import pl.receipts.dto.common.Meta;

public record SpendingLineItemsResponse(List<SpendingLineItem> data, Meta meta) {
    public SpendingLineItemsResponse(List<SpendingLineItem> data) {
        this(data, Meta.now());
    }
}
