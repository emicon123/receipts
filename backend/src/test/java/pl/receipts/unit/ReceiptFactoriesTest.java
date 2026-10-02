package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.entity.ReceiptStatus;

/**
 * The two named factories on {@link Receipt} (docs/architecture/02-domain-model-and-schema.md
 * § Image Upload Paths): {@code newImageUpload} serves both image-backed sources, MANUAL keeps its
 * own {@code newManualEntry}.
 */
class ReceiptFactoriesTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-09-30T18:30:00Z");

    @ParameterizedTest
    @EnumSource(value = ReceiptSource.class, names = {"CAMERA", "IMAGE_IMPORT"})
    void newImageUploadIsPendingAndImageBackedForBothImageSources(ReceiptSource source) {
        Receipt receipt = Receipt.newImageUpload(source, "2026/09/a.png", CAPTURED_AT);

        assertThat(receipt.getSource()).isEqualTo(source);
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(receipt.getImagePath()).isEqualTo("2026/09/a.png");
        assertThat(receipt.getCapturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(receipt.getStoreName()).isNull();
        assertThat(receipt.getTotalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(receipt.getProcessedAt()).isNull();
        assertThat(receipt.getFailureReason()).isNull();
        assertThat(receipt.getCreatedAt()).isNotNull();
    }

    @Test
    void newImageUploadRejectsManualBecauseManualHasNoImage() {
        assertThatThrownBy(() -> Receipt.newImageUpload(ReceiptSource.MANUAL, "2026/09/a.jpg", CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MANUAL");
    }

    @Test
    void newImageUploadRejectsMissingSource() {
        assertThatThrownBy(() -> Receipt.newImageUpload(null, "2026/09/a.jpg", CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newManualEntryIsStillCreatedProcessedWithoutImage() {
        Receipt receipt = Receipt.newManualEntry(CAPTURED_AT, "PGE");

        assertThat(receipt.getSource()).isEqualTo(ReceiptSource.MANUAL);
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PROCESSED);
        assertThat(receipt.getImagePath()).isNull();
        assertThat(receipt.getStoreName()).isEqualTo("PGE");
    }
}
