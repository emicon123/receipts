package pl.receipts.dto.spending;

import java.math.BigDecimal;
import pl.receipts.entity.SpendCategory;

public record CategoryAmount(SpendCategory category, BigDecimal amount) {
}
