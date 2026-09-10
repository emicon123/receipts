package pl.receipts.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.receipts.dto.receipt.LineItem;
import pl.receipts.dto.spending.SpendingLineItem;
import pl.receipts.entity.ReceiptLineItem;

@Mapper(componentModel = "spring")
public interface LineItemMapper {

    LineItem toDto(ReceiptLineItem entity);

    List<LineItem> toDtoList(List<ReceiptLineItem> entities);

    /**
     * Backs GET /spending/line-items (ADR-010 §3) — {@code receiptId}/{@code storeName}/
     * {@code capturedAt} are denormalized off the parent {@code receipt}, not plain properties of
     * {@link ReceiptLineItem} itself, hence the explicit nested-source mappings.
     */
    @Mapping(target = "receiptId", source = "receipt.id")
    @Mapping(target = "storeName", source = "receipt.storeName")
    @Mapping(target = "capturedAt", source = "receipt.capturedAt")
    SpendingLineItem toSpendingLineItem(ReceiptLineItem entity);

    List<SpendingLineItem> toSpendingLineItemList(List<ReceiptLineItem> entities);
}
