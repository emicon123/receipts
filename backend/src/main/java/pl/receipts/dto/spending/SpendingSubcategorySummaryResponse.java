package pl.receipts.dto.spending;

import pl.receipts.dto.common.Meta;

public record SpendingSubcategorySummaryResponse(SpendingSubcategorySummaryData data, Meta meta) {
    public SpendingSubcategorySummaryResponse(SpendingSubcategorySummaryData data) {
        this(data, Meta.now());
    }
}
