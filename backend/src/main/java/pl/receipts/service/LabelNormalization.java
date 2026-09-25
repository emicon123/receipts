package pl.receipts.service;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/**
 * The ADR-010 §5 label rule, in exactly one place: free-text labels are grouped by
 * {@code lower(trim(label))}, and a group is displayed as its most-used exact spelling. Shared
 * by {@link SubcategoryLabelGrouper} (GET /receipts/subcategory-labels) and
 * {@link SubcategorySpendingAggregator} (GET /spending/subcategory-summary, ADR-013) — see
 * docs/architecture/02-domain-model-and-schema.md § Subcategory Spending Breakdown.
 */
final class LabelNormalization {

    /** One exact spelling of a label within a normalized group, with its usage stats. */
    record Variant(String label, long count, Instant lastUsedAt) {
    }

    private static final Comparator<Variant> DISPLAY_PREFERENCE = Comparator
            .comparingLong(Variant::count).reversed()
            .thenComparing(Variant::lastUsedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Variant::label);

    private LabelNormalization() {
    }

    /** Grouping key: {@code lower(trim(value))}; {@code null} normalizes to {@code ""}. */
    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * The spelling to display for one normalized group: highest usage count, then most recent
     * use, then the spelling itself ascending (so the choice is deterministic).
     */
    static String pickDisplayCasing(Collection<Variant> variants) {
        return variants.stream().min(DISPLAY_PREFERENCE).map(Variant::label).orElseThrow();
    }
}
