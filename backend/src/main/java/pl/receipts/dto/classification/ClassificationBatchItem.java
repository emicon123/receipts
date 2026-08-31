package pl.receipts.dto.classification;

import java.time.LocalDate;
import java.util.List;

public record ClassificationBatchItem(Long receiptId, String storeName, LocalDate capturedAt,
                                       List<ClassificationLineItemInput> lineItems) {
}
