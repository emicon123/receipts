package pl.receipts.unit;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.receipts.controller.SpendingController;
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
}
