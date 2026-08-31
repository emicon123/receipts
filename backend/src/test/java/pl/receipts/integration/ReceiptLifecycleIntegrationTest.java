package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import pl.receipts.dto.classification.ClassificationBatchItem;
import pl.receipts.dto.classification.ClassificationBatchRequest;
import pl.receipts.dto.classification.ClassificationLineItemInput;
import pl.receipts.dto.receipt.LineItemCorrectionRequest;
import pl.receipts.dto.receipt.ReceiptDetail;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.entity.SpendCategory;
import pl.receipts.exception.ReceiptStateConflictException;
import pl.receipts.service.ClassificationBatchService;
import pl.receipts.service.LineItemCorrectionService;
import pl.receipts.service.ReceiptService;

/**
 * Full upload -> pending -> classify -> correct -> reprocess -> re-classify round trip against a
 * real PostgreSQL container, exercising the exact sequence in
 * docs/architecture/04-classification-flow.md. The heart of this test is CLAUDE.md's stickiest
 * rule: a user-corrected line item must survive a later classification-batch replace.
 */
class ReceiptLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private ClassificationBatchService classificationBatchService;

    @Autowired
    private LineItemCorrectionService lineItemCorrectionService;

    @Test
    void uploadThenClassifyThenCorrectThenReprocessPreservesCorrection() throws Exception {
        // 1. Camera upload -> PENDING
        var upload = new MockMultipartFile("image", "receipt.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());
        var summary = receiptService.uploadCameraReceipt(upload, Instant.parse("2026-08-15T10:00:00Z"));
        assertThat(summary.status()).isEqualTo(ReceiptStatus.PENDING);
        Long id = summary.id();

        // 2. Appears in the pending queue
        assertThat(receiptService.listPending().data()).extracting("id").contains(id);

        // 3. classification-batch classifies it -> PROCESSED, total computed
        var batchRequest = new ClassificationBatchRequest(
                List.of(new ClassificationBatchItem(id, "Biedronka", null, List.of(
                        new ClassificationLineItemInput("Mleko", "JEDZENIE_KONIECZNE", new BigDecimal("4.50"), null),
                        new ClassificationLineItemInput("Piwo", "ALKO", new BigDecimal("8.00"), null)))),
                List.of());
        var batchResult = classificationBatchService.submit(batchRequest);
        assertThat(batchResult.processed()).containsExactly(id);
        assertThat(batchResult.failed()).isEmpty();
        assertThat(batchResult.skipped()).isEmpty();

        ReceiptDetail detail = receiptService.getDetail(id);
        assertThat(detail.status()).isEqualTo(ReceiptStatus.PROCESSED);
        assertThat(detail.totalAmount()).isEqualByComparingTo("12.50");
        assertThat(detail.lineItems()).hasSize(2);

        // 4. No longer in the pending queue
        assertThat(receiptService.listPending().data()).extracting("id").doesNotContain(id);

        // 5. User corrects the "Piwo" line item's category
        Long piwoItemId = detail.lineItems().stream()
                .filter(li -> li.productName().equals("Piwo"))
                .findFirst().orElseThrow().id();
        var correction = new LineItemCorrectionRequest(null, "ROZRYWKA_RESTAURACJE", null, null);
        var corrected = lineItemCorrectionService.correct(id, piwoItemId, correction);
        assertThat(corrected.category()).isEqualTo(SpendCategory.ROZRYWKA_RESTAURACJE);
        assertThat(corrected.corrected()).isTrue();

        // Correction never moves receipt status.
        assertThat(receiptService.getDetail(id).status()).isEqualTo(ReceiptStatus.PROCESSED);

        // 6. Reprocessing a PROCESSED receipt without force is rejected (409-equivalent)
        assertThatThrownBy(() -> receiptService.reprocess(id, false))
                .isInstanceOf(ReceiptStateConflictException.class);

        // 7. Force-reprocess resets to PENDING
        var reprocessed = receiptService.reprocess(id, true);
        assertThat(reprocessed.status()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(receiptService.listPending().data()).extracting("id").contains(id);

        // 8. Re-classify with different (uncorrected) line items — the corrected "Piwo" row
        //    must survive untouched; only the uncorrected "Mleko" row is replaced.
        var secondBatch = new ClassificationBatchRequest(
                List.of(new ClassificationBatchItem(id, null, null, List.of(
                        new ClassificationLineItemInput("Chleb", "JEDZENIE_SREDNIE", new BigDecimal("3.00"), null)))),
                List.of());
        classificationBatchService.submit(secondBatch);

        ReceiptDetail afterReclassify = receiptService.getDetail(id);
        assertThat(afterReclassify.status()).isEqualTo(ReceiptStatus.PROCESSED);
        assertThat(afterReclassify.lineItems()).hasSize(2); // corrected "Piwo" + new "Chleb"
        assertThat(afterReclassify.lineItems()).anySatisfy(li -> {
            assertThat(li.productName()).isEqualTo("Piwo");
            assertThat(li.corrected()).isTrue();
            assertThat(li.category()).isEqualTo(SpendCategory.ROZRYWKA_RESTAURACJE);
        });
        assertThat(afterReclassify.lineItems()).anySatisfy(li -> {
            assertThat(li.productName()).isEqualTo("Chleb");
            assertThat(li.corrected()).isFalse();
        });
        assertThat(afterReclassify.totalAmount()).isEqualByComparingTo("11.00"); // 8.00 (Piwo) + 3.00 (Chleb)

        // 9. Delete removes the receipt entirely
        receiptService.delete(id);
        assertThatThrownBy(() -> receiptService.getDetail(id)).isInstanceOf(java.util.NoSuchElementException.class);
    }
}
