package pl.receipts.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a raw string is one of the fixed 11 {@link pl.receipts.entity.SpendCategory}
 * values. Applied to human-input paths (manual entry, line-item correction) where an unknown
 * category must fail the request with a 422 — see GlobalExceptionHandler. Null is considered
 * valid (pair with {@code @NotBlank} where presence is also required); this mirrors the
 * classification-batch path's tolerant per-entry handling, which validates the same way but
 * without throwing (see ClassificationApplierService).
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CategoryValidator.class)
public @interface ValidCategory {
    String message() default "must be one of the 11 fixed categories";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
