package pl.receipts.dto.receipt;

import java.math.BigDecimal;
import pl.receipts.entity.SpendCategory;

/** Response representation of a line item — category is a real enum here since it's always
 * a validated, already-persisted value (no risk of an invalid string reaching this type). */
public record LineItem(Long id, String productName, SpendCategory category, BigDecimal amount,
                        BigDecimal quantity, boolean corrected) {
}
