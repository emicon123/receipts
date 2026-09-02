package pl.receipts.dto.receipt;

import java.util.List;
import pl.receipts.dto.common.Meta;

public record StoreNameSuggestionsResponse(List<String> data, Meta meta) {
    public StoreNameSuggestionsResponse(List<String> data) {
        this(data, Meta.now());
    }
}
