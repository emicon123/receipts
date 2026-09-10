package pl.receipts.dto.receipt;

import java.util.List;
import pl.receipts.dto.common.Meta;

public record SubcategoryLabelsResponse(List<CategorySubcategoryLabels> data, Meta meta) {
    public SubcategoryLabelsResponse(List<CategorySubcategoryLabels> data) {
        this(data, Meta.now());
    }
}
