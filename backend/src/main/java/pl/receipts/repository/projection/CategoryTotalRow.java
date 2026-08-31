package pl.receipts.repository.projection;

import java.math.BigDecimal;
import pl.receipts.entity.SpendCategory;

/** Spring Data JPA interface projection for {@code GROUP BY category} aggregate queries. */
public interface CategoryTotalRow {
    SpendCategory getCategory();

    BigDecimal getTotal();
}
