package pl.receipts.dto.spending;

import java.util.List;

public record SpendingTrendData(int year, List<SpendingMonth> months) {
}
