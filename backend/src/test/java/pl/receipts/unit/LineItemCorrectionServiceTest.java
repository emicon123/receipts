package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.receipts.dto.receipt.LineItemCorrectionRequest;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.SpendCategory;
import pl.receipts.exception.InvalidLineItemException;
import pl.receipts.mapper.LineItemMapper;
import pl.receipts.mapper.LineItemMapperImpl;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;
import pl.receipts.service.LineItemCorrectionService;

@ExtendWith(MockitoExtension.class)
class LineItemCorrectionServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptLineItemRepository lineItemRepository;

    private LineItemMapper lineItemMapper;
    private LineItemCorrectionService service;

    @BeforeEach
    void setUp() {
        lineItemMapper = new LineItemMapperImpl();
        service = new LineItemCorrectionService(receiptRepository, lineItemRepository, lineItemMapper);
    }

    @Test
    void onlyProvidedFieldsAreAppliedAndCorrectedFlagIsSet() {
        ReceiptLineItem lineItem = new ReceiptLineItem("Chleb", SpendCategory.JEDZENIE_SREDNIE,
                new BigDecimal("5.00"), null);
        when(lineItemRepository.findByIdAndReceiptId(10L, 1L)).thenReturn(Optional.of(lineItem));
        when(lineItemRepository.sumAmountByReceiptId(1L)).thenReturn(new BigDecimal("7.50"));
        Receipt receipt = Receipt.newCameraUpload("2026/08/a.jpg", java.time.Instant.now());
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        var request = new LineItemCorrectionRequest(null, "JEDZENIE_PIERDOLOWATE", null, null);
        var result = service.correct(1L, 10L, request);

        assertThat(result.category()).isEqualTo(SpendCategory.JEDZENIE_PIERDOLOWATE);
        assertThat(result.productName()).isEqualTo("Chleb"); // unchanged — not provided
        assertThat(result.amount()).isEqualByComparingTo("5.00"); // unchanged — not provided
        assertThat(result.corrected()).isTrue();
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo("7.50");
    }

    @Test
    void invalidCategoryThrows422StyleException() {
        ReceiptLineItem lineItem = new ReceiptLineItem("Chleb", SpendCategory.JEDZENIE_SREDNIE,
                new BigDecimal("5.00"), null);
        when(lineItemRepository.findByIdAndReceiptId(10L, 1L)).thenReturn(Optional.of(lineItem));

        var request = new LineItemCorrectionRequest(null, "NOT_A_CATEGORY", null, null);

        assertThatThrownBy(() -> service.correct(1L, 10L, request))
                .isInstanceOf(InvalidLineItemException.class);
    }

    @Test
    void unknownLineItemThrowsNotFound() {
        when(lineItemRepository.findByIdAndReceiptId(99L, 1L)).thenReturn(Optional.empty());

        var request = new LineItemCorrectionRequest("x", null, null, null);

        assertThatThrownBy(() -> service.correct(1L, 99L, request))
                .isInstanceOf(NoSuchElementException.class);
    }
}
