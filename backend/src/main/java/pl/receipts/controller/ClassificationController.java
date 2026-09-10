package pl.receipts.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.receipts.dto.classification.ClassificationBatchRequest;
import pl.receipts.dto.classification.ClassificationBatchResponse;
import pl.receipts.dto.receipt.PendingReceiptsResponse;
import pl.receipts.dto.receipt.SubcategoryLabelsResponse;
import pl.receipts.service.ClassificationBatchService;
import pl.receipts.service.ReceiptService;

/**
 * The three endpoints owned by infra/classify/classify-receipts.sh's daily run — see CLAUDE.md
 * § Daily classification job and, for the newest of the three, ADR-010 § Cross-batch label
 * consistency. Split out from {@link ReceiptController} purely for ownership clarity (matches
 * docs/openapi.yaml's [classification] tag); URLs still live under /api/receipts per the OpenAPI
 * paths.
 */
@RestController
@RequestMapping("/api/receipts")
public class ClassificationController {

    private final ReceiptService receiptService;
    private final ClassificationBatchService classificationBatchService;

    public ClassificationController(ReceiptService receiptService,
                                     ClassificationBatchService classificationBatchService) {
        this.receiptService = receiptService;
        this.classificationBatchService = classificationBatchService;
    }

    @GetMapping("/pending")
    public PendingReceiptsResponse pending() {
        return receiptService.listPending();
    }

    @GetMapping("/subcategory-labels")
    public SubcategoryLabelsResponse subcategoryLabels() {
        return receiptService.listSubcategoryLabels();
    }

    @PostMapping("/classification-batch")
    public ClassificationBatchResponse submit(@RequestBody ClassificationBatchRequest request) {
        return new ClassificationBatchResponse(classificationBatchService.submit(request));
    }
}
