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
import pl.receipts.repository.projection.CategoryTotalRow;
import pl.receipts.repository.projection.MonthCategoryTotalRow;

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
}
