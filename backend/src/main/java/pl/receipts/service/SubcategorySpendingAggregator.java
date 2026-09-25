package pl.receipts.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import pl.receipts.dto.spending.CategorySubcategoryBreakdown;
import pl.receipts.dto.spending.SubcategoryAmount;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.projection.SubcategoryVariantTotalRow;

/**
 * Pure merge logic behind GET /spending/subcategory-summary (ADR-013) — dependency-free so the
 * rules are unit-testable without Testcontainers, same rationale as
 * {@link SubcategoryLabelGrouper}. Takes per-exact-spelling sums and, per category (see
 * docs/architecture/02-domain-model-and-schema.md § Subcategory Spending Breakdown):
 *
 * <ol>
 *   <li>adds NULL/blank-subcategory rows to {@code unlabeledAmount};</li>
 *   <li>merges the rest by {@link LabelNormalization#normalize}, displaying the most-used
 *       spelling ({@link LabelNormalization#pickDisplayCasing});</li>
 *   <li>sorts groups by amount descending, then normalized key ascending;</li>
 *   <li>sets {@code totalAmount = unlabeledAmount + Σ group amounts} (exact, no rounding).</li>
 * </ol>
 *
 * Every category in {@code canonicalOrder} is returned, zero-filled when it has no rows.
 */
public final class SubcategorySpendingAggregator {

    private static final Comparator<KeyedAmount> AMOUNT_DESC_THEN_KEY = Comparator
            .comparing((KeyedAmount k) -> k.value().amount()).reversed()
            .thenComparing(KeyedAmount::key);

    private SubcategorySpendingAggregator() {
    }

    public static List<CategorySubcategoryBreakdown> aggregate(List<SubcategoryVariantTotalRow> rows,
                                                               List<SpendCategory> canonicalOrder) {
        Map<SpendCategory, List<SubcategoryVariantTotalRow>> byCategory = new EnumMap<>(SpendCategory.class);
        for (SubcategoryVariantTotalRow row : rows) {
            byCategory.computeIfAbsent(row.getCategory(), c -> new ArrayList<>()).add(row);
        }
        return canonicalOrder.stream()
                .map(category -> breakdown(category, byCategory.getOrDefault(category, List.of())))
                .toList();
    }

    private static CategorySubcategoryBreakdown breakdown(SpendCategory category,
                                                          List<SubcategoryVariantTotalRow> rows) {
        BigDecimal unlabeled = BigDecimal.ZERO;
        Map<String, List<SubcategoryVariantTotalRow>> byKey = new LinkedHashMap<>();
        for (SubcategoryVariantTotalRow row : rows) {
            String key = LabelNormalization.normalize(row.getSubcategory());
            if (key.isEmpty()) {
                unlabeled = unlabeled.add(row.getTotal());
            } else {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
            }
        }

        List<SubcategoryAmount> subcategories = byKey.entrySet().stream()
                .map(e -> new KeyedAmount(e.getKey(), mergeGroup(e.getValue())))
                .sorted(AMOUNT_DESC_THEN_KEY)
                .map(KeyedAmount::value)
                .toList();
        BigDecimal total = subcategories.stream()
                .map(SubcategoryAmount::amount)
                .reduce(unlabeled, BigDecimal::add);
        return new CategorySubcategoryBreakdown(category, total, unlabeled, subcategories);
    }

    private static SubcategoryAmount mergeGroup(List<SubcategoryVariantTotalRow> variants) {
        BigDecimal amount = variants.stream()
                .map(SubcategoryVariantTotalRow::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String display = LabelNormalization.pickDisplayCasing(variants.stream()
                .map(v -> new LabelNormalization.Variant(v.getSubcategory(), v.getItemCount(), v.getLastCapturedAt()))
                .toList());
        return new SubcategoryAmount(display, amount);
    }

    private record KeyedAmount(String key, SubcategoryAmount value) {
    }
}
