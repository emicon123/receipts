package pl.receipts.dto.classification;

import java.util.List;

/**
 * Deliberately un-{@code @Valid}-ated at the controller: this endpoint's own contract (see
 * docs/openapi.yaml) says a structurally malformed body (not valid JSON, or a totally missing
 * {@code items}/{@code failures} key) is the *only* 400 case; everything else is tolerated
 * per-entry. ClassificationBatchService checks {@code items}/{@code failures} for null itself
 * and throws MalformedBatchRequestException (-> 400) rather than relying on bean validation
 * (which on this codebase's convention maps to 422 — not what this endpoint documents).
 */
public record ClassificationBatchRequest(List<ClassificationBatchItem> items,
                                          List<ClassificationBatchFailure> failures) {
}
