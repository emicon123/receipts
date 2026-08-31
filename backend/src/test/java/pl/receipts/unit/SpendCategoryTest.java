package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.receipts.entity.SpendCategory;

class SpendCategoryTest {

    @Test
    void parsesEveryFixedValue() {
        for (SpendCategory category : SpendCategory.values()) {
            assertThat(SpendCategory.tryParse(category.name())).contains(category);
        }
    }

    @Test
    void rejectsUnknownValue() {
        assertThat(SpendCategory.tryParse("NOT_A_REAL_CATEGORY")).isEmpty();
    }

    @Test
    void rejectsNull() {
        assertThat(SpendCategory.tryParse(null)).isEmpty();
    }

    @Test
    void isCaseSensitive() {
        // Claude's output must match the enum exactly — never silently coerced/lower-cased.
        assertThat(SpendCategory.tryParse("alko")).isEmpty();
    }

    @Test
    void exposesExactlyElevenFixedCategories() {
        assertThat(SpendCategory.values()).hasSize(11);
    }
}
