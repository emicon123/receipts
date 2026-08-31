package pl.receipts.dto.receipt;

import java.util.List;
import pl.receipts.dto.common.Meta;
import pl.receipts.dto.common.PageInfo;

public record ReceiptListResponse(List<ReceiptSummary> data, Meta meta, PageInfo page) {
    public ReceiptListResponse(List<ReceiptSummary> data, PageInfo page) {
        this(data, Meta.now(), page);
    }
}
