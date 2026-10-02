package pl.receipts.dto.receipt;

import pl.receipts.entity.ReceiptSource;

public record PendingReceiptRef(Long id, ReceiptSource source) {
}
