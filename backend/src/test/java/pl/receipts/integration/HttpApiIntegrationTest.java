package pl.receipts.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full-stack HTTP tests (real controllers, real services, real PostgreSQL via Testcontainers) —
 * these exist specifically to verify GlobalExceptionHandler's 400 vs 422 vs 404 split actually
 * fires end-to-end, not just by construction; the @WebMvcTest slices verify controller wiring in
 * isolation with a mocked service layer, which can't catch a mismatch between what a real service
 * throws and what the advice maps it to.
 */
@AutoConfigureMockMvc
class HttpApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void uploadCameraReceiptReturns201Pending() throws Exception {
        var file = new MockMultipartFile("image", "r.jpg", "image/jpeg", "bytes".getBytes());

        mockMvc.perform(multipart("/api/receipts").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.source").value("CAMERA"));
    }

    @Test
    void manualEntryHappyPathReturns201Processed() throws Exception {
        mockMvc.perform(post("/api/receipts/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capturedAt": "2026-08-20T09:00:00Z",
                                  "storeName": "PGE",
                                  "lineItems": [
                                    { "productName": "Rachunek za prad", "category": "RACHUNKI", "amount": 150.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.totalAmount").value(150.00));
    }

    @Test
    void manualEntryWithInvalidCategoryReturns422() throws Exception {
        mockMvc.perform(post("/api/receipts/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capturedAt": "2026-08-20T09:00:00Z",
                                  "lineItems": [
                                    { "productName": "Mystery item", "category": "NOT_REAL", "amount": 10.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].field").value("lineItems[0].category"));
    }

    @Test
    void manualEntryWithEmptyLineItemsReturns422() throws Exception {
        mockMvc.perform(post("/api/receipts/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "capturedAt": "2026-08-20T09:00:00Z", "lineItems": [] }
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void manualEntryWithUnreadableBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/receipts/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not valid json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUnknownReceiptReturns404() throws Exception {
        mockMvc.perform(get("/api/receipts/{id}", 987_654_321L))
                .andExpect(status().isNotFound());
    }

    @Test
    void classificationBatchWithMissingFailuresKeyReturns400() throws Exception {
        mockMvc.perform(post("/api/receipts/classification-batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void spendingSummaryZeroFillsViaHttp() throws Exception {
        mockMvc.perform(get("/api/spending/summary").param("year", "2018").param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories.length()").value(11))
                .andExpect(jsonPath("$.data.totalAmount").value(0));
    }

    @Test
    void categoriesEndpointReturnsElevenFixedCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11));
    }

    /** ADR-010 §3 — manual entry accepts subcategory/subSubcategory and echoes them back. */
    @Test
    void manualEntryWithSubcategoryFieldsPersistsThem() throws Exception {
        mockMvc.perform(post("/api/receipts/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capturedAt": "2026-08-20T09:00:00Z",
                                  "storeName": "Zabka",
                                  "lineItems": [
                                    { "productName": "Żelki", "category": "JEDZENIE_PIERDOLOWATE",
                                      "amount": 4.50, "subcategory": "Słodycze", "subSubcategory": "żelki" }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lineItems[0].subcategory").value("Słodycze"))
                .andExpect(jsonPath("$.data.lineItems[0].subSubcategory").value("żelki"));
    }

    /** ADR-010 §3 — GET /spending/line-items, the dashboard's category drill-down endpoint. */
    @Test
    void spendingLineItemsReturnsScopedItemsViaHttp() throws Exception {
        mockMvc.perform(post("/api/receipts/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capturedAt": "2026-05-05T09:00:00Z",
                                  "storeName": "Lidl",
                                  "lineItems": [
                                    { "productName": "Wino", "category": "ALKO", "amount": 25.00 }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/spending/line-items")
                        .param("year", "2026").param("month", "5").param("category", "ALKO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].productName").value("Wino"))
                .andExpect(jsonPath("$.data[0].storeName").value("Lidl"));
    }

    @Test
    void spendingLineItemsMissingCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/spending/line-items").param("year", "2026").param("month", "5"))
                .andExpect(status().isBadRequest());
    }

    /** ADR-010 § Cross-batch label consistency — GET /receipts/subcategory-labels shape. */
    @Test
    void subcategoryLabelsEndpointReturns200WithEnvelope() throws Exception {
        mockMvc.perform(get("/api/receipts/subcategory-labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.requestId").exists());
    }
}
