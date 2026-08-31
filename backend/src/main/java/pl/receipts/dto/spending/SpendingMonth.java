package pl.receipts.dto.spending;

import java.math.BigDecimal;
import java.util.List;

public record SpendingMonth(int month, BigDecimal totalAmount, List<CategoryAmount> categories) {
}
