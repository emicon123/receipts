package pl.receipts.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pl.receipts.dto.receipt.ReceiptDetail;
import pl.receipts.dto.receipt.ReceiptSummary;
import pl.receipts.entity.Receipt;

/**
 * Entity -&gt; DTO only (JPA entities never leave the persistence layer — architecture rule).
 * {@code imageUrl} is derived (not a plain field) so it gets an explicit expression mapping
 * rather than a 1:1 property match.
 */
@Mapper(componentModel = "spring", uses = LineItemMapper.class)
public interface ReceiptMapper {

    @Mapping(target = "imageUrl", expression = "java(imageUrl(receipt))")
    ReceiptSummary toSummary(Receipt receipt);

    @Mapping(target = "imageUrl", expression = "java(imageUrl(receipt))")
    ReceiptDetail toDetail(Receipt receipt);

    default String imageUrl(Receipt receipt) {
        return receipt.getImagePath() == null ? null : "/api/receipts/" + receipt.getId() + "/image";
    }
}
