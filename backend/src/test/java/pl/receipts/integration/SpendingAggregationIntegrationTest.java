package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pl.receipts.dto.receipt.LineItemInput;
import pl.receipts.dto.receipt.ManualReceiptRequest;
import pl.receipts.dto.spending.SpendingSummaryResponse;
import pl.receipts.dto.spending.SpendingTrendResponse;
import pl.receipts.entity.SpendCategory;
import pl.receipts.service.ReceiptService;
import pl.receipts.service.SpendingService;

/**
 * GET /spending/summary and /trend: zero-filled across all 11 categories / 12 months (Architect's
 * resolution — task brief item 5), computed live over PROCESSED receipts only.
 */
class SpendingAggregationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private SpendingService spendingService;

    @Test
    void summaryZeroFillsAllElevenCategoriesAndSumsOnlyProcessedReceipts() {
        receiptService.createManualReceipt(new ManualReceiptRequest(
                Instant.parse("2026-03-10T12:00:00Z"), "Sklep",
                List.of(new LineItemInput("Wino", "ALKO", new BigDecimal("30.00"), null))));

        SpendingSummaryResponse response = spendingService.summary(2026, 3);

        assertThat(response.data().categories()).hasSize(11); // every fixed category present
        assertThat(response.data().totalAmount()).isEqualByComparingTo("30.00");
        assertThat(response.data().categories()).anySatisfy(c -> {
            assertThat(c.category()).isEqualTo(SpendCategory.ALKO);
            assertThat(c.amount()).isEqualByComparingTo("30.00");
        });
        assertThat(response.data().categories()).filteredOn(c -> c.category() != SpendCategory.ALKO)
                .allSatisfy(c -> assertThat(c.amount()).isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void summaryForAMonthWithNoReceiptsIsAllZero() {
        SpendingSummaryResponse response = spendingService.summary(2019, 1);

        assertThat(response.data().categories()).hasSize(11);
        assertThat(response.data().totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.data().categories()).allSatisfy(c -> assertThat(c.amount())
                .isEqualByComparingTo(BigDecimal.ZERO));
    }

    @Test
    void trendZeroFillsAllTwelveMonths() {
        receiptService.createManualReceipt(new ManualReceiptRequest(
                Instant.parse("2027-06-01T12:00:00Z"), null,
                List.of(new LineItemInput("Rachunek za prad", "RACHUNKI", new BigDecimal("120.00"), null))));

        SpendingTrendResponse response = spendingService.trend(2027);

        assertThat(response.data().months()).hasSize(12);
        assertThat(response.data().months().get(5).month()).isEqualTo(6);
        assertThat(response.data().months().get(5).totalAmount()).isEqualByComparingTo("120.00");
        assertThat(response.data().months()).filteredOn(m -> m.month() != 6)
                .allSatisfy(m -> assertThat(m.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO));
    }
}
