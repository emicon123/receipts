package pl.receipts.repository.projection;

import java.math.BigDecimal;
import java.time.Instant;
import pl.receipts.entity.SpendCategory;

/**
 * One row per {@code (category, exact subcategory spelling)} for a month — backs
 * GET /spending/subcategory-summary (ADR-013) via
 * {@link pl.receipts.repository.ReceiptLineItemRepository#sumBySubcategoryVariant}.
 * {@code subcategory} may be {@code null}/blank (unlabeled line items); normalization into
 * groups happens in {@link pl.receipts.service.SubcategorySpendingAggregator}.
 */
public interface SubcategoryVariantTotalRow {
    SpendCategory getCategory();

    String getSubcategory();

    BigDecimal getTotal();

    long getItemCount();

    Instant getLastCapturedAt();
}
