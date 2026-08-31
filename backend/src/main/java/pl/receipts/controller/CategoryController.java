package pl.receipts.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.receipts.dto.category.CategoriesResponse;
import pl.receipts.service.CategoryCatalogService;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryCatalogService categoryCatalogService;

    public CategoryController(CategoryCatalogService categoryCatalogService) {
        this.categoryCatalogService = categoryCatalogService;
    }

    @GetMapping
    public CategoriesResponse list() {
        return new CategoriesResponse(categoryCatalogService.listAll());
    }
}
