package pl.receipts.dto.spending;

import pl.receipts.dto.common.Meta;

public record SpendingTrendResponse(SpendingTrendData data, Meta meta) {
    public SpendingTrendResponse(SpendingTrendData data) {
        this(data, Meta.now());
    }
}
