package pl.receipts.service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.receipts.dto.receipt.LineItem;
import pl.receipts.dto.receipt.LineItemCorrectionRequest;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.SpendCategory;
import pl.receipts.exception.InvalidLineItemException;
import pl.receipts.mapper.LineItemMapper;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;

/**
 * PUT /receipts/{id}/line-items/{itemId} — the one place {@code corrected} is ever set to true
 * (CLAUDE.md rule 7). Never touches {@code receipts.status}; recomputes {@code total_amount} in
 * the same transaction (CLAUDE.md rule 2).
 *
 * <p>Only non-null fields in the request are applied — see {@link LineItemCorrectionRequest}'s
 * Javadoc for the one documented limitation this implies (can't explicitly clear
 * {@code quantity} back to null through this endpoint).
 */
@Service
public class LineItemCorrectionService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptLineItemRepository lineItemRepository;
    private final LineItemMapper lineItemMapper;

    public LineItemCorrectionService(ReceiptRepository receiptRepository,
                                      ReceiptLineItemRepository lineItemRepository,
                                      LineItemMapper lineItemMapper) {
        this.receiptRepository = receiptRepository;
        this.lineItemRepository = lineItemRepository;
        this.lineItemMapper = lineItemMapper;
    }

    @Transactional
    public LineItem correct(Long receiptId, Long itemId, LineItemCorrectionRequest request) {
        ReceiptLineItem lineItem = lineItemRepository.findByIdAndReceiptId(itemId, receiptId)
                .orElseThrow(() -> new NoSuchElementException(
                        "line item " + itemId + " not found on receipt " + receiptId));

        if (request.productName() != null) {
            lineItem.setProductName(request.productName());
        }
        if (request.category() != null) {
            SpendCategory category = SpendCategory.tryParse(request.category())
                    .orElseThrow(() -> new InvalidLineItemException("category",
                            "must be one of the 11 fixed categories"));
            lineItem.setCategory(category);
        }
        if (request.amount() != null) {
            lineItem.setAmount(request.amount());
        }
        if (request.quantity() != null) {
            lineItem.setQuantity(request.quantity());
        }
        lineItem.setCorrected(true);
        lineItemRepository.save(lineItem);

        BigDecimal newTotal = lineItemRepository.sumAmountByReceiptId(receiptId);
        receiptRepository.findById(receiptId).ifPresent(receipt -> receipt.setTotalAmount(newTotal));

        return lineItemMapper.toDto(lineItem);
    }
}
