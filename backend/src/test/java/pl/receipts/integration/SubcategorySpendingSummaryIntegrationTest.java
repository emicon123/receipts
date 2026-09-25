package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pl.receipts.dto.receipt.LineItemInput;
import pl.receipts.dto.receipt.ManualReceiptRequest;
import pl.receipts.dto.spending.CategoryAmount;
import pl.receipts.dto.spending.CategorySubcategoryBreakdown;
import pl.receipts.dto.spending.SpendingSubcategorySummaryData;
import pl.receipts.dto.spending.SpendingSummaryData;
import pl.receipts.dto.spending.SubcategoryAmount;
import pl.receipts.entity.SpendCategory;
import pl.receipts.service.CategoryCatalogService;
import pl.receipts.service.ReceiptService;
import pl.receipts.service.SpendingService;

/**
 * GET /spending/subcategory-summary (ADR-013) against real PostgreSQL: the SQL aggregate
 * ({@code sumBySubcategoryVariant}) plus the Java normalization, month scoping, PROCESSED-only
 * filter, zero-fill, and the reconciliation invariant with GET /spending/summary.
 *
 * <p>The Testcontainers database is shared across test classes and not cleaned between tests,
 * so every test here owns its own 2028 month (no other test class writes 2028 data).
 */
@AutoConfigureMockMvc
class SubcategorySpendingSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final String PIERDOL = "JEDZENIE_PIERDOLOWATE";
    private static final String KONIECZNE = "JEDZENIE_KONIECZNE";

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private SpendingService spendingService;

    @Autowired
    private CategoryCatalogService categoryCatalog;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sumsPerSubcategoryNormalizesLabelsAndBucketsUnlabeledSpend() {
        manual("2028-01-10T12:00:00Z",
                item("Żelki", PIERDOL, "4.50", "Słodycze"),
                item("Baton", PIERDOL, "3.20", " słodycze "),
                item("Chipsy paprykowe", PIERDOL, "6.99", "Chipsy"),
                item("Cola", PIERDOL, "5.00", null),
                item("Mleko", KONIECZNE, "3.49", "Nabiał"));
        manual("2028-01-20T12:00:00Z",
                item("Czekolada", PIERDOL, "7.10", "Słodycze"),
                item("Oranżada", PIERDOL, "2.00", "   "),
                item("Jogurt", KONIECZNE, "2.10", "nabiał"));

        SpendingSubcategorySummaryData data = spendingService.subcategorySummary(2028, 1).data();

        CategorySubcategoryBreakdown food = find(data, SpendCategory.JEDZENIE_PIERDOLOWATE);
        // "Słodycze" x2 + " słodycze " x1 merge into one group displayed with the majority spelling.
        assertThat(food.subcategories()).extracting(SubcategoryAmount::subcategory)
                .containsExactly("Słodycze", "Chipsy"); // 14.80 > 6.99
        assertThat(food.subcategories().get(0).amount()).isEqualByComparingTo("14.80");
        assertThat(food.subcategories().get(1).amount()).isEqualByComparingTo("6.99");
        // NULL and whitespace-only subcategories -> unlabeledAmount, never a list entry.
        assertThat(food.unlabeledAmount()).isEqualByComparingTo("7.00");
        assertThat(food.totalAmount()).isEqualByComparingTo("28.79");

        CategorySubcategoryBreakdown healthy = find(data, SpendCategory.JEDZENIE_KONIECZNE);
        // 1 vs 1 item -> the more recent spelling wins.
        assertThat(healthy.subcategories()).containsExactly(new SubcategoryAmount("nabiał", new BigDecimal("5.59")));
        assertThat(healthy.unlabeledAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(data.totalAmount()).isEqualByComparingTo("34.38");
    }

    @Test
    void scopesToTheUtcMonthAndExcludesNonProcessedReceipts() {
        manual("2028-10-01T00:00:00Z", item("Piwo", "ALKO", "10.00", "Piwo"));          // first instant: in
        manual("2028-10-31T23:59:59Z", item("Wino", "ALKO", "20.00", "Wino"));          // last second: in
        manual("2028-09-30T23:59:59Z", item("Whisky", "ALKO", "99.00", "Mocne"));       // previous month
        manual("2028-11-01T00:00:00Z", item("Wódka", "ALKO", "88.00", "Mocne"));        // next month
        // PROCESSED manual receipt force-reprocessed back to PENDING: keeps its line items, but
        // must no longer count (only PROCESSED receipts have reliable line items).
        Long pendingId = manual("2028-10-15T12:00:00Z", item("Nalewka", "ALKO", "77.00", "Mocne"));
        receiptService.reprocess(pendingId, true);

        CategorySubcategoryBreakdown alko = find(spendingService.subcategorySummary(2028, 10).data(), SpendCategory.ALKO);

        assertThat(alko.subcategories()).extracting(SubcategoryAmount::subcategory).containsExactly("Wino", "Piwo");
        assertThat(alko.totalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void perCategoryTotalsEqualSpendingSummary() {
        manual("2028-04-03T08:00:00Z",
                item("Chleb", "JEDZENIE_SREDNIE", "4.19", "Pieczywo"),
                item("Ser", "JEDZENIE_SREDNIE", "12.34", null),
                item("Płyn do naczyń", "MYCIE_CHEMIA", "8.99", "Naczynia"),
                item("Pieluchy", "BOBINEK", "59.90", "Pieluchy"));
        manual("2028-04-18T19:30:00Z",
                item("Kolacja", "ROZRYWKA_RESTAURACJE", "143.50", "Restauracje"),
                item("Bułki", "JEDZENIE_SREDNIE", "2.01", "pieczywo "),
                item("Chusteczki", "BOBINEK", "7.77", ""));
        Long pendingId = manual("2028-04-20T10:00:00Z", item("Paliwo", "RZECZY_PALIWO_INNE_ROZNE", "250.00", "Paliwo"));
        receiptService.reprocess(pendingId, true);

        SpendingSummaryData summary = spendingService.summary(2028, 4).data();
        SpendingSubcategorySummaryData breakdown = spendingService.subcategorySummary(2028, 4).data();

        assertThat(breakdown.categories()).hasSize(11);
        for (int i = 0; i < 11; i++) {
            CategoryAmount expected = summary.categories().get(i);
            CategorySubcategoryBreakdown actual = breakdown.categories().get(i);
            assertThat(actual.category()).isEqualTo(expected.category()); // zips index-for-index
            assertThat(actual.totalAmount()).as(expected.category().name()).isEqualTo(expected.amount());
            BigDecimal parts = actual.subcategories().stream().map(SubcategoryAmount::amount)
                    .reduce(actual.unlabeledAmount(), BigDecimal::add);
            assertThat(parts).as(expected.category().name()).isEqualByComparingTo(actual.totalAmount());
        }
        assertThat(breakdown.totalAmount()).isEqualByComparingTo(summary.totalAmount());
        assertThat(breakdown.totalAmount()).isEqualByComparingTo("238.70");
    }

    @Test
    void monthWithoutSpendIsAllElevenCategoriesZeroFilledInCanonicalOrder() {
        SpendingSubcategorySummaryData data = spendingService.subcategorySummary(2028, 6).data();

        assertThat(data.year()).isEqualTo(2028);
        assertThat(data.month()).isEqualTo(6);
        assertThat(data.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(data.categories()).extracting(CategorySubcategoryBreakdown::category)
                .containsExactlyElementsOf(categoryCatalog.canonicalOrder());
        assertThat(data.categories()).allSatisfy(c -> {
            assertThat(c.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(c.unlabeledAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(c.subcategories()).isEmpty();
        });
    }

    @Test
    void httpEndpointServesTheBreakdownAndRejectsAnInvalidMonth() throws Exception {
        manual("2028-08-08T08:00:00Z", item("Witamina D", "SUPLE", "19.99", "Witaminy"));

        mockMvc.perform(get("/api/spending/subcategory-summary").param("year", "2028").param("month", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories.length()").value(11))
                .andExpect(jsonPath("$.data.categories[10].category").value("SUPLE"))
                .andExpect(jsonPath("$.data.categories[10].subcategories[0].subcategory").value("Witaminy"))
                .andExpect(jsonPath("$.data.categories[10].subcategories[0].amount").value(19.99))
                .andExpect(jsonPath("$.data.categories[10].unlabeledAmount").value(0))
                .andExpect(jsonPath("$.meta.requestId").exists());

        mockMvc.perform(get("/api/spending/subcategory-summary").param("year", "2028").param("month", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    private Long manual(String capturedAt, LineItemInput... items) {
        return receiptService.createManualReceipt(
                new ManualReceiptRequest(Instant.parse(capturedAt), "Sklep", List.of(items))).id();
    }

    private static LineItemInput item(String name, String category, String amount, String subcategory) {
        return new LineItemInput(name, category, new BigDecimal(amount), null, subcategory, null);
    }

    private static CategorySubcategoryBreakdown find(SpendingSubcategorySummaryData data, SpendCategory category) {
        return data.categories().stream().filter(c -> c.category() == category).findFirst().orElseThrow();
    }
}
