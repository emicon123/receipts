package pl.receipts.dto.spending;

import java.math.BigDecimal;

/** One normalized subcategory group's sum within a category/month — see ADR-013. */
public record SubcategoryAmount(String subcategory, BigDecimal amount) {
}
