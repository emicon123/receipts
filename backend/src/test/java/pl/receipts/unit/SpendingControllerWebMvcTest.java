package pl.receipts.unit;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.receipts.controller.SpendingController;
import pl.receipts.dto.spending.SpendingLineItem;
import pl.receipts.dto.spending.SpendingLineItemsResponse;
import pl.receipts.dto.spending.SpendingSummaryData;
import pl.receipts.dto.spending.SpendingSummaryResponse;
import pl.receipts.entity.SpendCategory;
import pl.receipts.service.SpendingService;

@WebMvcTest(SpendingController.class)
class SpendingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SpendingService spendingService;

    @Test
    void summaryReturns200() throws Exception {
        var data = new SpendingSummaryData(2026, 3, new BigDecimal("30.00"),
                List.of(new pl.receipts.dto.spending.CategoryAmount(SpendCategory.ALKO, new BigDecimal("30.00"))));
        given(spendingService.summary(2026, 3)).willReturn(new SpendingSummaryResponse(data));

        mockMvc.perform(get("/api/spending/summary").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(30.00));
    }

    @Test
    void summaryMissingMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/spending/summary").param("year", "2026"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trendYearOutOfRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/spending/trend").param("year", "1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lineItemsReturns200() throws Exception {
        var item = new SpendingLineItem(1L, "Żelki", SpendCategory.JEDZENIE_PIERDOLOWATE, new BigDecimal("4.50"),
                null, "Słodycze", "żelki", false, 7L, "Zabka",
                java.time.Instant.parse("2026-04-05T12:00:00Z"));
        given(spendingService.lineItems(2026, 4, SpendCategory.JEDZENIE_PIERDOLOWATE))
                .willReturn(new SpendingLineItemsResponse(List.of(item)));

        mockMvc.perform(get("/api/spending/line-items")
                        .param("year", "2026").param("month", "4").param("category", "JEDZENIE_PIERDOLOWATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productName").value("Żelki"))
                .andExpect(jsonPath("$.data[0].subcategory").value("Słodycze"))
                .andExpect(jsonPath("$.data[0].receiptId").value(7));
    }

    @Test
    void lineItemsMissingCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/spending/line-items").param("year", "2026").param("month", "4"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lineItemsInvalidCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/spending/line-items")
                        .param("year", "2026").param("month", "4").param("category", "NOT_REAL"))
                .andExpect(status().isBadRequest());
    }
}
