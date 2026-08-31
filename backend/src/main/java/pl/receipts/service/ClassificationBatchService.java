package pl.receipts.service;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pl.receipts.dto.classification.ClassificationBatchFailure;
import pl.receipts.dto.classification.ClassificationBatchItem;
import pl.receipts.dto.classification.ClassificationBatchRequest;
import pl.receipts.dto.classification.ClassificationBatchResult;
import pl.receipts.dto.classification.ClassificationBatchSkip;
import pl.receipts.exception.MalformedBatchRequestException;
import pl.receipts.repository.ReceiptRepository;

/**
 * Orchestrates POST /receipts/classification-batch: loops over {@code items}/{@code failures}
 * and delegates each entry's actual DB write to {@link ClassificationApplierService} (a separate
 * bean, so each entry commits in its own transaction — see that class's Javadoc). This class is
 * deliberately NOT {@code @Transactional} itself: per CLAUDE.md, "one receipt's failure to
 * persist shouldn't roll back the rest of the batch."
 */
@Service
public class ClassificationBatchService {

    private static final Logger log = LoggerFactory.getLogger(ClassificationBatchService.class);

    private final ReceiptRepository receiptRepository;
    private final ClassificationApplierService applier;

    public ClassificationBatchService(ReceiptRepository receiptRepository, ClassificationApplierService applier) {
        this.receiptRepository = receiptRepository;
        this.applier = applier;
    }

    public ClassificationBatchResult submit(ClassificationBatchRequest request) {
        if (request == null || request.items() == null || request.failures() == null) {
            throw new MalformedBatchRequestException("request body must include both 'items' and 'failures'");
        }

        List<Long> processed = new ArrayList<>();
        List<Long> failed = new ArrayList<>();
        List<ClassificationBatchSkip> skipped = new ArrayList<>();

        for (ClassificationBatchItem item : request.items()) {
            if (item == null || item.receiptId() == null) {
                log.warn("classification-batch: dropping an items[] entry with no receiptId");
                continue;
            }
            if (!receiptRepository.existsById(item.receiptId())) {
                skipped.add(new ClassificationBatchSkip(item.receiptId(), "unknown receiptId"));
                continue;
            }
            try {
                var result = applier.applyItem(item);
                switch (result.outcome()) {
                    case PROCESSED -> processed.add(item.receiptId());
                    case FAILED -> failed.add(item.receiptId());
                }
            } catch (Exception e) {
                // Unexpected persistence-level failure (not a classifier content problem) — the
                // transaction rolled back, so the receipt is unchanged (still whatever it was,
                // typically PENDING). Never reported as FAILED (that's reserved for genuine
                // content problems per CLAUDE.md step 5) — it naturally resurfaces via the next
                // GET /receipts/pending run, same as a quota-exhaustion no-op.
                log.error("classification-batch: unexpected failure applying receipt {}", item.receiptId(), e);
            }
        }

        for (ClassificationBatchFailure failure : request.failures()) {
            if (failure == null || failure.receiptId() == null) {
                log.warn("classification-batch: dropping a failures[] entry with no receiptId");
                continue;
            }
            if (!receiptRepository.existsById(failure.receiptId())) {
                skipped.add(new ClassificationBatchSkip(failure.receiptId(), "unknown receiptId"));
                continue;
            }
            try {
                applier.applyFailure(failure.receiptId(), failure.reason());
                failed.add(failure.receiptId());
            } catch (Exception e) {
                log.error("classification-batch: unexpected failure recording failure for receipt {}",
                        failure.receiptId(), e);
            }
        }

        return new ClassificationBatchResult(processed, failed, skipped);
    }
}
