package pl.receipts.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import pl.receipts.dto.receipt.PendingReceiptRef;
import pl.receipts.dto.receipt.ReceiptSummary;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.exception.UnsupportedImageTypeException;
import pl.receipts.mapper.ReceiptMapperImpl;
import pl.receipts.repository.ReceiptLineItemRepository;
import pl.receipts.repository.ReceiptRepository;
import pl.receipts.service.ReceiptService;
import pl.receipts.storage.ImageStorageService;
import pl.receipts.storage.LoadedImage;
import pl.receipts.storage.StoredImage;

/**
 * Pure-Mockito tests for the image-backed upload path shared by POST /receipts (CAMERA) and POST
 * /receipts/image-import (IMAGE_IMPORT) — ReceiptService.uploadImageReceipt — plus the two reads
 * that expose a receipt's source / image (GET /receipts/pending, GET /receipts/{id}/image).
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceImageUploadTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-09-30T18:30:00Z");

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private ReceiptLineItemRepository lineItemRepository;

    @Mock
    private ImageStorageService imageStorageService;

    private ReceiptService service;

    private final MockMultipartFile image =
            new MockMultipartFile("image", "import.png", "image/png", "bytes".getBytes());

    @BeforeEach
    void setUp() {
        service = new ReceiptService(receiptRepository, lineItemRepository, new ReceiptMapperImpl(),
                imageStorageService);
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptSource.class, names = {"CAMERA", "IMAGE_IMPORT"})
    void uploadStoresTheImageAndSavesAPendingReceiptWithTheGivenSource(ReceiptSource source) {
        when(imageStorageService.store(image, CAPTURED_AT)).thenReturn(new StoredImage("2026/09/abc.png"));

        ReceiptSummary summary = service.uploadImageReceipt(image, CAPTURED_AT, source);

        ArgumentCaptor<Receipt> saved = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(saved.capture());
        Receipt receipt = saved.getValue();
        assertThat(receipt.getSource()).isEqualTo(source);
        assertThat(receipt.getStatus()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(receipt.getImagePath()).isEqualTo("2026/09/abc.png");
        assertThat(receipt.getCapturedAt()).isEqualTo(CAPTURED_AT);

        assertThat(summary.source()).isEqualTo(source);
        assertThat(summary.status()).isEqualTo(ReceiptStatus.PENDING);
        assertThat(summary.capturedAt()).isEqualTo(CAPTURED_AT);
    }

    @ParameterizedTest
    @EnumSource(value = ReceiptSource.class, names = {"CAMERA", "IMAGE_IMPORT"})
    void missingCapturedAtDefaultsToNowForBothSources(ReceiptSource source) {
        when(imageStorageService.store(eq(image), any(Instant.class))).thenReturn(new StoredImage("2026/10/abc.png"));
        Instant before = Instant.now();

        service.uploadImageReceipt(image, null, source);

        Instant after = Instant.now();
        ArgumentCaptor<Instant> storedAt = ArgumentCaptor.forClass(Instant.class);
        verify(imageStorageService).store(eq(image), storedAt.capture());
        ArgumentCaptor<Receipt> saved = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(saved.capture());
        assertThat(storedAt.getValue()).isBetween(before, after);
        // the image is filed under the same instant the receipt is stamped with
        assertThat(saved.getValue().getCapturedAt()).isEqualTo(storedAt.getValue());
    }

    @Test
    void anUnsupportedImageTypeSavesNoReceipt() {
        when(imageStorageService.store(any(), any()))
                .thenThrow(new UnsupportedImageTypeException("unsupported image content type 'image/heic'"));

        assertThatThrownBy(() -> service.uploadImageReceipt(image, CAPTURED_AT, ReceiptSource.IMAGE_IMPORT))
                .isInstanceOf(UnsupportedImageTypeException.class);

        verifyNoInteractions(receiptRepository);
    }

    @Test
    void listPendingCarriesEachReceiptsSource() {
        Receipt camera = Receipt.newImageUpload(ReceiptSource.CAMERA, "2026/09/a.jpg", CAPTURED_AT);
        camera.setId(1L);
        Receipt imported = Receipt.newImageUpload(ReceiptSource.IMAGE_IMPORT, "2026/09/b.png", CAPTURED_AT);
        imported.setId(2L);
        when(receiptRepository.findAllByStatusOrderByCapturedAtAsc(ReceiptStatus.PENDING))
                .thenReturn(List.of(camera, imported));

        assertThat(service.listPending().data()).containsExactly(
                new PendingReceiptRef(1L, ReceiptSource.CAMERA),
                new PendingReceiptRef(2L, ReceiptSource.IMAGE_IMPORT));
    }

    @Test
    void getImageLoadsTheStoredPathOfAnImportedReceipt() {
        Receipt imported = Receipt.newImageUpload(ReceiptSource.IMAGE_IMPORT, "2026/09/b.png", CAPTURED_AT);
        when(receiptRepository.findById(2L)).thenReturn(Optional.of(imported));
        LoadedImage loaded = new LoadedImage(new ByteArrayResource("png".getBytes()), MediaType.IMAGE_PNG);
        when(imageStorageService.load("2026/09/b.png")).thenReturn(loaded);

        assertThat(service.getImage(2L)).isSameAs(loaded);
    }

    @Test
    void getImageOfAReceiptWithoutAnImageIsNotFound() {
        Receipt manual = Receipt.newManualEntry(CAPTURED_AT, "PGE");
        when(receiptRepository.findById(5L)).thenReturn(Optional.of(manual));

        assertThatThrownBy(() -> service.getImage(5L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("receipt 5 has no image");
        verifyNoInteractions(imageStorageService);
    }
}
