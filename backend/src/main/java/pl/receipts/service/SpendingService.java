package pl.receipts.service;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.receipts.dto.spending.CategoryAmount;
import pl.receipts.dto.spending.SpendingMonth;
import pl.receipts.dto.spending.SpendingSummaryData;
import pl.receipts.dto.spending.SpendingSummaryResponse;
import pl.receipts.dto.spending.SpendingTrendData;
import pl.receipts.dto.spending.SpendingTrendResponse;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.projection.CategoryTotalRow;
import pl.receipts.repository.projection.MonthCategoryTotalRow;

/**
 * GET /spending/summary and GET /spending/trend — computed at query time via SQL GROUP BY, never
 * stored/materialized (CLAUDE.md § Aggregation endpoints). Only PROCESSED receipts are included
 * (PENDING/FAILED don't have reliable line items). Both responses zero-fill every category
 * (summary) / every month (trend) in {@link CategoryCatalogService}'s canonical order, per the
 * Architect's resolution — see the task brief item 5.
 *
 * <p>Month/year boundaries are computed in UTC (matching {@code captured_at}'s TIMESTAMPTZ
 * storage) rather than a per-request local timezone, since this is a single-user app with no
 * per-user timezone concept — a receipt captured within a few hours of local midnight could in
 * principle land in the neighboring UTC month; flagged as a resolved judgment call, not
 * something the OpenAPI spec dictates either way.
 */
@Service
public class SpendingService {

    private final ReceiptLineItemRepository lineItemRepository;
    private final CategoryCatalogService categoryCatalog;

    public SpendingService(ReceiptLineItemRepository lineItemRepository, CategoryCatalogService categoryCatalog) {
        this.lineItemRepository = lineItemRepository;
        this.categoryCatalog = categoryCatalog;
    }

    @Transactional(readOnly = true)
    public SpendingSummaryResponse summary(int year, int month) {
        ZonedDateTime start = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        Instant from = start.toInstant();
        Instant to = start.plusMonths(1).toInstant();

        Map<SpendCategory, BigDecimal> totals = new EnumMap<>(SpendCategory.class);
        for (CategoryTotalRow row : lineItemRepository.sumByCategory(ReceiptStatus.PROCESSED, from, to)) {
            totals.put(row.getCategory(), row.getTotal());
        }

        List<CategoryAmount> categories = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (SpendCategory category : categoryCatalog.canonicalOrder()) {
            BigDecimal amount = totals.getOrDefault(category, BigDecimal.ZERO);
            categories.add(new CategoryAmount(category, amount));
            total = total.add(amount);
        }

        return new SpendingSummaryResponse(new SpendingSummaryData(year, month, total, categories));
    }

    @Transactional(readOnly = true)
    public SpendingTrendResponse trend(int year) {
        ZonedDateTime start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        Instant from = start.toInstant();
        Instant to = start.plusYears(1).toInstant();

        Map<Integer, Map<SpendCategory, BigDecimal>> totalsByMonth = new HashMap<>();
        for (MonthCategoryTotalRow row : lineItemRepository.sumByMonthAndCategory(ReceiptStatus.PROCESSED, from, to)) {
            int monthNumber = row.getMonth().intValue();
            totalsByMonth.computeIfAbsent(monthNumber, m -> new EnumMap<>(SpendCategory.class))
                    .put(row.getCategory(), row.getTotal());
        }

        List<SpendingMonth> months = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            Map<SpendCategory, BigDecimal> monthTotals = totalsByMonth.getOrDefault(month, Map.of());
            List<CategoryAmount> categories = new ArrayList<>();
            BigDecimal monthTotal = BigDecimal.ZERO;
            for (SpendCategory category : categoryCatalog.canonicalOrder()) {
                BigDecimal amount = monthTotals.getOrDefault(category, BigDecimal.ZERO);
                categories.add(new CategoryAmount(category, amount));
                monthTotal = monthTotal.add(amount);
            }
            months.add(new SpendingMonth(month, monthTotal, categories));
        }

        return new SpendingTrendResponse(new SpendingTrendData(year, months));
    }
}
