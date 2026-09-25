package pl.receipts.dto.spending;

import java.math.BigDecimal;
import java.util.List;
import pl.receipts.entity.SpendCategory;

/**
 * One category's month spend split by subcategory (ADR-013).
 * {@code totalAmount == unlabeledAmount + Σ subcategories[].amount}.
 */
public record CategorySubcategoryBreakdown(SpendCategory category, BigDecimal totalAmount,
                                           BigDecimal unlabeledAmount, List<SubcategoryAmount> subcategories) {
}
