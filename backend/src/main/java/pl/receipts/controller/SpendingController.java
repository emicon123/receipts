package pl.receipts.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.receipts.dto.spending.SpendingSummaryResponse;
import pl.receipts.dto.spending.SpendingTrendResponse;
import pl.receipts.service.SpendingService;

@RestController
@RequestMapping("/api/spending")
@Validated
public class SpendingController {

    private final SpendingService spendingService;

    public SpendingController(SpendingService spendingService) {
        this.spendingService = spendingService;
    }

    @GetMapping("/summary")
    public SpendingSummaryResponse summary(@RequestParam @Min(2000) @Max(2100) int year,
                                            @RequestParam @Min(1) @Max(12) int month) {
        return spendingService.summary(year, month);
    }

    @GetMapping("/trend")
    public SpendingTrendResponse trend(@RequestParam @Min(2000) @Max(2100) int year) {
        return spendingService.trend(year);
    }
}
