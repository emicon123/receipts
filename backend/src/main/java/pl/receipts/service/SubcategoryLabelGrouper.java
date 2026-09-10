package pl.receipts.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import pl.receipts.dto.receipt.CategorySubcategoryLabels;
import pl.receipts.dto.receipt.SubcategoryLabelGroup;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.projection.SubcategoryLabelRow;

/**
 * Pure normalize/rank/cap/group logic backing GET /receipts/subcategory-labels — deliberately a
 * standalone, dependency-free class (no Spring wiring, no DB) so the ranking rules can be unit
 * tested directly without Testcontainers, matching CLAUDE.md's "unit tests for all service-layer
 * business logic" gate. Takes {@link ReceiptLineItemRepository#findSubcategoryLabelRows()}'s flat
 * projection and applies the same two rules ADR-009 already established for
 * {@code /receipts/store-names}, at both the {@code subcategory} and {@code subSubcategory}
 * levels (see docs/architecture/02-domain-model-and-schema.md § Known Subcategory/Sub-Subcategory
 * Labels and ADR-010 § Cross-batch label consistency):
 *
 * <ol>
 *   <li>Normalize for dedup via {@code lower(trim(...))}; display the exact-cased variant with
 *       the highest occurrence count within its group, tie-broken by that variant's own most
 *       recent {@code capturedAt}.</li>
 *   <li>Rank groups by usage count descending, then most-recent {@code capturedAt} descending.</li>
 * </ol>
 *
 * <p>{@code subSubcategories} is capped at 30 per subcategory group; {@code subcategories} within
 * a category is uncapped. Only categories with at least one known subcategory appear in the
 * result (the 11-value set is not zero-filled here, unlike {@code /spending/summary}).
 */
public final class SubcategoryLabelGrouper {

    private static final int MAX_SUB_SUBCATEGORIES_PER_GROUP = 30;

    private SubcategoryLabelGrouper() {
    }

    public static List<CategorySubcategoryLabels> group(List<SubcategoryLabelRow> rows) {
        Map<SpendCategory, List<SubcategoryLabelRow>> byCategory = rows.stream()
                .collect(Collectors.groupingBy(SubcategoryLabelRow::getCategory, LinkedHashMap::new,
                        Collectors.toList()));

        List<CategorySubcategoryLabels> result = new ArrayList<>();
        for (SpendCategory category : SpendCategory.values()) {
            List<SubcategoryLabelRow> categoryRows = byCategory.get(category);
            if (categoryRows == null || categoryRows.isEmpty()) {
                continue;
            }
            result.add(new CategorySubcategoryLabels(category, groupSubcategories(categoryRows)));
        }
        return result;
    }

    private static List<SubcategoryLabelGroup> groupSubcategories(List<SubcategoryLabelRow> categoryRows) {
        Map<String, List<SubcategoryLabelRow>> bySubKey = categoryRows.stream()
                .collect(Collectors.groupingBy(r -> normalize(r.getSubcategory())));

        return rankedGroupKeys(bySubKey).stream()
                .map(subKey -> {
                    List<SubcategoryLabelRow> subRows = bySubKey.get(subKey);
                    String displaySubcategory = pickDisplayCasing(subRows, SubcategoryLabelRow::getSubcategory);
                    return new SubcategoryLabelGroup(displaySubcategory, rankSubSubcategories(subRows));
                })
                .toList();
    }

    private static List<String> rankSubSubcategories(List<SubcategoryLabelRow> subRows) {
        List<SubcategoryLabelRow> withSubSubcategory = subRows.stream()
                .filter(r -> r.getSubSubcategory() != null && !r.getSubSubcategory().isBlank())
                .toList();

        Map<String, List<SubcategoryLabelRow>> bySubSubKey = withSubSubcategory.stream()
                .collect(Collectors.groupingBy(r -> normalize(r.getSubSubcategory())));

        return rankedGroupKeys(bySubSubKey).stream()
                .limit(MAX_SUB_SUBCATEGORIES_PER_GROUP)
                .map(key -> pickDisplayCasing(bySubSubKey.get(key), SubcategoryLabelRow::getSubSubcategory))
                .toList();
    }

    /** Group keys ordered by usage count descending, then most-recent capturedAt descending. */
    private static List<String> rankedGroupKeys(Map<String, List<SubcategoryLabelRow>> byKey) {
        return byKey.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<String, List<SubcategoryLabelRow>>>comparingInt(e -> e.getValue().size())
                        .thenComparing(e -> maxCapturedAt(e.getValue()))
                        .reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Highest-occurrence exact-cased variant within a normalized group, tie-broken by recency. */
    private static String pickDisplayCasing(List<SubcategoryLabelRow> rows,
                                             Function<SubcategoryLabelRow, String> extractor) {
        Map<String, List<SubcategoryLabelRow>> byExactCasing = rows.stream()
                .collect(Collectors.groupingBy(extractor));
        return byExactCasing.entrySet().stream()
                .max(Comparator
                        .<Map.Entry<String, List<SubcategoryLabelRow>>>comparingInt(e -> e.getValue().size())
                        .thenComparing(e -> maxCapturedAt(e.getValue())))
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private static Instant maxCapturedAt(List<SubcategoryLabelRow> rows) {
        return rows.stream().map(SubcategoryLabelRow::getCapturedAt).max(Instant::compareTo).orElse(Instant.EPOCH);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
