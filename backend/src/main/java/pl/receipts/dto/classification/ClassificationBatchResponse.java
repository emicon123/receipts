package pl.receipts.dto.classification;

import pl.receipts.dto.common.Meta;

public record ClassificationBatchResponse(ClassificationBatchResult data, Meta meta) {
    public ClassificationBatchResponse(ClassificationBatchResult data) {
        this(data, Meta.now());
    }
}
