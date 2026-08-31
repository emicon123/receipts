package pl.receipts.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.receipts.dto.receipt.LineItemCorrectionRequest;
import pl.receipts.dto.receipt.LineItemResponse;
import pl.receipts.dto.receipt.ManualReceiptRequest;
import pl.receipts.dto.receipt.ReceiptDetailResponse;
import pl.receipts.dto.receipt.ReceiptListResponse;
import pl.receipts.dto.receipt.ReceiptSummaryResponse;
import pl.receipts.dto.receipt.ReprocessRequest;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.service.LineItemCorrectionService;
import pl.receipts.service.ReceiptService;
import pl.receipts.storage.LoadedImage;

/**
 * Frontend-facing receipt surface: upload, manual entry, list/detail, image bytes, correction,
 * reprocess, delete. The classify-receipts.sh-specific endpoints (pending list, batch submit)
 * live in {@link ClassificationController} — see that class's Javadoc for the split rationale.
 */
@RestController
@RequestMapping("/api/receipts")
@Validated
public class ReceiptController {

    private final ReceiptService receiptService;
    private final LineItemCorrectionService lineItemCorrectionService;

    public ReceiptController(ReceiptService receiptService, LineItemCorrectionService lineItemCorrectionService) {
        this.receiptService = receiptService;
        this.lineItemCorrectionService = lineItemCorrectionService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ReceiptSummaryResponse> upload(@RequestPart("image") MultipartFile image,
                                                           @RequestParam(required = false) Instant capturedAt) {
        var summary = receiptService.uploadCameraReceipt(image, capturedAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReceiptSummaryResponse(summary));
    }

    @PostMapping("/manual")
    public ResponseEntity<ReceiptDetailResponse> createManual(@Valid @RequestBody ManualReceiptRequest request) {
        var detail = receiptService.createManualReceipt(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ReceiptDetailResponse(detail));
    }

    @GetMapping
    public ReceiptListResponse list(@RequestParam(required = false) @Min(2000) @Max(2100) Integer year,
                                     @RequestParam(required = false) @Min(1) @Max(12) Integer month,
                                     @RequestParam(required = false) ReceiptStatus status,
                                     @RequestParam(defaultValue = "0") @Min(0) int page,
                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return receiptService.list(year, month, status, page, size);
    }

    @GetMapping("/{id}")
    public ReceiptDetailResponse get(@PathVariable Long id) {
        return new ReceiptDetailResponse(receiptService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        receiptService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<Resource> getImage(@PathVariable Long id) {
        LoadedImage image = receiptService.getImage(id);
        return ResponseEntity.ok().contentType(image.mediaType()).body(image.resource());
    }

    @PutMapping("/{id}/line-items/{itemId}")
    public LineItemResponse correctLineItem(@PathVariable Long id, @PathVariable Long itemId,
                                             @Valid @RequestBody LineItemCorrectionRequest request) {
        return new LineItemResponse(lineItemCorrectionService.correct(id, itemId, request));
    }

    @PostMapping("/{id}/reprocess")
    public ReceiptSummaryResponse reprocess(@PathVariable Long id,
                                             @RequestBody(required = false) ReprocessRequest request) {
        boolean force = request != null && request.forceOrDefault();
        return new ReceiptSummaryResponse(receiptService.reprocess(id, force));
    }
}
