package pl.receipts.dto.spending;

import pl.receipts.dto.common.Meta;

public record SpendingSummaryResponse(SpendingSummaryData data, Meta meta) {
    public SpendingSummaryResponse(SpendingSummaryData data) {
        this(data, Meta.now());
    }
}
