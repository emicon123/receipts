package pl.receipts.dto.receipt;

import java.util.List;
import pl.receipts.entity.SpendCategory;

/**
 * One category's known subcategories — see GET /receipts/subcategory-labels. {@code subcategories}
 * is ranked most-used first (most-recently-used as the tiebreak); no cap at this level (ADR-010).
 */
public record CategorySubcategoryLabels(SpendCategory category, List<SubcategoryLabelGroup> subcategories) {
}
