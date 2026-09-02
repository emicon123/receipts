package pl.receipts.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptStatus;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    /**
     * 3 optional filters (status, capturedAt range) collapsed into one null-tolerant JPQL query
     * rather than a Specification/Criteria abstraction — see ADR/02-domain-model-and-schema.md's
     * pattern evaluation table ("Specification pattern ... rejected: overkill for 3 filters").
     *
     * <p>{@code status} is bound as plain {@code String} (compared against {@code r.status} cast
     * to string) rather than the {@code ReceiptStatus} enum directly, and {@code :from}/{@code
     * :to}'s bare {@code IS NULL} occurrences are explicitly cast to {@code timestamp}. Both
     * work around the same underlying issue: binding a parameter whose ONLY occurrence in the
     * query is a bare {@code :param IS NULL} (no other type-revealing context) gives PGJDBC's
     * "extended" prepared-statement protocol nothing to infer that parameter's type from, and
     * Postgres rejects the whole statement with "could not determine data type of parameter $n"
     * — a real failure discovered by ReceiptRepositoryDataJpaTest against a live container, not
     * something an H2/mocked-DB test would have caught. Native-enum params (via Hibernate's
     * Postgres NAMED_ENUM binder, {@code Types.OTHER}) are hit hardest since that binder gives
     * PGJDBC no fallback type hint at all; explicitly casting either the parameter or its bound
     * Java type sidesteps it either way.
     */
    @Query("""
            SELECT r FROM Receipt r
            WHERE (:status IS NULL OR CAST(r.status AS string) = :status)
              AND (CAST(:from AS timestamp) IS NULL OR r.capturedAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR r.capturedAt < :to)
            ORDER BY r.capturedAt DESC, r.id DESC
            """)
    Page<Receipt> search(@Param("status") String status,
                          @Param("from") Instant from,
                          @Param("to") Instant to,
                          Pageable pageable);

    List<Receipt> findAllByStatusOrderByCapturedAtAsc(ReceiptStatus status);

    /**
     * Backs GET /receipts/store-names — see docs/architecture/02-domain-model-and-schema.md
     * § Store-Name Suggestions for the exact rules and ADR-009 for why this ranking was chosen.
     * No {@code status}/{@code source} filter — every receipt's {@code store_name} counts.
     *
     * <p>A native query rather than JPQL: {@code DISTINCT ON} (picking each dedup group's
     * best-cased display variant) has no JPQL equivalent, and the architect's SQL sketch is
     * reproduced here near-verbatim rather than re-derived — see that doc for the CTE-by-CTE
     * rationale (normalize -&gt; count casing variants -&gt; pick display casing -&gt; rank groups).
     */
    @Query(value = """
            WITH normalized AS (
                SELECT store_name, lower(trim(store_name)) AS norm_key, captured_at
                FROM receipts
                WHERE store_name IS NOT NULL AND trim(store_name) <> ''
            ),
            casing_counts AS (
                SELECT norm_key, store_name, COUNT(*) AS casing_count, MAX(captured_at) AS casing_last_used
                FROM normalized
                GROUP BY norm_key, store_name
            ),
            best_casing AS (
                SELECT DISTINCT ON (norm_key) norm_key, store_name AS display_name
                FROM casing_counts
                ORDER BY norm_key, casing_count DESC, casing_last_used DESC
            ),
            groups AS (
                SELECT norm_key, COUNT(*) AS usage_count, MAX(captured_at) AS last_used_at
                FROM normalized
                GROUP BY norm_key
            )
            SELECT b.display_name
            FROM groups g JOIN best_casing b USING (norm_key)
            ORDER BY g.usage_count DESC, g.last_used_at DESC
            LIMIT 20
            """, nativeQuery = true)
    List<String> findStoreNameSuggestions();
}
