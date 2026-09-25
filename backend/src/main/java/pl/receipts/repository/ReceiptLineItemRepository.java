package pl.receipts.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.projection.CategoryTotalRow;
import pl.receipts.repository.projection.MonthCategoryTotalRow;
import pl.receipts.repository.projection.SubcategoryLabelRow;
import pl.receipts.repository.projection.SubcategoryVariantTotalRow;

public interface ReceiptLineItemRepository extends JpaRepository<ReceiptLineItem, Long> {

    Optional<ReceiptLineItem> findByIdAndReceiptId(Long id, Long receiptId);

    /**
     * The "replace only uncorrected line items" rule (CLAUDE.md rule 4 / rule 2 in
     * 02-domain-model-and-schema.md) — a corrected=true row is never touched by this delete.
     */
    @Modifying
    @Query("DELETE FROM ReceiptLineItem li WHERE li.receipt.id = :receiptId AND li.corrected = false")
    void deleteUncorrectedByReceiptId(@Param("receiptId") Long receiptId);

    @Query("SELECT COALESCE(SUM(li.amount), 0) FROM ReceiptLineItem li WHERE li.receipt.id = :receiptId")
    BigDecimal sumAmountByReceiptId(@Param("receiptId") Long receiptId);

    List<ReceiptLineItem> findByReceiptIdOrderByIdAsc(Long receiptId);

    /** Backs GET /spending/summary. Only PROCESSED receipts have reliable line items. */
    @Query("""
            SELECT li.category AS category, SUM(li.amount) AS total
            FROM ReceiptLineItem li JOIN li.receipt r
            WHERE r.status = :status AND r.capturedAt >= :from AND r.capturedAt < :to
            GROUP BY li.category
            """)
    List<CategoryTotalRow> sumByCategory(@Param("status") ReceiptStatus status,
                                          @Param("from") Instant from,
                                          @Param("to") Instant to);

    /** Backs GET /spending/trend. */
    @Query("""
            SELECT EXTRACT(MONTH FROM r.capturedAt) AS month, li.category AS category, SUM(li.amount) AS total
            FROM ReceiptLineItem li JOIN li.receipt r
            WHERE r.status = :status AND r.capturedAt >= :from AND r.capturedAt < :to
            GROUP BY EXTRACT(MONTH FROM r.capturedAt), li.category
            """)
    List<MonthCategoryTotalRow> sumByMonthAndCategory(@Param("status") ReceiptStatus status,
                                                        @Param("from") Instant from,
                                                        @Param("to") Instant to);

    /**
     * Backs GET /spending/subcategory-summary (ADR-013) — same PROCESSED-only scope and month
     * window as {@link #sumByCategory}, grouped one level deeper by the <em>exact</em>
     * {@code subcategory} spelling (NULL groups as its own row). Normalization by
     * {@code lower(trim())} and display-spelling choice happen in Java
     * ({@link pl.receipts.service.SubcategorySpendingAggregator}), which is why each row carries
     * its line-item count and latest {@code capturedAt}.
     */
    @Query("""
            SELECT li.category AS category, li.subcategory AS subcategory, SUM(li.amount) AS total,
                   COUNT(li) AS itemCount, MAX(r.capturedAt) AS lastCapturedAt
            FROM ReceiptLineItem li JOIN li.receipt r
            WHERE r.status = :status AND r.capturedAt >= :from AND r.capturedAt < :to
            GROUP BY li.category, li.subcategory
            """)
    List<SubcategoryVariantTotalRow> sumBySubcategoryVariant(@Param("status") ReceiptStatus status,
                                                             @Param("from") Instant from,
                                                             @Param("to") Instant to);

    /**
     * Backs GET /spending/line-items (ADR-010 §3) — one category/month's line items, flat,
     * ordered by the parent receipt's {@code capturedAt} descending then line-item {@code id}
     * ascending (matches docs/openapi.yaml's documented sort). {@code JOIN FETCH} avoids an N+1
     * when the mapper reads {@code receiptId}/{@code storeName}/{@code capturedAt} off the parent
     * receipt. Same PROCESSED-only scope as {@code sumByCategory}/{@code sumByMonthAndCategory} —
     * PENDING/FAILED receipts don't have reliable line items.
     */
    @Query("""
            SELECT li FROM ReceiptLineItem li JOIN FETCH li.receipt r
            WHERE r.status = :status AND li.category = :category
              AND r.capturedAt >= :from AND r.capturedAt < :to
            ORDER BY r.capturedAt DESC, li.id ASC
            """)
    List<ReceiptLineItem> findForSpendingDrilldown(@Param("status") ReceiptStatus status,
                                                    @Param("category") SpendCategory category,
                                                    @Param("from") Instant from,
                                                    @Param("to") Instant to);

    /**
     * Backs GET /receipts/subcategory-labels — see
     * docs/architecture/02-domain-model-and-schema.md § Known Subcategory/Sub-Subcategory Labels
     * for the full rule set and {@link pl.receipts.service.SubcategoryLabelGrouper} for the
     * normalize/rank/cap/group logic applied to this flat projection in the service layer (that
     * doc explicitly sanctions doing this in Java over a two-level-nested SQL CTE). No
     * {@code status}/{@code source} filter — every receipt's labels count, same reasoning as
     * {@code findStoreNameSuggestions}. Only the {@code subcategory}-blank filter happens here;
     * a blank/null {@code subSubcategory} is filtered out in the grouper, since a subcategory can
     * be known even when no sub-subcategory has ever accompanied it.
     */
    @Query("""
            SELECT li.category AS category, li.subcategory AS subcategory,
                   li.subSubcategory AS subSubcategory, r.capturedAt AS capturedAt
            FROM ReceiptLineItem li JOIN li.receipt r
            WHERE li.subcategory IS NOT NULL AND trim(li.subcategory) <> ''
            """)
    List<SubcategoryLabelRow> findSubcategoryLabelRows();
}
