package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.receipts.dto.classification.ClassificationBatchItem;
import pl.receipts.dto.classification.ClassificationLineItemInput;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;
import pl.receipts.service.ClassificationApplierService;

/**
 * Pure-Mockito unit tests (no Spring context, no DB) for the "replace only uncorrected line
 * items" + category-validation business logic — CLAUDE.md's service-layer unit-test gate.
 * Idempotency-across-resubmission is covered at the integration level (real DB) instead, since
 * mocks can't reveal row-duplication bugs.
 */
@ExtendWith(MockitoExtension.class)
class ClassificationApplierServiceTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptLineItemRepository lineItemRepository;

    private ClassificationApplierService applier;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        applier = new ClassificationApplierService(receiptRepository, lineItemRepository);
    }

    @Test
    void appliesValidItemsRecomputesTotalAndTransitionsToProcessed() {
        Receipt receipt = Receipt.newCameraUpload("2026/08/a.jpg", Instant.now());
        receipt.setId(1L);
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));
        when(lineItemRepository.sumAmountByReceiptId(1L)).thenReturn(new BigDecimal("12.50"));

        var item = new ClassificationBatchItem(1L, "Biedronka", null, List.of(
                new ClassificationLineItemInput("Mleko", "JEDZENIE_KONIECZNE", new BigDecimal("4.50"), null),
                new ClassificationLineItemInput("Piwo", "ALKO", new BigDecimal("8.00"), null)));

        var result = applier.applyItem(item);

        assertThat(result.outcome()).isEqualTo(ClassificationApplierService.Outcome.PROCESSED);
        verify(lineItemRepository).deleteUncorrectedByReceiptId(1L);

        ArgumentCaptor<List<ReceiptLineItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(lineItemRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PROCESSED);
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo("12.50");
        assertThat(receipt.getStoreName()).isEqualTo("Biedronka");
        assertThat(receipt.getFailureReason()).isNull();
        assertThat(receipt.getProcessedAt()).isNotNull();
    }

    @Test
    void invalidCategoryRoutesWholeReceiptToFailedWithoutTouchingLineItems() {
        Receipt receipt = Receipt.newCameraUpload("2026/08/b.jpg", Instant.now());
        receipt.setId(2L);
        when(receiptRepository.findById(2L)).thenReturn(Optional.of(receipt));

        var item = new ClassificationBatchItem(2L, null, null, List.of(
                new ClassificationLineItemInput("Widget", "NOT_A_CATEGORY", BigDecimal.TEN, null)));

        var result = applier.applyItem(item);

        assertThat(result.outcome()).isEqualTo(ClassificationApplierService.Outcome.FAILED);
        assertThat(result.failureReason()).contains("NOT_A_CATEGORY");
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.FAILED);
        assertThat(receipt.getFailureReason()).isNotBlank();
        verify(lineItemRepository, never()).deleteUncorrectedByReceiptId(anyLong());
        verify(lineItemRepository, never()).saveAll(any());
    }

    @Test
    void negativeAmountRoutesWholeReceiptToFailed() {
        Receipt receipt = Receipt.newCameraUpload("2026/08/c.jpg", Instant.now());
        receipt.setId(3L);
        when(receiptRepository.findById(3L)).thenReturn(Optional.of(receipt));

        var item = new ClassificationBatchItem(3L, null, null, List.of(
                new ClassificationLineItemInput("Widget", "SUPLE", new BigDecimal("-1.00"), null)));

        var result = applier.applyItem(item);

        assertThat(result.outcome()).isEqualTo(ClassificationApplierService.Outcome.FAILED);
        verify(lineItemRepository, never()).saveAll(any());
    }

    @Test
    void zeroQuantityRoutesToFailed() {
        // Matches the DB CHECK constraint (quantity IS NULL OR quantity > 0) — see V1__init.sql.
        Receipt receipt = Receipt.newCameraUpload("2026/08/d.jpg", Instant.now());
        receipt.setId(4L);
        when(receiptRepository.findById(4L)).thenReturn(Optional.of(receipt));

        var item = new ClassificationBatchItem(4L, null, null, List.of(
                new ClassificationLineItemInput("Widget", "SUPLE", BigDecimal.TEN, BigDecimal.ZERO)));

        var result = applier.applyItem(item);

        assertThat(result.outcome()).isEqualTo(ClassificationApplierService.Outcome.FAILED);
    }

    @Test
    void nullLineItemsRoutesToFailed() {
        Receipt receipt = Receipt.newCameraUpload("2026/08/e.jpg", Instant.now());
        receipt.setId(5L);
        when(receiptRepository.findById(5L)).thenReturn(Optional.of(receipt));

        var item = new ClassificationBatchItem(5L, null, null, null);

        var result = applier.applyItem(item);

        assertThat(result.outcome()).isEqualTo(ClassificationApplierService.Outcome.FAILED);
        assertThat(result.failureReason()).contains("lineItems");
    }

    @Test
    void applyFailureSetsStatusAndReason() {
        Receipt receipt = Receipt.newCameraUpload("2026/08/f.jpg", Instant.now());
        receipt.setId(6L);
        when(receiptRepository.findById(6L)).thenReturn(Optional.of(receipt));

        applier.applyFailure(6L, "blurry photo");

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.FAILED);
        assertThat(receipt.getFailureReason()).isEqualTo("blurry photo");
        assertThat(receipt.getProcessedAt()).isNotNull();
    }
}
