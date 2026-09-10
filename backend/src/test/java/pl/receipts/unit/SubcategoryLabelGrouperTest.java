package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import pl.receipts.dto.receipt.CategorySubcategoryLabels;
import pl.receipts.dto.receipt.SubcategoryLabelGroup;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.projection.SubcategoryLabelRow;
import pl.receipts.service.SubcategoryLabelGrouper;

/**
 * Pure logic test (no Spring, no DB) for the normalize/rank/cap/group rules behind
 * GET /receipts/subcategory-labels — see docs/architecture/02-domain-model-and-schema.md
 * § Known Subcategory/Sub-Subcategory Labels and ADR-010 § Cross-batch label consistency. Mirrors
 * ReceiptRepositoryDataJpaTest#findStoreNameSuggestionsDedupsRanksAndCaps's scenario shape, one
 * level deeper.
 */
class SubcategoryLabelGrouperTest {

    @Test
    void dedupsSubcategoriesAndSubSubcategoriesWithMajorityCasingAndUsageRanking() {
        List<SubcategoryLabelRow> rows = List.of(
                // "Słodycze" group: 3 occurrences total, majority casing "Słodycze" (2x) beats
                // " słodycze " (1x). Its "żelki" sub-subcategory (2x) outranks "Batony" (1x).
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, "Słodycze", "żelki", "2026-01-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, "Słodycze", "żelki", "2026-01-02T10:00:00Z"),
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, " słodycze ", "Batony", "2026-01-03T10:00:00Z"),
                // "Chipsy" group: 2 occurrences — fewer than Słodycze's 3, ranks below it despite
                // being more recent (count is the primary key, recency only the tiebreak). Never
                // given a subSubcategory, so its list must be empty, not absent-but-erroring.
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, "Chipsy", null, "2026-02-01T10:00:00Z"),
                row(SpendCategory.JEDZENIE_PIERDOLOWATE, "Chipsy", "", "2026-02-02T10:00:00Z"),
                // A different category entirely — proves grouping is category-scoped ("Batony"
                // under ALKO must not merge with "Batony" under JEDZENIE_PIERDOLOWATE).
                row(SpendCategory.ALKO, "Piwo", "Piwo jasne", "2026-01-01T10:00:00Z"));

        List<CategorySubcategoryLabels> result = SubcategoryLabelGrouper.group(rows);

        // Only categories with data appear, ordered by SpendCategory's declaration order
        // (ALKO before JEDZENIE_PIERDOLOWATE), not insertion order.
        assertThat(result).extracting(CategorySubcategoryLabels::category)
                .containsExactly(SpendCategory.ALKO, SpendCategory.JEDZENIE_PIERDOLOWATE);

        CategorySubcategoryLabels food = result.stream()
                .filter(c -> c.category() == SpendCategory.JEDZENIE_PIERDOLOWATE).findFirst().orElseThrow();
        assertThat(food.subcategories()).extracting(SubcategoryLabelGroup::subcategory)
                .containsExactly("Słodycze", "Chipsy"); // Słodycze (3) ranks above Chipsy (2)

        SubcategoryLabelGroup slodycze = food.subcategories().get(0);
        assertThat(slodycze.subcategory()).isEqualTo("Słodycze"); // majority casing, not " słodycze "
        assertThat(slodycze.subSubcategories()).containsExactly("żelki", "Batony");

        SubcategoryLabelGroup chipsy = food.subcategories().get(1);
        assertThat(chipsy.subSubcategories()).isEmpty(); // null/blank subSubcategory never surfaces

        CategorySubcategoryLabels alko = result.stream()
                .filter(c -> c.category() == SpendCategory.ALKO).findFirst().orElseThrow();
        assertThat(alko.subcategories()).hasSize(1);
        assertThat(alko.subcategories().get(0).subSubcategories()).containsExactly("Piwo jasne");
    }

    @Test
    void tiesOnUsageCountAreBrokenByMostRecentCapturedAt() {
        List<SubcategoryLabelRow> rows = List.of(
                row(SpendCategory.SUPLE, "Witaminy", null, "2026-01-10T10:00:00Z"),
                row(SpendCategory.SUPLE, "Bialko", null, "2026-01-20T10:00:00Z")); // more recent, same count (1)

        List<CategorySubcategoryLabels> result = SubcategoryLabelGrouper.group(rows);

        assertThat(result.get(0).subcategories()).extracting(SubcategoryLabelGroup::subcategory)
                .containsExactly("Bialko", "Witaminy");
    }

    @Test
    void subSubcategoriesAreCappedAtThirty() {
        List<SubcategoryLabelRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 35; i++) {
            rows.add(row(SpendCategory.RZECZY_LUKSUSOWE, "Gadżety", "gadget-" + i,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i).toString()));
        }

        List<CategorySubcategoryLabels> result = SubcategoryLabelGrouper.group(rows);

        assertThat(result.get(0).subcategories().get(0).subSubcategories()).hasSize(30);
    }

    @Test
    void noCapOnDistinctSubcategoriesPerCategory() {
        List<SubcategoryLabelRow> rows = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            rows.add(row(SpendCategory.RZECZY_LUKSUSOWE, "sub-" + i, null,
                    Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i).toString()));
        }

        List<CategorySubcategoryLabels> result = SubcategoryLabelGrouper.group(rows);

        assertThat(result.get(0).subcategories()).hasSize(40);
    }

    @Test
    void emptyInputProducesEmptyResult() {
        assertThat(SubcategoryLabelGrouper.group(List.of())).isEmpty();
    }

    private static SubcategoryLabelRow row(SpendCategory category, String subcategory, String subSubcategory,
                                            String capturedAt) {
        return new Row(category, subcategory, subSubcategory, Instant.parse(capturedAt));
    }

    private record Row(SpendCategory category, String subcategory, String subSubcategory, Instant capturedAt)
            implements SubcategoryLabelRow {
        @Override
        public SpendCategory getCategory() {
            return category;
        }

        @Override
        public String getSubcategory() {
            return subcategory;
        }

        @Override
        public String getSubSubcategory() {
            return subSubcategory;
        }

        @Override
        public Instant getCapturedAt() {
            return capturedAt;
        }
    }
}
