package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import pl.receipts.dto.classification.ClassificationBatchFailure;
import pl.receipts.dto.classification.ClassificationBatchItem;
import pl.receipts.dto.classification.ClassificationBatchRequest;
import pl.receipts.dto.classification.ClassificationLineItemInput;
import pl.receipts.dto.receipt.ReceiptDetail;
import pl.receipts.dto.receipt.ReceiptSummary;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.exception.MalformedBatchRequestException;
import pl.receipts.service.ClassificationBatchService;
import pl.receipts.service.ReceiptService;

/**
 * The classification-batch tolerance/idempotency rules from
 * docs/architecture/05-api-contract.md, exercised against a real DB — this is the one endpoint
 * whose caller (the classifier, via the wrapper script) isn't fully trustworthy input.
 */
class ClassificationBatchIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private ClassificationBatchService classificationBatchService;

    @Test
    void unknownReceiptIdIsSkippedNotErrored() {
        var request = new ClassificationBatchRequest(
                List.of(new ClassificationBatchItem(999_999L, null, null,
                        List.of(new ClassificationLineItemInput("x", "SUPLE", BigDecimal.ONE, null)))),
                List.of());

        var result = classificationBatchService.submit(request);

        assertThat(result.processed()).isEmpty();
        assertThat(result.failed()).isEmpty();
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).receiptId()).isEqualTo(999_999L);
    }

    @Test
    void mixedBatchAppliesEachReceiptIndependently() throws Exception {
        Long good = upload();
        Long bad = upload();

        var request = new ClassificationBatchRequest(
                List.of(
                        new ClassificationBatchItem(good, null, null, List.of(
                                new ClassificationLineItemInput("Ok item", "SUPLE", BigDecimal.TEN, null))),
                        new ClassificationBatchItem(bad, null, null, List.of(
                                new ClassificationLineItemInput("Bad item", "NOT_REAL", BigDecimal.TEN, null)))),
                List.of());

        var result = classificationBatchService.submit(request);

        assertThat(result.processed()).containsExactly(good);
        assertThat(result.failed()).containsExactly(bad);

        assertThat(receiptService.getDetail(good).status()).isEqualTo(ReceiptStatus.PROCESSED);
        ReceiptDetail badDetail = receiptService.getDetail(bad);
        assertThat(badDetail.status()).isEqualTo(ReceiptStatus.FAILED);
        assertThat(badDetail.failureReason()).isNotBlank();
    }

    @Test
    void resubmittingTheSameBatchIsIdempotent() throws Exception {
        Long id = upload();
        var request = new ClassificationBatchRequest(
                List.of(new ClassificationBatchItem(id, "Sklep", null, List.of(
                        new ClassificationLineItemInput("A", "SUPLE", new BigDecimal("5.00"), null),
                        new ClassificationLineItemInput("B", "SUPLE", new BigDecimal("3.00"), null)))),
                List.of());

        classificationBatchService.submit(request);
        classificationBatchService.submit(request); // resubmit unchanged

        ReceiptDetail detail = receiptService.getDetail(id);
        assertThat(detail.lineItems()).hasSize(2); // not duplicated
        assertThat(detail.totalAmount()).isEqualByComparingTo("8.00");
    }

    @Test
    void failuresArrayMarksReceiptFailedWithReason() throws Exception {
        Long id = upload();
        var request = new ClassificationBatchRequest(List.of(),
                List.of(new ClassificationBatchFailure(id, "photo too blurry to read")));

        var result = classificationBatchService.submit(request);

        assertThat(result.failed()).containsExactly(id);
        ReceiptDetail detail = receiptService.getDetail(id);
        assertThat(detail.status()).isEqualTo(ReceiptStatus.FAILED);
        assertThat(detail.failureReason()).isEqualTo("photo too blurry to read");
    }

    @Test
    void malformedRequestMissingFailuresArrayIsRejected() {
        var request = new ClassificationBatchRequest(List.of(), null);

        assertThatThrownBy(() -> classificationBatchService.submit(request))
                .isInstanceOf(MalformedBatchRequestException.class);
    }

    private Long upload() throws Exception {
        var file = new MockMultipartFile("image", "r.jpg", "image/jpeg", "bytes".getBytes());
        ReceiptSummary summary = receiptService.uploadCameraReceipt(file, Instant.now());
        return summary.id();
    }
}
