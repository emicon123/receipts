package pl.receipts.dto.receipt;

import java.util.List;
import pl.receipts.dto.common.Meta;

public record PendingReceiptsResponse(List<PendingReceiptRef> data, Meta meta) {
    public PendingReceiptsResponse(List<PendingReceiptRef> data) {
        this(data, Meta.now());
    }
}
