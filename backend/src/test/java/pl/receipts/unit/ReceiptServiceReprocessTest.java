package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.exception.InvalidQueryParamException;
import pl.receipts.exception.ReceiptStateConflictException;
import pl.receipts.mapper.ReceiptMapper;
import pl.receipts.mapper.ReceiptMapperImpl;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;
import pl.receipts.service.ReceiptService;
import pl.receipts.storage.ImageStorageService;

/**
 * Pure-Mockito tests for the reprocess status-transition guard clauses (docs/architecture/03) and
 * the "month requires year" list-query validation — CLAUDE.md's service-layer unit-test gate.
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceReprocessTest {

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptLineItemRepository lineItemRepository;

    @Mock
    private ImageStorageService imageStorageService;

    private ReceiptService service;

    @BeforeEach
    void setUp() {
        ReceiptMapper mapper = new ReceiptMapperImpl();
        service = new ReceiptService(receiptRepository, lineItemRepository, mapper, imageStorageService);
    }

    @Test
    void failedAlwaysReprocessesWithoutForce() {
        Receipt receipt = failedReceipt();
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        service.reprocess(1L, false);

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(receipt.getFailureReason()).isNull();
    }

    @Test
    void failedImageImportReceiptReprocessesLikeACameraOne() {
        // Reprocess is source-agnostic (ADR-014): an IMAGE_IMPORT receipt re-enters the pending queue
        // and keeps its (immutable) source.
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.IMAGE_IMPORT, "2026/09/a.png", Instant.now());
        receipt.setStatus(ReceiptStatus.FAILED);
        receipt.setFailureReason("not a purchase");
        receipt.setProcessedAt(Instant.now());
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        var summary = service.reprocess(1L, false);

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(receipt.getFailureReason()).isNull();
        assertThat(receipt.getProcessedAt()).isNull();
        assertThat(receipt.getSource()).isEqualTo(ReceiptSource.IMAGE_IMPORT);
        assertThat(summary.source()).isEqualTo(ReceiptSource.IMAGE_IMPORT);
    }

    @Test
    void pendingReprocessIsNoOp() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/08/a.jpg", Instant.now());
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        service.reprocess(1L, false);

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
    }

    @Test
    void processedRequiresForce() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/08/a.jpg", Instant.now());
        receipt.setStatus(ReceiptStatus.PROCESSED);
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.reprocess(1L, false))
                .isInstanceOf(ReceiptStateConflictException.class);
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PROCESSED); // unchanged
    }

    @Test
    void processedWithForceResets() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/08/a.jpg", Instant.now());
        receipt.setStatus(ReceiptStatus.PROCESSED);
        receipt.setProcessedAt(Instant.now());
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        service.reprocess(1L, true);

        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(receipt.getProcessedAt()).isNull();
    }

    @Test
    void stuckProcessingRequiresForce() {
        // Recovery path for a receipt left mid-batch by a backend crash — see
        // docs/architecture/03-receipt-lifecycle.md "Why PROCESSING exists".
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/08/a.jpg", Instant.now());
        receipt.setStatus(ReceiptStatus.PROCESSING);
        when(receiptRepository.findById(1L)).thenReturn(Optional.of(receipt));

        assertThatThrownBy(() -> service.reprocess(1L, false))
                .isInstanceOf(ReceiptStateConflictException.class);

        service.reprocess(1L, true);
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
    }

    @Test
    void monthWithoutYearIsRejected() {
        assertThatThrownBy(() -> service.list(null, 6, null, 0, 20))
                .isInstanceOf(InvalidQueryParamException.class);
    }

    private Receipt failedReceipt() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/08/a.jpg", Instant.now());
        receipt.setStatus(ReceiptStatus.FAILED);
        receipt.setFailureReason("blurry");
        receipt.setProcessedAt(Instant.now());
        return receipt;
    }
}
