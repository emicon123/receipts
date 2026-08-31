package pl.receipts.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import pl.receipts.dto.receipt.LineItem;
import pl.receipts.entity.ReceiptLineItem;

@Mapper(componentModel = "spring")
public interface LineItemMapper {

    LineItem toDto(ReceiptLineItem entity);

    List<LineItem> toDtoList(List<ReceiptLineItem> entities);
}
