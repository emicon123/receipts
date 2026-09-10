package pl.receipts.dto.spending;

import java.math.BigDecimal;
import java.time.Instant;
import pl.receipts.entity.SpendCategory;

/**
 * Flattened equivalent of the OpenAPI {@code allOf} [LineItem, {receiptId, storeName, capturedAt}]
 * — one entry in GET /spending/line-items' response (ADR-010 §3). {@code storeName}/
 * {@code capturedAt} are denormalized from the parent receipt so the frontend drill-down view
 * doesn't need a second round-trip per item; {@code receiptId} lets it link back to
 * GET /receipts/{id}.
 */
public record SpendingLineItem(Long id, String productName, SpendCategory category, BigDecimal amount,
                                BigDecimal quantity, String subcategory, String subSubcategory, boolean corrected,
                                Long receiptId, String storeName, Instant capturedAt) {
}
