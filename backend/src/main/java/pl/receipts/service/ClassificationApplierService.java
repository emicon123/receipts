package pl.receipts.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.receipts.dto.classification.ClassificationBatchItem;
import pl.receipts.dto.classification.ClassificationLineItemInput;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.entity.SpendCategory;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;

/**
 * Applies ONE classification-batch entry (an {@code items[]} success or a {@code failures[]}
 * failure) to ONE receipt, each in its own transaction. Deliberately a separate Spring bean from
 * {@link ClassificationBatchService} (which loops over the whole batch, not transactional
 * itself) so that Spring's transactional proxy actually applies per call — self-invocation from
 * within the same class would silently skip the proxy and merge every receipt into one
 * transaction, defeating CLAUDE.md's "one receipt's failure to persist shouldn't roll back the
 * rest of the batch" gate.
 *
 * <p>Deliberately has NO "must currently be PENDING" guard — see docs/architecture/04's
 * idempotency note: resubmitting the same batch (retry, or reprocessing a PROCESSED receipt's
 * old entry) must reapply cleanly regardless of the receipt's current status.
 */
@Service
public class ClassificationApplierService {

    public enum Outcome { PROCESSED, FAILED }

    public record ApplyResult(Outcome outcome, String failureReason) {
    }

    private final ReceiptRepository receiptRepository;
    private final ReceiptLineItemRepository lineItemRepository;

    public ClassificationApplierService(ReceiptRepository receiptRepository,
                                         ReceiptLineItemRepository lineItemRepository) {
        this.receiptRepository = receiptRepository;
        this.lineItemRepository = lineItemRepository;
    }

    @Transactional
    public ApplyResult applyItem(ClassificationBatchItem item) {
        Receipt receipt = receiptRepository.findById(item.receiptId())
                .orElseThrow(() -> new NoSuchElementException("receipt " + item.receiptId() + " not found"));

        if (item.lineItems() == null) {
            return markFailed(receipt, "classification entry for receipt " + item.receiptId()
                    + " is missing lineItems");
        }

        List<ReceiptLineItem> parsed = new ArrayList<>();
        for (ClassificationLineItemInput input : item.lineItems()) {
            String invalidReason = validate(input);
            if (invalidReason != null) {
                return markFailed(receipt, invalidReason);
            }
            SpendCategory category = SpendCategory.tryParse(input.category()).orElseThrow();
            parsed.add(new ReceiptLineItem(input.productName(), category, input.amount(), input.quantity()));
        }

        // Durably mark PROCESSING before the risky delete+insert work, so a mid-batch crash
        // leaves this receipt recoverable via POST /reprocess?force=true rather than silently
        // half-applied — see docs/architecture/03-receipt-lifecycle.md "Why PROCESSING exists".
        receipt.setStatus(ReceiptStatus.PROCESSING);
        receiptRepository.saveAndFlush(receipt);

        lineItemRepository.deleteUncorrectedByReceiptId(receipt.getId());
        for (ReceiptLineItem lineItem : parsed) {
            lineItem.setReceipt(receipt);
        }
        lineItemRepository.saveAll(parsed);

        BigDecimal total = lineItemRepository.sumAmountByReceiptId(receipt.getId());
        receipt.setTotalAmount(total);
        if (item.storeName() != null) {
            receipt.setStoreName(item.storeName());
        }
        if (item.capturedAt() != null) {
            receipt.setCapturedAt(combineDate(receipt.getCapturedAt(), item.capturedAt()));
        }
        receipt.setStatus(ReceiptStatus.PROCESSED);
        receipt.setFailureReason(null);
        receipt.setProcessedAt(Instant.now());
        receiptRepository.save(receipt);

        return new ApplyResult(Outcome.PROCESSED, null);
    }

    @Transactional
    public void applyFailure(Long receiptId, String reason) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NoSuchElementException("receipt " + receiptId + " not found"));
        markFailed(receipt, reason);
    }

    private ApplyResult markFailed(Receipt receipt, String reason) {
        receipt.setStatus(ReceiptStatus.PROCESSING);
        receipt.setStatus(ReceiptStatus.FAILED);
        receipt.setFailureReason(reason);
        receipt.setProcessedAt(Instant.now());
        receiptRepository.save(receipt);
        return new ApplyResult(Outcome.FAILED, reason);
    }

    /**
     * Tolerant, per-entry validation — mirrors the DB CHECK constraints in V1__init.sql
     * (amount &gt;= 0, quantity NULL or &gt; 0) plus the fixed category enum. Any violation here
     * routes the WHOLE owning receipt to FAILED (never a partial per-line-item skip, and never a
     * thrown exception that would abort the rest of the batch) — see
     * docs/architecture/05-api-contract.md's tolerance rules.
     */
    private String validate(ClassificationLineItemInput input) {
        if (input.productName() == null || input.productName().isBlank()) {
            return "classification line item is missing productName";
        }
        if (SpendCategory.tryParse(input.category()).isEmpty()) {
            return "invalid category '" + input.category() + "' for product '" + input.productName() + "'";
        }
        if (input.amount() == null || input.amount().signum() < 0) {
            return "invalid amount for product '" + input.productName() + "'";
        }
        if (input.quantity() != null && input.quantity().signum() <= 0) {
            return "invalid quantity for product '" + input.productName() + "'";
        }
        return null;
    }

    /**
     * The classifier's capturedAt is a plain date; combine it with the receipt's existing
     * time-of-day (preserving the original upload instant's clock time) rather than a synthetic
     * midnight/noon default — see docs/architecture/05-api-contract.md's "Money and Dates"
     * section, which leaves the exact combination rule to the backend.
     */
    private Instant combineDate(Instant existingCapturedAt, LocalDate newDate) {
        LocalDateTime combined = LocalDateTime.of(newDate, existingCapturedAt.atZone(ZoneOffset.UTC).toLocalTime());
        return combined.toInstant(ZoneOffset.UTC);
    }
}
