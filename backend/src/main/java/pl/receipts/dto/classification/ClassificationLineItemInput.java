package pl.receipts.dto.classification;

import java.math.BigDecimal;

/**
 * Wire-identical to {@link pl.receipts.dto.receipt.LineItemInput} but deliberately its own type:
 * every field is unvalidated-on-deserialize (no bean validation annotations, {@code category}
 * plain {@code String}) so that a malformed entry from the classifier can NEVER blow up Jackson
 * deserialization / bean validation for the whole request. Per docs/openapi.yaml, a bad
 * {@code category} value here must route only the owning receipt to FAILED, never reject the
 * whole classification-batch call — see ClassificationApplierService for where these are
 * actually validated, per-receipt.
 */
public record ClassificationLineItemInput(String productName, String category, BigDecimal amount,
                                           BigDecimal quantity, String subcategory, String subSubcategory) {

    /**
     * Convenience constructor for the pre-ADR-010 4-arg call shape (an older prompt/script
     * version, or a test, that omits subcategory/subSubcategory) — both fields simply come
     * through as {@code null}, which is always valid (ADR-010: omission must never fail
     * validation).
     */
    public ClassificationLineItemInput(String productName, String category, BigDecimal amount,
                                        BigDecimal quantity) {
        this(productName, category, amount, quantity, null, null);
    }
}
