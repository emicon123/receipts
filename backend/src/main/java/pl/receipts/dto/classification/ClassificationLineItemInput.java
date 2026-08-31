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
                                           BigDecimal quantity) {
}
