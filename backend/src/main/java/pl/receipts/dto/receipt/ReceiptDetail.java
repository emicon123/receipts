package pl.receipts.dto.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.entity.ReceiptStatus;

/** Flattened equivalent of the OpenAPI allOf [ReceiptSummary, {processedAt, lineItems}]. */
public record ReceiptDetail(Long id, ReceiptStatus status, ReceiptSource source, Instant capturedAt,
                             String storeName, BigDecimal totalAmount, String imageUrl,
                             String failureReason, Instant createdAt, Instant processedAt,
                             List<LineItem> lineItems) {
}
