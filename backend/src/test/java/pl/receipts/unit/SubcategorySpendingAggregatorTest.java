package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.receipts.dto.spending.CategorySubcategoryBreakdown;
import pl.receipts.dto.spending.SubcategoryAmount;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.projection.SubcategoryVariantTotalRow;
import pl.receipts.service.SubcategorySpendingAggregator;

/**
 * Pure logic test (no Spring, no DB) for GET /spending/subcategory-summary's merge rules —
 * docs/architecture/02-domain-model-and-schema.md § Subcategory Spending Breakdown, ADR-013.
 */
class SubcategorySpendingAggregatorTest {

    private static final List<SpendCategory> CANONICAL = Arrays.asList(SpendCategory.values());

    @Test
    void mergesSpellingVariantsByLowerTrimAndPicksMostUsedSpelling() {
        List<CategorySubcategoryBreakdown> result = SubcategorySpendingAggregator.aggregate(List.of(
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, "Słodycze", "10.00", 2, "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, " słodycze ", "5.50", 1, "2026-01-20T10:00:00Z")),
                CANONICAL);

        CategorySubcategoryBreakdown food = find(result, SpendCategory.JEDZENIE_PIERDOLOWATE);
        assertThat(food.subcategories()).hasSize(1);
        assertThat(food.subcategories().get(0).subcategory()).isEqualTo("Słodycze"); // 2 items beat 1
        assertThat(food.subcategories().get(0).amount()).isEqualByComparingTo("15.50");
        assertThat(food.totalAmount()).isEqualByComparingTo("15.50");
    }

    @Test
    void displaySpellingTieBrokenByRecencyThenAlphabetically() {
        List<CategorySubcategoryBreakdown> result = SubcategorySpendingAggregator.aggregate(List.of(
                // same count -> the more recent spelling wins, even though it sorts later
                row(SpendCategory.JEDZENIE_KONIECZNE, "Nabiał", "1.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_KONIECZNE, "nabiał", "1.00", 1, "2026-01-05T10:00:00Z"),
                // same count and recency -> alphabetical ("Owoce" < "owoce")
                row(SpendCategory.JEDZENIE_KONIECZNE, "owoce", "1.00", 1, "2026-01-05T10:00:00Z"),
                row(SpendCategory.JEDZENIE_KONIECZNE, "Owoce", "1.00", 1, "2026-01-05T10:00:00Z")),
                CANONICAL);

        assertThat(find(result, SpendCategory.JEDZENIE_KONIECZNE).subcategories())
                .extracting(SubcategoryAmount::subcategory)
                .containsExactly("nabiał", "Owoce"); // equal amounts (2.00) -> normalized key asc
    }

    @Test
    void nullAndBlankSubcategoriesGoToUnlabeledAmountNotToTheList() {
        List<CategorySubcategoryBreakdown> result = SubcategorySpendingAggregator.aggregate(List.of(
                row(SpendCategory.SUPLE, null, "20.00", 3, "2026-01-01T10:00:00Z"),
                row(SpendCategory.SUPLE, "", "1.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.SUPLE, "   ", "2.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.SUPLE, "Witaminy", "7.00", 1, "2026-01-01T10:00:00Z")),
                CANONICAL);

        CategorySubcategoryBreakdown suple = find(result, SpendCategory.SUPLE);
        assertThat(suple.unlabeledAmount()).isEqualByComparingTo("23.00");
        assertThat(suple.subcategories()).extracting(SubcategoryAmount::subcategory).containsExactly("Witaminy");
        assertThat(suple.totalAmount()).isEqualByComparingTo("30.00");
    }

    @Test
    void subcategoriesSortedByAmountDescThenNormalizedKeyAsc() {
        List<CategorySubcategoryBreakdown> result = SubcategorySpendingAggregator.aggregate(List.of(
                row(SpendCategory.JEDZENIE_SREDNIE, "Pieczywo", "5.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_SREDNIE, "Makaron", "12.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_SREDNIE, "sosy", "5.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_SREDNIE, "Mrożonki", "5.00", 1, "2026-01-01T10:00:00Z")),
                CANONICAL);

        assertThat(find(result, SpendCategory.JEDZENIE_SREDNIE).subcategories())
                .extracting(SubcategoryAmount::subcategory)
                .containsExactly("Makaron", "Mrożonki", "Pieczywo", "sosy");
    }

    @Test
    void sameLabelUnderDifferentCategoriesStaysSeparate() {
        List<CategorySubcategoryBreakdown> result = SubcategorySpendingAggregator.aggregate(List.of(
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, "Napoje", "8.00", 1, "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_SREDNIE, "Napoje", "3.00", 1, "2026-01-01T10:00:00Z")),
                CANONICAL);

        assertThat(find(result, SpendCategory.JEDZENIE_PIERDOLOWATE).totalAmount()).isEqualByComparingTo("8.00");
        assertThat(find(result, SpendCategory.JEDZENIE_SREDNIE).totalAmount()).isEqualByComparingTo("3.00");
    }

    @Test
    void everyCategoryIsReturnedZeroFilledInTheGivenOrder() {
        List<CategorySubcategoryBreakdown> result = SubcategorySpendingAggregator.aggregate(List.of(), CANONICAL);

        assertThat(result).extracting(CategorySubcategoryBreakdown::category).containsExactlyElementsOf(CANONICAL);
        assertThat(result).allSatisfy(c -> {
            assertThat(c.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(c.unlabeledAmount()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(c.subcategories()).isEmpty();
        });
    }

    private static CategorySubcategoryBreakdown find(List<CategorySubcategoryBreakdown> result, SpendCategory c) {
        return result.stream().filter(b -> b.category() == c).findFirst().orElseThrow();
    }

    private static SubcategoryVariantTotalRow row(SpendCategory category, String subcategory, String total,
                                                  long itemCount, String lastCapturedAt) {
        return new Row(category, subcategory, new BigDecimal(total), itemCount, Instant.parse(lastCapturedAt));
    }

    private record Row(SpendCategory category, String subcategory, BigDecimal total, long itemCount,
                       Instant lastCapturedAt) implements SubcategoryVariantTotalRow {
        @Override
        public SpendCategory getCategory() {
            return category;
        }

        @Override
        public String getSubcategory() {
            return subcategory;
        }

        @Override
        public BigDecimal getTotal() {
            return total;
        }

        @Override
        public long getItemCount() {
            return itemCount;
        }

        @Override
        public Instant getLastCapturedAt() {
            return lastCapturedAt;
        }
    }
}
