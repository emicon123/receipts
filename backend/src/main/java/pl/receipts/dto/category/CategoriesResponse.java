package pl.receipts.dto.category;

import java.util.List;
import pl.receipts.dto.common.Meta;

public record CategoriesResponse(List<CategoryInfo> data, Meta meta) {
    public CategoriesResponse(List<CategoryInfo> data) {
        this(data, Meta.now());
    }
}
