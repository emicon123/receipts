package pl.receipts.dto.spending;

import java.math.BigDecimal;
import java.util.List;

public record SpendingSummaryData(int year, int month, BigDecimal totalAmount, List<CategoryAmount> categories) {
}
