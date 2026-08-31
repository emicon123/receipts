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
}
