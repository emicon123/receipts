package pl.receipts.unit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pl.receipts.controller.CategoryController;
import pl.receipts.service.CategoryCatalogService;

/** No service mock — CategoryCatalogService is a plain hard-coded @Service with no dependencies
 * of its own (@WebMvcTest excludes @Service beans by default, so it's explicitly imported here
 * rather than mocked, since exercising the real canonical-order data is the point of this test). */
@WebMvcTest(CategoryController.class)
@Import(CategoryCatalogService.class)
class CategoryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsAllElevenCategoriesInCanonicalOrder() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(11))
                .andExpect(jsonPath("$.data[0].code").value("ALKO"))
                .andExpect(jsonPath("$.data[0].label").value("Alko"));
    }
}
