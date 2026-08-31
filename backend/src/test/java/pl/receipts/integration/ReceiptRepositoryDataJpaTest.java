package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;

/**
 * @DataJpaTest slice against real PostgreSQL (Testcontainers) — validates the hand-written SQL in
 * ReceiptRepository/ReceiptLineItemRepository, in particular the "replace only uncorrected line
 * items" delete query, which an in-memory/H2 substitute wouldn't meaningfully exercise (the
 * Postgres native enum mapping alone would fail against H2's default dialect).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReceiptRepositoryDataJpaTest {

    // Shares the same JVM-wide singleton container as AbstractIntegrationTest's subclasses
    // rather than starting a second Postgres instance — see TestPostgresContainer's Javadoc.
    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", TestPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", TestPostgresContainer.INSTANCE::getPassword);
    }

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReceiptLineItemRepository lineItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void searchFiltersByStatusAndCapturedAtRange() {
        Receipt inRange = Receipt.newCameraUpload("2026/05/a.jpg", Instant.parse("2026-05-15T10:00:00Z"));
        Receipt outOfRange = Receipt.newCameraUpload("2026/06/a.jpg", Instant.parse("2026-06-15T10:00:00Z"));
        Receipt wrongStatus = Receipt.newCameraUpload("2026/05/b.jpg", Instant.parse("2026-05-16T10:00:00Z"));
        wrongStatus.setStatus(ReceiptStatus.PROCESSED);
        wrongStatus.setProcessedAt(Instant.now());
        entityManager.persist(inRange);
        entityManager.persist(outOfRange);
        entityManager.persist(wrongStatus);
        entityManager.flush();

        var page = receiptRepository.search(ReceiptStatus.PENDING.name(),
                Instant.parse("2026-05-01T00:00:00Z"), Instant.parse("2026-06-01T00:00:00Z"),
                PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Receipt::getId).containsExactly(inRange.getId());
    }

    @Test
    void searchWithNoFiltersReturnsEverything() {
        // Also exercises the bare ":status IS NULL" path itself (no status filter at all) —
        // this is the exact case that used to fail against real Postgres before the String-bind
        // fix (see ReceiptRepository.search's Javadoc).
        Receipt receipt = Receipt.newCameraUpload("2026/07/z.jpg", Instant.now());
        entityManager.persist(receipt);
        entityManager.flush();

        var page = receiptRepository.search(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Receipt::getId).contains(receipt.getId());
    }

    @Test
    void deleteUncorrectedByReceiptIdLeavesCorrectedRowsIntact() {
        Receipt receipt = Receipt.newCameraUpload("2026/05/c.jpg", Instant.now());
        entityManager.persist(receipt);

        ReceiptLineItem uncorrected = new ReceiptLineItem("A", SpendCategory.SUPLE, BigDecimal.ONE, null);
        uncorrected.setReceipt(receipt);
        ReceiptLineItem corrected = new ReceiptLineItem("B", SpendCategory.ALKO, BigDecimal.TEN, null);
        corrected.setReceipt(receipt);
        corrected.setCorrected(true);
        entityManager.persist(uncorrected);
        entityManager.persist(corrected);
        entityManager.flush();
        entityManager.clear();

        lineItemRepository.deleteUncorrectedByReceiptId(receipt.getId());
        entityManager.flush();
        entityManager.clear();

        var remaining = lineItemRepository.findByReceiptIdOrderByIdAsc(receipt.getId());
        assertThat(remaining).extracting(ReceiptLineItem::getProductName).containsExactly("B");
        assertThat(remaining.get(0).isCorrected()).isTrue();
    }

    @Test
    void sumAmountByReceiptIdReturnsZeroWhenNoLineItems() {
        Receipt receipt = Receipt.newCameraUpload("2026/05/d.jpg", Instant.now());
        entityManager.persist(receipt);
        entityManager.flush();

        BigDecimal sum = lineItemRepository.sumAmountByReceiptId(receipt.getId());

        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
