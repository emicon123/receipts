package pl.receipts.dto.receipt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import pl.receipts.validation.ValidCategory;

/**
 * Strict human-input line item (manual entry). {@code category} is deliberately a raw
 * {@code String} rather than the {@link pl.receipts.entity.SpendCategory} enum so that an
 * out-of-enum value fails via {@code @ValidCategory} (a normal 422 bean-validation error with a
 * clean field-level message) rather than a Jackson enum-deserialization failure (which would be
 * a 400 "unparseable body" and carries a less friendly message) — see GlobalExceptionHandler.
 *
 * <p>quantity's lower bound is exclusive (must be {@code > 0} when present), matching the DB
 * CHECK constraint {@code receipt_line_items_quantity_positive} in V1__init.sql — note this is
 * stricter than the OpenAPI schema's stated {@code minimum: 0} for {@code LineItemInput.quantity},
 * which would (if taken literally) allow a value the database rejects. Resolved in the DB's favor
 * since that CHECK constraint is a hard invariant; flagged to the Architect as a spec mismatch.
 */
public record LineItemInput(
        @NotBlank @Size(max = 300) String productName,
        @NotBlank @ValidCategory String category,
        @NotNull @DecimalMin(value = "0.0") BigDecimal amount,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
        @Size(max = 100) String subcategory,
        @Size(max = 100) String subSubcategory) {

    /**
     * Convenience constructor for the common (pre-ADR-010) 4-arg call shape — existing callers
     * (manual entry today has no UI for these two fields) and tests that don't care about
     * subcategory/subSubcategory stay unchanged; both simply persist as {@code null} (ADR-010).
     */
    public LineItemInput(String productName, String category, BigDecimal amount, BigDecimal quantity) {
        this(productName, category, amount, quantity, null, null);
    }
}
