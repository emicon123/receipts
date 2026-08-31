package pl.receipts.unit;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import pl.receipts.controller.ClassificationController;
import pl.receipts.dto.classification.ClassificationBatchResult;
import pl.receipts.dto.receipt.PendingReceiptRef;
import pl.receipts.dto.receipt.PendingReceiptsResponse;
import pl.receipts.service.ClassificationBatchService;
import pl.receipts.service.ReceiptService;

@WebMvcTest(ClassificationController.class)
class ClassificationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReceiptService receiptService;

    @MockitoBean
    private ClassificationBatchService classificationBatchService;

    @Test
    void pendingReturnsLeanIdList() throws Exception {
        given(receiptService.listPending())
                .willReturn(new PendingReceiptsResponse(java.util.List.of(new PendingReceiptRef(1L))));

        mockMvc.perform(get("/api/receipts/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void batchWithMissingKeysReturns400() throws Exception {
        // "failures" key entirely missing — the one structurally-malformed case this endpoint
        // rejects with a plain 400 rather than tolerating per-entry (docs/openapi.yaml). The
        // actual null-check lives in ClassificationBatchService, so this test verifies the
        // controller correctly deserializes the partial body through to it and that
        // GlobalExceptionHandler maps the resulting exception to 400 — stub the mocked service
        // to reproduce that real behavior for a request shaped this way.
        given(classificationBatchService.submit(
                org.mockito.ArgumentMatchers.argThat(r -> r != null && r.failures() == null)))
                .willThrow(new pl.receipts.exception.MalformedBatchRequestException(
                        "request body must include both 'items' and 'failures'"));

        mockMvc.perform(post("/api/receipts/classification-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wellFormedBatchReturns200WithResult() throws Exception {
        given(classificationBatchService.submit(org.mockito.ArgumentMatchers.any()))
                .willReturn(new ClassificationBatchResult(java.util.List.of(1L), java.util.List.of(), java.util.List.of()));

        mockMvc.perform(post("/api/receipts/classification-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [], \"failures\": [] }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed[0]").value(1));
    }
}
