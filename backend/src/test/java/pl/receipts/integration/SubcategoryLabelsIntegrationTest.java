package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import pl.receipts.dto.classification.ClassificationBatchItem;
import pl.receipts.dto.classification.ClassificationBatchRequest;
import pl.receipts.dto.classification.ClassificationLineItemInput;
import pl.receipts.dto.receipt.CategorySubcategoryLabels;
import pl.receipts.dto.receipt.SubcategoryLabelGroup;
import pl.receipts.entity.SpendCategory;
import pl.receipts.service.ClassificationBatchService;
import pl.receipts.service.ReceiptService;

/**
 * GET /receipts/subcategory-labels (ADR-010 § Cross-batch label consistency), exercised end to
 * end against a real DB: classification-batch submission -> ReceiptLineItemRepository's flat
 * projection -> SubcategoryLabelGrouper's normalize/rank/cap/group logic (unit-tested in
 * isolation by SubcategoryLabelGrouperTest; this test only proves the wiring between the three
 * layers is correct, not the ranking rules themselves in exhaustive detail).
 *
 * <p>Assertions here deliberately use {@code contains}/{@code filter+findFirst} rather than
 * exhaustive {@code containsExactly}/{@code hasSize} on the top-level response: this class
 * extends {@link AbstractIntegrationTest} (a real, non-rolled-back {@code @SpringBootTest}
 * transaction, unlike {@code @DataJpaTest}'s per-test rollback), sharing one JVM-wide Postgres
 * container with every other {@code @SpringBootTest} class with no cleanup and no guaranteed
 * class execution order (see TestPostgresContainer's Javadoc) — and this endpoint, unlike
 * {@code /spending/summary}, has no year/month scope to isolate a test's fixture data behind, so
 * an "empty overall dataset" assertion would be flaky by construction. An empty-input result is
 * instead covered at the pure-logic level by SubcategoryLabelGrouperTest.
 */
class SubcategoryLabelsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private ClassificationBatchService classificationBatchService;

    @Test
    void returnsLabelsGroupedByCategoryAcrossReceiptsRegardlessOfStatus() throws Exception {
        Long processedId = upload();
        classificationBatchService.submit(new ClassificationBatchRequest(
                List.of(new ClassificationBatchItem(processedId, null, null, List.of(
                        new ClassificationLineItemInput("Żelki", "JEDZENIE_PIERDOLOWATE", new BigDecimal("4.50"),
                                null, "Słodycze", "żelki")))),
                List.of()));

        // A FAILED receipt's previously-recorded label (from an earlier successful batch, before
        // this specific receipt separately failed) must still count — labels are worth reminding
        // Claude of regardless of the owning receipt's current status. Simulate by classifying
        // one receipt with a label, then marking a second receipt FAILED (its own line items, if
        // any, stay whatever they were) — simplest faithful reproduction is a second receipt that
        // was classified successfully and then independently reprocessed to FAILED without ever
        // clearing its already-persisted line items (reprocess doesn't touch line items).
        Long secondId = upload();
        classificationBatchService.submit(new ClassificationBatchRequest(
                List.of(new ClassificationBatchItem(secondId, null, null, List.of(
                        new ClassificationLineItemInput("Piwo", "ALKO", new BigDecimal("8.00"), null,
                                "Piwo", "jasne")))),
                List.of()));
        receiptService.reprocess(secondId, true);
        classificationBatchService.submit(new ClassificationBatchRequest(List.of(),
                List.of(new pl.receipts.dto.classification.ClassificationBatchFailure(secondId, "blurry"))));

        var response = receiptService.listSubcategoryLabels();

        assertThat(response.data()).extracting(CategorySubcategoryLabels::category)
                .contains(SpendCategory.ALKO, SpendCategory.JEDZENIE_PIERDOLOWATE);

        CategorySubcategoryLabels food = response.data().stream()
                .filter(c -> c.category() == SpendCategory.JEDZENIE_PIERDOLOWATE).findFirst().orElseThrow();
        assertThat(food.subcategories()).extracting(SubcategoryLabelGroup::subcategory).contains("Słodycze");
        assertThat(food.subcategories().stream().filter(g -> g.subcategory().equals("Słodycze")).findFirst()
                .orElseThrow().subSubcategories()).containsExactly("żelki");
    }

    private Long upload() throws Exception {
        var file = new MockMultipartFile("image", "r.jpg", "image/jpeg", "bytes".getBytes());
        var summary = receiptService.uploadCameraReceipt(file, Instant.now());
        return summary.id();
    }
}
