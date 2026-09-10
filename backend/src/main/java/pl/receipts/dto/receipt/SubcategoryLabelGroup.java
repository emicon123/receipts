package pl.receipts.dto.receipt;

import java.util.List;

/**
 * One known {@code subcategory} value within a category, plus its known {@code subSubcategory}
 * values — see GET /receipts/subcategory-labels (ADR-010 § Cross-batch label consistency).
 * {@code subSubcategories} is ranked most-used first (most-recently-used as the tiebreak) and
 * capped at 30 entries.
 */
public record SubcategoryLabelGroup(String subcategory, List<String> subSubcategories) {
}
