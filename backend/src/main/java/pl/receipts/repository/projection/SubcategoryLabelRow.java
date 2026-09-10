package pl.receipts.repository.projection;

import java.time.Instant;
import pl.receipts.entity.SpendCategory;

/**
 * Flat, already-filtered ({@code subcategory} non-null/non-blank) projection backing
 * GET /receipts/subcategory-labels — see {@link pl.receipts.repository.ReceiptLineItemRepository
 * #findSubcategoryLabelRows()}. The normalize/rank/cap/group steps happen in the service layer
 * (see docs/architecture/02-domain-model-and-schema.md § Known Subcategory/Sub-Subcategory
 * Labels, which explicitly sanctions this over a fully nested SQL CTE) via
 * {@link pl.receipts.service.SubcategoryLabelGrouper}.
 */
public interface SubcategoryLabelRow {
    SpendCategory getCategory();

    String getSubcategory();

    String getSubSubcategory();

    Instant getCapturedAt();
}
