package pl.receipts.dto.category;

import pl.receipts.entity.SpendCategory;

public record CategoryInfo(SpendCategory code, String label, String gloss) {
}
