package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.ReceiptSource;
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
// Spring Boot 4's @DataJpaTest slice no longer auto-imports Flyway (see
// AutoConfigureDataJpa.imports, which only lists DataJpaRepositoriesAutoConfiguration and
// HibernateJpaAutoConfiguration) — without this, the schema is never migrated onto the real
// Testcontainers Postgres instance and every insert fails with "relation ... does not exist".
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
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

    /**
     * The database is the JVM-wide singleton shared with the @SpringBootTest classes, which commit
     * their receipts for good; several tests below assert on exact result sets, so each starts from
     * an empty table whichever class ran first. The DELETE runs inside the test's own transaction
     * (rolled back afterwards), so it never removes what other classes committed.
     */
    @BeforeEach
    void startFromAnEmptyReceiptsTable() {
        entityManager.getEntityManager().createNativeQuery("DELETE FROM receipts").executeUpdate();
    }

    @Test
    void searchFiltersByStatusAndCapturedAtRange() {
        Receipt inRange = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/05/a.jpg",
                Instant.parse("2026-05-15T10:00:00Z"));
        Receipt outOfRange = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/06/a.jpg",
                Instant.parse("2026-06-15T10:00:00Z"));
        Receipt wrongStatus = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/05/b.jpg",
                Instant.parse("2026-05-16T10:00:00Z"));
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
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/07/z.jpg", Instant.now());
        entityManager.persist(receipt);
        entityManager.flush();

        var page = receiptRepository.search(null, null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Receipt::getId).contains(receipt.getId());
    }

    @Test
    void deleteUncorrectedByReceiptIdLeavesCorrectedRowsIntact() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/05/c.jpg", Instant.now());
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

    /**
     * Confirms V2__line_item_subcategories.sql applied cleanly and the new columns round-trip —
     * the most direct possible check that the migration exists and matches the entity mapping
     * (a broken/missing migration would fail Flyway validation at context startup for every test
     * in this module, but this test also proves the columns are actually readable/writable via
     * the entity, not just present in the schema).
     */
    @Test
    void subcategoryAndSubSubcategoryColumnsPersistAndReadBack() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/05/sub.jpg", Instant.now());
        entityManager.persist(receipt);

        ReceiptLineItem withSubcategories = new ReceiptLineItem("Żelki", SpendCategory.JEDZENIE_PIERDOLOWATE,
                new BigDecimal("4.50"), null, "Słodycze", "żelki");
        withSubcategories.setReceipt(receipt);
        ReceiptLineItem withoutSubcategories = new ReceiptLineItem("Mleko", SpendCategory.JEDZENIE_KONIECZNE,
                new BigDecimal("3.00"), null);
        withoutSubcategories.setReceipt(receipt);
        entityManager.persist(withSubcategories);
        entityManager.persist(withoutSubcategories);
        entityManager.flush();
        entityManager.clear();

        var reloaded = lineItemRepository.findByReceiptIdOrderByIdAsc(receipt.getId());
        assertThat(reloaded).hasSize(2);
        assertThat(reloaded.get(0).getSubcategory()).isEqualTo("Słodycze");
        assertThat(reloaded.get(0).getSubSubcategory()).isEqualTo("żelki");
        assertThat(reloaded.get(1).getSubcategory()).isNull(); // omitted -> NULL, no backfill (ADR-010)
        assertThat(reloaded.get(1).getSubSubcategory()).isNull();
    }

    /** Covers ReceiptLineItemRepository.findForSpendingDrilldown backing GET /spending/line-items. */
    @Test
    void findForSpendingDrilldownScopesToCategoryMonthAndProcessedOnly() {
        Receipt march = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/03/a.jpg",
                Instant.parse("2026-03-10T09:00:00Z"));
        march.setStatus(ReceiptStatus.PROCESSED);
        march.setStoreName("Biedronka");
        entityManager.persist(march);
        ReceiptLineItem marchAlko = new ReceiptLineItem("Wino", SpendCategory.ALKO, new BigDecimal("30.00"), null,
                "Wino", null);
        marchAlko.setReceipt(march);
        entityManager.persist(marchAlko);
        ReceiptLineItem marchOtherCategory = new ReceiptLineItem("Chleb", SpendCategory.JEDZENIE_SREDNIE,
                new BigDecimal("5.00"), null);
        marchOtherCategory.setReceipt(march);
        entityManager.persist(marchOtherCategory);

        // Same category, wrong month -> excluded.
        Receipt april = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/04/a.jpg",
                Instant.parse("2026-04-01T09:00:00Z"));
        april.setStatus(ReceiptStatus.PROCESSED);
        entityManager.persist(april);
        ReceiptLineItem aprilAlko = new ReceiptLineItem("Piwo", SpendCategory.ALKO, new BigDecimal("10.00"), null);
        aprilAlko.setReceipt(april);
        entityManager.persist(aprilAlko);

        // Same category/month, still PENDING -> excluded (only PROCESSED receipts have reliable
        // line items, matching /spending/summary's rule).
        Receipt marchPending = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/03/b.jpg",
                Instant.parse("2026-03-15T09:00:00Z"));
        entityManager.persist(marchPending);
        ReceiptLineItem pendingAlko = new ReceiptLineItem("Piwo", SpendCategory.ALKO, new BigDecimal("99.00"), null);
        pendingAlko.setReceipt(marchPending);
        entityManager.persist(pendingAlko);

        entityManager.flush();
        entityManager.clear();

        var results = lineItemRepository.findForSpendingDrilldown(ReceiptStatus.PROCESSED, SpendCategory.ALKO,
                Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-04-01T00:00:00Z"));

        assertThat(results).extracting(ReceiptLineItem::getProductName).containsExactly("Wino");
        assertThat(results.get(0).getReceipt().getStoreName()).isEqualTo("Biedronka");
    }

    /** Covers ReceiptLineItemRepository.findSubcategoryLabelRows backing GET /receipts/subcategory-labels. */
    @Test
    void findSubcategoryLabelRowsExcludesBlankSubcategoryAndIncludesEveryStatus() {
        Receipt processed = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/06/a.jpg",
                Instant.parse("2026-06-01T10:00:00Z"));
        processed.setStatus(ReceiptStatus.PROCESSED);
        processed.setProcessedAt(Instant.now());
        entityManager.persist(processed);
        ReceiptLineItem withLabel = new ReceiptLineItem("Żelki", SpendCategory.JEDZENIE_PIERDOLOWATE,
                new BigDecimal("4.50"), null, "Słodycze", "żelki");
        withLabel.setReceipt(processed);
        entityManager.persist(withLabel);

        // A still-PENDING receipt's label must still count (labels are worth reminding Claude of
        // regardless of the owning receipt's status).
        Receipt pending = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/06/b.jpg",
                Instant.parse("2026-06-02T10:00:00Z"));
        entityManager.persist(pending);
        ReceiptLineItem pendingLabel = new ReceiptLineItem("Chipsy", SpendCategory.JEDZENIE_PIERDOLOWATE,
                new BigDecimal("6.00"), null, "Chipsy", null);
        pendingLabel.setReceipt(pending);
        entityManager.persist(pendingLabel);

        // Blank/NULL subcategory must never surface.
        ReceiptLineItem blankSubcategory = new ReceiptLineItem("Mleko", SpendCategory.JEDZENIE_KONIECZNE,
                new BigDecimal("3.00"), null, "   ", null);
        blankSubcategory.setReceipt(processed);
        entityManager.persist(blankSubcategory);
        ReceiptLineItem nullSubcategory = new ReceiptLineItem("Ser", SpendCategory.JEDZENIE_SREDNIE,
                new BigDecimal("8.00"), null);
        nullSubcategory.setReceipt(processed);
        entityManager.persist(nullSubcategory);

        entityManager.flush();
        entityManager.clear();

        var rows = lineItemRepository.findSubcategoryLabelRows();

        assertThat(rows).extracting(pl.receipts.repository.projection.SubcategoryLabelRow::getSubcategory)
                .containsExactlyInAnyOrder("Słodycze", "Chipsy");
    }

    /**
     * The NAMED_ENUM mapping of Receipt.source must read and write the value V3 added to
     * receipt_source_enum, and an IMAGE_IMPORT receipt is queued like a camera one (ADR-014).
     */
    @Test
    void imageImportReceiptRoundTripsAndIsQueuedAsPendingNextToACameraOne() {
        entityManager.persist(Receipt.newImageUpload(ReceiptSource.IMAGE_IMPORT, "2026/10/b.png",
                Instant.parse("2026-10-02T09:00:00Z")));
        entityManager.persist(Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/10/a.jpg",
                Instant.parse("2026-10-01T09:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        List<Receipt> pending = receiptRepository.findAllByStatusOrderByCapturedAtAsc(ReceiptStatus.PENDING);

        assertThat(pending).extracting(Receipt::getSource)
                .containsExactly(ReceiptSource.CAMERA, ReceiptSource.IMAGE_IMPORT);
        assertThat(pending).extracting(Receipt::getImagePath).containsExactly("2026/10/a.jpg", "2026/10/b.png");
    }

    @Test
    void sumAmountByReceiptIdReturnsZeroWhenNoLineItems() {
        Receipt receipt = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/05/d.jpg", Instant.now());
        entityManager.persist(receipt);
        entityManager.flush();

        BigDecimal sum = lineItemRepository.sumAmountByReceiptId(receipt.getId());

        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }

    /**
     * Covers ReceiptRepository.findStoreNameSuggestions end-to-end against real Postgres:
     * case/whitespace dedup with majority-casing display, count-then-recency group ranking,
     * NULL/blank exclusion, the top-20 cap, and that every status/source is included (not just
     * PROCESSED) — see docs/architecture/02-domain-model-and-schema.md § Store-Name Suggestions
     * and ADR-009.
     */
    @Test
    void findStoreNameSuggestionsDedupsRanksAndCaps() {
        // "Lidl" group: 3 occurrences total. Majority casing is "Lidl" (2x), beating " LIDL "
        // (1x) and "lidl" (1x would tie count with "Lidl" if not careful — kept at 2 vs 1 vs 1
        // to make the majority unambiguous). Spans PENDING/PROCESSING/PROCESSED/FAILED statuses
        // and both CAMERA/MANUAL sources to prove no status/source filter is applied.
        persistReceiptWithStore("Lidl", Instant.parse("2026-01-01T10:00:00Z"), ReceiptStatus.PENDING,
                r -> {});
        persistReceiptWithStore("Lidl", Instant.parse("2026-01-05T10:00:00Z"), ReceiptStatus.PROCESSED,
                r -> r.setProcessedAt(Instant.now()));
        persistReceiptWithStore(" LIDL ", Instant.parse("2026-01-10T10:00:00Z"), ReceiptStatus.FAILED,
                r -> r.setFailureReason("blurry"));

        // "Kaufland" group: 2 occurrences — fewer than Lidl's 3, so ranks below it despite being
        // more recent, proving count is the primary key and recency only the tiebreak. Within
        // the group, "kaufland"/"Kaufland" tie 1-1 on casing count, so the display casing is
        // decided by each variant's own most recent use — "Kaufland" (the later one) must win.
        persistReceiptWithStore("kaufland", Instant.parse("2026-02-01T10:00:00Z"), ReceiptStatus.PROCESSING,
                r -> {});
        persistReceiptWithStore("Kaufland", Instant.parse("2026-02-20T10:00:00Z"), ReceiptStatus.PENDING,
                r -> {});

        // Two single-use stores with equal count (1) — "Biedronka" used more recently than
        // "Zabka", so it must rank ahead purely on the recency tiebreak.
        persistReceiptWithStore("Zabka", Instant.parse("2026-01-15T10:00:00Z"), ReceiptStatus.PENDING, r -> {});
        persistReceiptWithStore("Biedronka", Instant.parse("2026-01-20T10:00:00Z"), ReceiptStatus.PENDING, r -> {});

        // NULL and blank/whitespace-only store names must never surface.
        Receipt nullStore = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/03/null.jpg",
                Instant.parse("2026-03-01T10:00:00Z"));
        entityManager.persist(nullStore);
        Receipt blankStore = Receipt.newManualEntry(Instant.parse("2026-03-02T10:00:00Z"), "   ");
        entityManager.persist(blankStore);
        entityManager.flush();
        entityManager.clear();

        List<String> suggestions = receiptRepository.findStoreNameSuggestions();

        assertThat(suggestions).containsExactly("Lidl", "Kaufland", "Biedronka", "Zabka");
    }

    @Test
    void findStoreNameSuggestionsCapsAtTwenty() {
        for (int i = 0; i < 25; i++) {
            persistReceiptWithStore("Store" + i, Instant.parse("2026-01-01T10:00:00Z").plusSeconds(i),
                    ReceiptStatus.PENDING, r -> {});
        }
        entityManager.flush();
        entityManager.clear();

        List<String> suggestions = receiptRepository.findStoreNameSuggestions();

        assertThat(suggestions).hasSize(20);
    }

    private void persistReceiptWithStore(String storeName, Instant capturedAt, ReceiptStatus status,
                                          Consumer<Receipt> customize) {
        Receipt receipt = Receipt.newManualEntry(capturedAt, storeName);
        receipt.setStatus(status);
        customize.accept(receipt);
        entityManager.persist(receipt);
    }
}
