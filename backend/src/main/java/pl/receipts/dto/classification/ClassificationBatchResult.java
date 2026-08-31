package pl.receipts.dto.classification;

import java.util.List;

public record ClassificationBatchResult(List<Long> processed, List<Long> failed,
                                         List<ClassificationBatchSkip> skipped) {
}
