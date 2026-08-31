package pl.receipts.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pl.receipts.dto.common.PageInfo;
import pl.receipts.dto.receipt.LineItemInput;
import pl.receipts.dto.receipt.ManualReceiptRequest;
import pl.receipts.dto.receipt.PendingReceiptRef;
import pl.receipts.dto.receipt.PendingReceiptsResponse;
import pl.receipts.dto.receipt.ReceiptDetail;
import pl.receipts.dto.receipt.ReceiptListResponse;
import pl.receipts.dto.receipt.ReceiptSummary;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptLineItem;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.entity.SpendCategory;
import pl.receipts.exception.InvalidQueryParamException;
import pl.receipts.exception.ReceiptStateConflictException;
import pl.receipts.mapper.ReceiptMapper;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;
import pl.receipts.storage.ImageStorageService;
import pl.receipts.storage.LoadedImage;
import pl.receipts.storage.StoredImage;

/**
 * Owns the receipt aggregate's CRUD + lifecycle-adjacent operations that aren't classification
 * itself: upload, manual entry, list/detail, delete, reprocess (a pure status reset — no
 * classification logic, see CLAUDE.md rule 8). Classification-batch application lives in
 * {@link ClassificationBatchService} / {@link ClassificationApplierService} — kept separate so
 * this class isn't also the one deciding per-transaction isolation for a many-receipt batch.
 */
@Service
public class ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final ReceiptLineItemRepository lineItemRepository;
    private final ReceiptMapper receiptMapper;
    private final ImageStorageService imageStorageService;

    public ReceiptService(ReceiptRepository receiptRepository, ReceiptLineItemRepository lineItemRepository,
                           ReceiptMapper receiptMapper, ImageStorageService imageStorageService) {
        this.receiptRepository = receiptRepository;
        this.lineItemRepository = lineItemRepository;
        this.receiptMapper = receiptMapper;
        this.imageStorageService = imageStorageService;
    }

    @Transactional
    public ReceiptSummary uploadCameraReceipt(MultipartFile image, Instant capturedAt) {
        Instant effectiveCapturedAt = capturedAt != null ? capturedAt : Instant.now();
        StoredImage stored = imageStorageService.store(image, effectiveCapturedAt);
        Receipt receipt = Receipt.newCameraUpload(stored.relativePath(), effectiveCapturedAt);
        receiptRepository.save(receipt);
        return receiptMapper.toSummary(receipt);
    }

    @Transactional
    public ReceiptDetail createManualReceipt(ManualReceiptRequest request) {
        Receipt receipt = Receipt.newManualEntry(request.capturedAt(), request.storeName());
        BigDecimal total = BigDecimal.ZERO;
        for (LineItemInput input : request.lineItems()) {
            SpendCategory category = SpendCategory.tryParse(input.category())
                    .orElseThrow(() -> new IllegalStateException(
                            "category already validated by @ValidCategory: " + input.category()));
            ReceiptLineItem lineItem = new ReceiptLineItem(input.productName(), category, input.amount(),
                    input.quantity());
            receipt.addLineItem(lineItem);
            total = total.add(input.amount());
        }
        receipt.setTotalAmount(total);
        receipt.setProcessedAt(Instant.now());
        receiptRepository.save(receipt);
        return receiptMapper.toDetail(receipt);
    }

    @Transactional
    public ReceiptListResponse list(Integer year, Integer month, ReceiptStatus status, int page, int size) {
        if (month != null && year == null) {
            throw new InvalidQueryParamException("month", "month requires year to also be present");
        }
        Instant from = null;
        Instant to = null;
        if (year != null) {
            if (month != null) {
                ZonedDateTime start = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneOffset.UTC);
                from = start.toInstant();
                to = start.plusMonths(1).toInstant();
            } else {
                ZonedDateTime start = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
                from = start.toInstant();
                to = start.plusYears(1).toInstant();
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        String statusFilter = status == null ? null : status.name();
        Page<Receipt> result = receiptRepository.search(statusFilter, from, to, pageable);
        List<ReceiptSummary> data = result.getContent().stream().map(receiptMapper::toSummary).toList();
        PageInfo pageInfo = new PageInfo(result.getNumber(), result.getSize(), result.getTotalElements());
        return new ReceiptListResponse(data, pageInfo);
    }

    /**
     * Backs GET /receipts/pending — called by classify-receipts.sh. Pure read, never mutates
     * status (see docs/architecture/03-receipt-lifecycle.md — the only way into PROCESSING is a
     * classification-batch submission that's about to resolve the receipt in the same request).
     */
    @Transactional(readOnly = true)
    public PendingReceiptsResponse listPending() {
        List<PendingReceiptRef> refs = receiptRepository.findAllByStatusOrderByCapturedAtAsc(ReceiptStatus.PENDING)
                .stream()
                .map(r -> new PendingReceiptRef(r.getId()))
                .toList();
        return new PendingReceiptsResponse(refs);
    }

    @Transactional
    public ReceiptDetail getDetail(Long id) {
        Receipt receipt = findOrThrow(id);
        return receiptMapper.toDetail(receipt);
    }

    @Transactional
    public LoadedImage getImage(Long id) {
        Receipt receipt = findOrThrow(id);
        if (receipt.getImagePath() == null) {
            throw new NoSuchElementException("receipt " + id + " has no image (MANUAL entry)");
        }
        return imageStorageService.load(receipt.getImagePath());
    }

    @Transactional
    public void delete(Long id) {
        Receipt receipt = findOrThrow(id);
        String imagePath = receipt.getImagePath();
        receiptRepository.delete(receipt);
        imageStorageService.deleteQuietly(imagePath);
    }

    @Transactional
    public ReceiptSummary reprocess(Long id, boolean force) {
        Receipt receipt = findOrThrow(id);
        switch (receipt.getStatus()) {
            case FAILED -> resetToPending(receipt);
            case PENDING -> {
                // no-op, idempotent
            }
            case PROCESSED, PROCESSING -> {
                if (!force) {
                    throw new ReceiptStateConflictException(
                            "receipt " + id + " is " + receipt.getStatus() + "; force=true required to reprocess");
                }
                resetToPending(receipt);
            }
        }
        return receiptMapper.toSummary(receipt);
    }

    private void resetToPending(Receipt receipt) {
        receipt.setStatus(ReceiptStatus.PENDING);
        receipt.setFailureReason(null);
        receipt.setProcessedAt(null);
    }

    private Receipt findOrThrow(Long id) {
        return receiptRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("receipt " + id + " not found"));
    }
}
