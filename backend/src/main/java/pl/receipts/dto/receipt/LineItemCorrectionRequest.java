package pl.receipts.dto.receipt;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import pl.receipts.validation.ValidCategory;

/**
 * All fields optional/nullable — only the ones present (non-null) in the JSON body are applied.
 * As a consequence, this endpoint cannot be used to explicitly clear {@code quantity} back to
 * null (a JSON {@code "quantity": null} is indistinguishable from omitting the field entirely
 * under this plain-record deserialization) — a documented, deliberate simplification; see the
 * Backend agent's final report for the full rationale.
 */
public record LineItemCorrectionRequest(
        @Size(max = 300) String productName,
        @ValidCategory String category,
        @DecimalMin(value = "0.0") BigDecimal amount,
        @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity) {
}
