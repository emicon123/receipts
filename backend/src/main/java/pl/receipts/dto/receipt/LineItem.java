package pl.receipts.dto.receipt;

import java.math.BigDecimal;
import pl.receipts.entity.SpendCategory;

/** Response representation of a line item — category is a real enum here since it's always
 * a validated, already-persisted value (no risk of an invalid string reaching this type).
 * {@code subcategory}/{@code subSubcategory} are free-text, classifier-assigned, nullable — see
 * ADR-010; unlike {@code corrected}, they carry no sticky-edit protection. */
public record LineItem(Long id, String productName, SpendCategory category, BigDecimal amount,
                        BigDecimal quantity, String subcategory, String subSubcategory, boolean corrected) {
}
