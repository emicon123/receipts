package pl.receipts.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.receipts.controller.ReceiptController;
import pl.receipts.dto.receipt.ReceiptSummary;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.entity.ReceiptStatus;
import pl.receipts.exception.ReceiptStateConflictException;
import pl.receipts.service.LineItemCorrectionService;
import pl.receipts.service.ReceiptService;
import pl.receipts.storage.LoadedImage;

@WebMvcTest(ReceiptController.class)
class ReceiptControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receiptService;

    @MockitoBean
    private LineItemCorrectionService lineItemCorrectionService;

    @Test
    void uploadReturns201() throws Exception {
        var file = new MockMultipartFile("image", "r.jpg", "image/jpeg", "bytes".getBytes());
        var summary = new ReceiptSummary(1L, ReceiptStatus.PENDING, ReceiptSource.CAMERA, Instant.now(),
                null, BigDecimal.ZERO, "/api/receipts/1/image", null, Instant.now());
        given(receiptService.uploadCameraReceipt(any(), any())).willReturn(summary);

        mockMvc.perform(multipart("/api/receipts").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void uploadParsesOptionalCapturedAtFormParam() throws Exception {
        // Verifies Spring's default String->Instant conversion actually applies to a multipart
        // text field, not just to a JSON body field — the one part of the upload contract not
        // otherwise exercised by an integration test.
        var file = new MockMultipartFile("image", "r.jpg", "image/jpeg", "bytes".getBytes());
        var summary = new ReceiptSummary(1L, ReceiptStatus.PENDING, ReceiptSource.CAMERA,
                Instant.parse("2026-08-15T10:00:00Z"), null, BigDecimal.ZERO, "/api/receipts/1/image", null,
                Instant.now());
        given(receiptService.uploadCameraReceipt(any(), eq(Instant.parse("2026-08-15T10:00:00Z"))))
                .willReturn(summary);

        mockMvc.perform(multipart("/api/receipts").file(file).param("capturedAt", "2026-08-15T10:00:00Z"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.capturedAt").value("2026-08-15T10:00:00Z"));
    }

    @Test
    void uploadWithoutImagePartReturns400() throws Exception {
        mockMvc.perform(multipart("/api/receipts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].code").exists());
        verifyNoInteractions(receiptService);
    }

    @Test
    void getUnknownReceiptReturns404() throws Exception {
        given(receiptService.getDetail(42L)).willThrow(new NoSuchElementException("receipt 42 not found"));

        mockMvc.perform(get("/api/receipts/{id}", 42))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0].code").value("NOT_FOUND"));
    }

    @Test
    void listPropagatesInvalidQueryParamAs400() throws Exception {
        given(receiptService.list(null, 6, null, 0, 20))
                .willThrow(new pl.receipts.exception.InvalidQueryParamException("month", "month requires year"));

        mockMvc.perform(get("/api/receipts").param("month", "6"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("month"));
    }

    @Test
    void listWithYearOutOfRangeIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(get("/api/receipts").param("year", "1899"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(receiptService);
    }

    @Test
    void correctLineItemWithInvalidCategoryReturns422WithoutCallingService() throws Exception {
        mockMvc.perform(put("/api/receipts/{id}/line-items/{itemId}", 1, 2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "category": "NOT_A_REAL_CATEGORY" }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("category"));
        verifyNoInteractions(lineItemCorrectionService);
    }

    @Test
    void reprocessConflictReturns409() throws Exception {
        given(receiptService.reprocess(eq(5L), anyBoolean()))
                .willThrow(new ReceiptStateConflictException("receipt 5 is PROCESSED; force=true required"));

        mockMvc.perform(post("/api/receipts/{id}/reprocess", 5)).andExpect(status().is4xxClientError());
    }

    @Test
    void getImageStreamsBytesWithContentType() throws Exception {
        var loaded = new LoadedImage(new ByteArrayResource("img-bytes".getBytes()), MediaType.IMAGE_JPEG);
        given(receiptService.getImage(7L)).willReturn(loaded);

        mockMvc.perform(get("/api/receipts/{id}/image", 7))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("img-bytes".getBytes()));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/receipts/{id}", 9)).andExpect(status().isNoContent());
    }
}
