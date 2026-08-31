package pl.receipts.dto.receipt;

import java.math.BigDecimal;
import java.time.Instant;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.entity.ReceiptStatus;

public record ReceiptSummary(Long id, ReceiptStatus status, ReceiptSource source, Instant capturedAt,
                              String storeName, BigDecimal totalAmount, String imageUrl,
                              String failureReason, Instant createdAt) {
}
