package pl.receipts.entity;

import java.util.Optional;

/**
 * The fixed 11-value spending category taxonomy. Canonical source of truth for what each value
 * means is CLAUDE.md § Categories at the repo root — this enum is a direct translation of that
 * table (and of the Postgres {@code spend_category_enum} type in V1__init.sql), not a
 * redefinition of it. Declaration order here is the "canonical order" referenced throughout
 * docs/openapi.yaml (GET /categories, and the zero-filled spending summary/trend responses).
 */
public enum SpendCategory {
    ALKO,
    JEDZENIE_KONIECZNE,
    JEDZENIE_SREDNIE,
    JEDZENIE_PIERDOLOWATE,
    RZECZY_PALIWO_INNE_ROZNE,
    RZECZY_LUKSUSOWE,
    MYCIE_CHEMIA,
    ROZRYWKA_RESTAURACJE,
    RACHUNKI,
    BOBINEK,
    SUPLE;

    /**
     * Parses a raw string (e.g. classifier output, or user-correction input) against this enum
     * without throwing — callers decide per-context how to react to an invalid value (see
     * CLAUDE.md's gate: "Claude must never invent a category outside the fixed 11 — validate its
     * output against the enum and flag (don't silently coerce) any mismatch").
     */
    public static Optional<SpendCategory> tryParse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        for (SpendCategory category : values()) {
            if (category.name().equals(raw)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }
}
