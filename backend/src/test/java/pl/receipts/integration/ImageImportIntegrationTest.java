package pl.receipts.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.receipts.config.StorageProperties;
import pl.receipts.entity.Receipt;
import pl.receipts.entity.ReceiptSource;
import pl.receipts.repository.ReceiptRepository;

/**
 * Full-stack tests (real controllers, services, Hibernate and PostgreSQL via Testcontainers, real
 * files on disk) for the IMAGE_IMPORT source (ADR-014): the upload endpoint, the pending list the
 * daily script reads, the image bytes it downloads, and the unchanged classification-batch /
 * reprocess / correction / delete machinery working on an imported receipt exactly as on a
 * camera one. The V3/V4 migrations themselves are covered by {@link ImageImportMigrationTest}.
 *
 * <p>Same isolation rule as SubcategorySpendingSummaryIntegrationTest: the database is a shared
 * JVM-wide singleton, so every receipt that ends up PROCESSED lives in 2029 — a year no other
 * test class writes — and assertions about the pending queue look entries up by id.
 */
@AutoConfigureMockMvc
class ImageImportIntegrationTest extends AbstractIntegrationTest {

    private static final String CAPTURED_AT = "2029-03-15T10:00:00Z";

    /** A real 1x1 PNG, so the stored bytes are a genuine image and not just a labelled placeholder. */
    private static final byte[] PNG_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private StorageProperties storageProperties;

    // ---- POST /api/receipts/image-import ----

    @Test
    void importCreatesAPendingImageImportReceiptWithItsImageOnDisk() throws Exception {
        String body = mockMvc.perform(multipart("/api/receipts/image-import")
                        .file(new MockMultipartFile("image", "import-20290315-101500.png", "image/png", PNG_BYTES))
                        .param("capturedAt", CAPTURED_AT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.source").value("IMAGE_IMPORT"))
                .andExpect(jsonPath("$.data.capturedAt").value(CAPTURED_AT))
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.meta").exists())
                .andReturn().getResponse().getContentAsString();
        long id = idOf(body);

        // Hibernate round-trips the freshly added enum value, and the file landed under the
        // year/month of capturedAt with the extension of the real type.
        Receipt stored = receiptRepository.findById(id).orElseThrow();
        assertThat(stored.getSource()).isEqualTo(ReceiptSource.IMAGE_IMPORT);
        assertThat(stored.getImagePath()).startsWith("2029/03/").endsWith(".png");
        assertThat(Files.readAllBytes(storedFile(stored))).isEqualTo(PNG_BYTES);

        mockMvc.perform(get("/api/receipts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("IMAGE_IMPORT"))
                .andExpect(jsonPath("$.data.imageUrl").value("/api/receipts/" + id + "/image"));
        mockMvc.perform(get("/api/receipts").param("year", "2029").param("month", "3").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].source", id).value("IMAGE_IMPORT"));
    }

    @Test
    void capturedAtDefaultsToTheUploadTimeWhenOmitted() throws Exception {
        Instant before = Instant.now();

        String body = mockMvc.perform(multipart("/api/receipts/image-import")
                        .file(new MockMultipartFile("image", "shot.png", "image/png", PNG_BYTES)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Instant capturedAt = Instant.parse(JsonPath.read(body, "$.data.capturedAt"));
        assertThat(capturedAt).isBetween(before, Instant.now());
    }

    /** ADR-014: each endpoint owns its source; a "source" form field from the client is never read. */
    @Test
    void aClientSuppliedSourceIsIgnoredOnBothUploadEndpoints() throws Exception {
        String imported = mockMvc.perform(multipart("/api/receipts/image-import")
                        .file(new MockMultipartFile("image", "shot.png", "image/png", PNG_BYTES))
                        .param("capturedAt", CAPTURED_AT).param("source", "MANUAL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("IMAGE_IMPORT"))
                .andReturn().getResponse().getContentAsString();
        String camera = mockMvc.perform(multipart("/api/receipts")
                        .file(new MockMultipartFile("image", "r.jpg", "image/jpeg", "jpeg".getBytes()))
                        .param("capturedAt", CAPTURED_AT).param("source", "IMAGE_IMPORT"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.source").value("CAMERA"))
                .andReturn().getResponse().getContentAsString();

        assertThat(receiptRepository.findById(idOf(imported)).orElseThrow().getSource())
                .isEqualTo(ReceiptSource.IMAGE_IMPORT);
        assertThat(receiptRepository.findById(idOf(camera)).orElseThrow().getSource())
                .isEqualTo(ReceiptSource.CAMERA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/heic", "image/gif", "application/pdf"})
    void anUnsupportedImageTypeIsRejectedWith422AndCreatesNothing(String contentType) throws Exception {
        long receiptsBefore = receiptRepository.count();

        mockMvc.perform(multipart("/api/receipts/image-import")
                        .file(new MockMultipartFile("image", "photo.bin", contentType, "bytes".getBytes())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("image"));

        assertThat(receiptRepository.count()).isEqualTo(receiptsBefore);
    }

    @Test
    void aMissingImagePartIsRejectedWith400() throws Exception {
        mockMvc.perform(multipart("/api/receipts/image-import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("image"));
    }

    // ---- GET /api/receipts/pending and GET /api/receipts/{id}/image: what the daily script reads ----

    @Test
    void pendingListExposesTheSourceOfCameraAndImportedReceipts() throws Exception {
        long camera = uploadCamera();
        long imported = importImage("image/png", PNG_BYTES);

        mockMvc.perform(get("/api/receipts/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].source", camera).value("CAMERA"))
                .andExpect(jsonPath("$.data[?(@.id == %d)].source", imported).value("IMAGE_IMPORT"))
                // lean by design: an entry is just {id, source}
                .andExpect(jsonPath("$.data[?(@.id == %d)].imageUrl", imported).isEmpty());
    }

    /** The script derives the temp file's extension from this Content-Type, so it must be exact. */
    @ParameterizedTest
    @ValueSource(strings = {"image/png", "image/jpeg", "image/webp"})
    void anImportedImageIsServedWithItsOwnMediaType(String contentType) throws Exception {
        byte[] bytes = "image/png".equals(contentType) ? PNG_BYTES : ("fake-" + contentType).getBytes();
        long id = importImage(contentType, bytes);

        mockMvc.perform(get("/api/receipts/{id}/image", id))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType(contentType)))
                .andExpect(content().bytes(bytes));
    }

    // ---- POST /api/receipts/classification-batch ----

    @Test
    void classificationBatchProcessesAnImportedReceiptAndIsIdempotent() throws Exception {
        long id = importImage("image/png", PNG_BYTES);
        String batch = """
                {
                  "items": [
                    { "receiptId": %d, "storeName": "Żabka", "capturedAt": "2029-03-16",
                      "lineItems": [
                        { "productName": "Makaron", "category": "JEDZENIE_SREDNIE", "amount": 7.50 },
                        { "productName": "Szampon", "category": "MYCIE_CHEMIA", "amount": 12.49 }
                      ] }
                  ],
                  "failures": []
                }
                """.formatted(id);

        submitBatch(batch).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed[0]").value(id))
                .andExpect(jsonPath("$.data.failed").isEmpty())
                .andExpect(jsonPath("$.data.skipped").isEmpty());
        submitBatch(batch).andExpect(status().isOk()); // the script's retry re-submits the same batch

        mockMvc.perform(get("/api/receipts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.source").value("IMAGE_IMPORT"))
                .andExpect(jsonPath("$.data.storeName").value("Żabka"))
                .andExpect(jsonPath("$.data.capturedAt").value("2029-03-16T10:00:00Z"))
                .andExpect(jsonPath("$.data.totalAmount").value(19.99))
                .andExpect(jsonPath("$.data.lineItems.length()").value(2)) // replaced, not duplicated
                .andExpect(jsonPath("$.data.lineItems[0].corrected").value(false));
        assertNotPending(id);
    }

    @Test
    void classificationBatchFailureMarksAnImportedReceiptFailedWithItsReason() throws Exception {
        long id = importImage("image/png", PNG_BYTES);

        submitBatch("""
                { "items": [], "failures": [
                    { "receiptId": %d, "reason": "screenshot of a chat, not a purchase" } ] }
                """.formatted(id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failed[0]").value(id));

        mockMvc.perform(get("/api/receipts/{id}", id))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.source").value("IMAGE_IMPORT"))
                .andExpect(jsonPath("$.data.failureReason").value("screenshot of a chat, not a purchase"));
        assertNotPending(id);
    }

    /** One bad receipt (here: an out-of-enum category) never costs the rest of the batch. */
    @Test
    void aMixedBatchOfImportedAndCameraReceiptsAppliesEachOneIndependently() throws Exception {
        long goodImport = importImage("image/png", PNG_BYTES);
        long badImport = importImage("image/png", PNG_BYTES);
        long goodCamera = uploadCamera();

        submitBatch("""
                { "items": [
                    { "receiptId": %d, "lineItems": [
                        { "productName": "Mleko", "category": "JEDZENIE_KONIECZNE", "amount": 3.20 } ] },
                    { "receiptId": %d, "lineItems": [
                        { "productName": "Mystery", "category": "NOT_A_CATEGORY", "amount": 9.99 } ] },
                    { "receiptId": %d, "lineItems": [
                        { "productName": "Chleb", "category": "JEDZENIE_SREDNIE", "amount": 4.00 } ] }
                  ], "failures": [] }
                """.formatted(goodImport, badImport, goodCamera))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processed.length()").value(2))
                .andExpect(jsonPath("$.data.failed[0]").value(badImport));

        mockMvc.perform(get("/api/receipts/{id}", goodImport))
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.totalAmount").value(3.20));
        mockMvc.perform(get("/api/receipts/{id}", badImport))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.failureReason").value(containsString("NOT_A_CATEGORY")));
        mockMvc.perform(get("/api/receipts/{id}", goodCamera))
                .andExpect(jsonPath("$.data.status").value("PROCESSED"));
    }

    // ---- POST /api/receipts/{id}/reprocess, PUT .../line-items/{itemId}, DELETE /api/receipts/{id} ----

    @Test
    void reprocessReturnsAFailedImportToThePendingQueueWithItsSource() throws Exception {
        long id = importImage("image/png", PNG_BYTES);
        submitBatch("""
                { "items": [], "failures": [ { "receiptId": %d, "reason": "unreadable" } ] }
                """.formatted(id)).andExpect(status().isOk());

        mockMvc.perform(post("/api/receipts/{id}/reprocess", id)) // FAILED needs no force
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.source").value("IMAGE_IMPORT"))
                .andExpect(jsonPath("$.data.failureReason").doesNotExist());

        mockMvc.perform(get("/api/receipts/pending"))
                .andExpect(jsonPath("$.data[?(@.id == %d)].source", id).value("IMAGE_IMPORT"));
    }

    @Test
    void forceReprocessOfAProcessedImportKeepsAUserCorrectedLineItem() throws Exception {
        long id = importImage("image/png", PNG_BYTES);
        submitBatch("""
                { "items": [ { "receiptId": %d, "lineItems": [
                    { "productName": "Makaron", "category": "JEDZENIE_SREDNIE", "amount": 7.50 },
                    { "productName": "Szampon", "category": "MYCIE_CHEMIA", "amount": 12.49 } ] } ],
                  "failures": [] }
                """.formatted(id)).andExpect(status().isOk());

        // The user re-categorises "Szampon" -> corrected = true.
        String detail = mockMvc.perform(get("/api/receipts/{id}", id)).andReturn().getResponse().getContentAsString();
        List<Integer> shampooIds = JsonPath.read(detail, "$.data.lineItems[?(@.productName == 'Szampon')].id");
        mockMvc.perform(put("/api/receipts/{id}/line-items/{itemId}", id, shampooIds.get(0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"category\": \"RZECZY_LUKSUSOWE\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.corrected").value(true));

        // A PROCESSED receipt needs force=true to go back to the queue.
        mockMvc.perform(post("/api/receipts/{id}/reprocess", id)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/receipts/{id}/reprocess", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"force\": true }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        // The next daily run re-classifies it: only the uncorrected "Makaron" row is replaced.
        submitBatch("""
                { "items": [ { "receiptId": %d, "lineItems": [
                    { "productName": "Chleb", "category": "JEDZENIE_SREDNIE", "amount": 3.00 } ] } ],
                  "failures": [] }
                """.formatted(id)).andExpect(status().isOk());

        mockMvc.perform(get("/api/receipts/{id}", id))
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.lineItems.length()").value(2))
                .andExpect(jsonPath("$.data.lineItems[?(@.productName == 'Szampon')].category")
                        .value("RZECZY_LUKSUSOWE"))
                .andExpect(jsonPath("$.data.lineItems[?(@.productName == 'Szampon')].corrected").value(true))
                .andExpect(jsonPath("$.data.lineItems[?(@.productName == 'Chleb')].corrected").value(false))
                .andExpect(jsonPath("$.data.totalAmount").value(15.49));
    }

    @Test
    void deleteRemovesTheImportedReceiptAndItsImageFile() throws Exception {
        long id = importImage("image/png", PNG_BYTES);
        Path file = storedFile(receiptRepository.findById(id).orElseThrow());
        assertThat(file).exists();

        mockMvc.perform(delete("/api/receipts/{id}", id)).andExpect(status().isNoContent());

        assertThat(receiptRepository.existsById(id)).isFalse();
        assertThat(file).doesNotExist();
        mockMvc.perform(get("/api/receipts/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/receipts/{id}/image", id)).andExpect(status().isNotFound());
        assertNotPending(id);
    }

    // ---- helpers ----

    private long importImage(String contentType, byte[] bytes) throws Exception {
        return idOf(mockMvc.perform(multipart("/api/receipts/image-import")
                        .file(new MockMultipartFile("image", "import.bin", contentType, bytes))
                        .param("capturedAt", CAPTURED_AT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private long uploadCamera() throws Exception {
        return idOf(mockMvc.perform(multipart("/api/receipts")
                        .file(new MockMultipartFile("image", "r.jpg", "image/jpeg", "jpeg".getBytes()))
                        .param("capturedAt", CAPTURED_AT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    private ResultActions submitBatch(String json) throws Exception {
        return mockMvc.perform(post("/api/receipts/classification-batch")
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void assertNotPending(long id) throws Exception {
        mockMvc.perform(get("/api/receipts/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)]", id).isEmpty());
    }

    private static long idOf(String responseBody) {
        return ((Number) JsonPath.read(responseBody, "$.data.id")).longValue();
    }

    private Path storedFile(Receipt receipt) {
        return Path.of(storageProperties.rootPath()).toAbsolutePath().normalize().resolve(receipt.getImagePath());
    }
}
