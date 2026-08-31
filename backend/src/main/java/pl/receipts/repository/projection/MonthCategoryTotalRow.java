package pl.receipts.repository.projection;

import java.math.BigDecimal;
import pl.receipts.entity.SpendCategory;

/**
 * Spring Data JPA interface projection for {@code GROUP BY (month, category)}. {@code month} is
 * {@code Number} rather than {@code Integer} because JPQL's {@code EXTRACT(MONTH FROM ...)}
 * returns a numeric type whose exact box type depends on the dialect.
 */
public interface MonthCategoryTotalRow {
    Number getMonth();

    SpendCategory getCategory();

    BigDecimal getTotal();
}
