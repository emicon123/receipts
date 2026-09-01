package pl.receipts.service;

import java.util.List;
import org.springframework.stereotype.Service;
import pl.receipts.dto.category.CategoryInfo;
import pl.receipts.entity.SpendCategory;

/**
 * The single rendering of CLAUDE.md § Categories in the running system's API surface (ADR-005 —
 * a fixed enum, not a DB-backed lookup table, so this list is hard-coded here rather than
 * queried). Canonical order matches {@link SpendCategory}'s declaration order, which is also the
 * order GET /categories, the spending-summary zero-fill, and GET /categories all use.
 */
@Service
public class CategoryCatalogService {

    private static final List<CategoryInfo> CATEGORIES = List.of(
            new CategoryInfo(SpendCategory.ALKO, "Alko", "Alkohol"),
            new CategoryInfo(SpendCategory.JEDZENIE_KONIECZNE, "Jedzenie konieczne", "Zdrowa żywność"),
            new CategoryInfo(SpendCategory.JEDZENIE_SREDNIE, "Jedzenie średnie", "Jedzenie neutralne"),
            new CategoryInfo(SpendCategory.JEDZENIE_PIERDOLOWATE, "Jedzenie pierdołowate", "Niezdrowe jedzenie"),
            new CategoryInfo(SpendCategory.RZECZY_PALIWO_INNE_ROZNE, "Rzeczy/paliwo/inne/różne",
                    "Rzeczy ogólne, paliwo, różne"),
            new CategoryInfo(SpendCategory.RZECZY_LUKSUSOWE, "Rzeczy luksusowe", "Rzeczy luksusowe"),
            new CategoryInfo(SpendCategory.MYCIE_CHEMIA, "Mycie/chemia", "Sprzątanie i chemia"),
            new CategoryInfo(SpendCategory.ROZRYWKA_RESTAURACJE, "Rozrywka/restauracje",
                    "Rozrywka i jedzenie na mieście"),
            new CategoryInfo(SpendCategory.RACHUNKI, "Rachunki", "Rachunki"),
            new CategoryInfo(SpendCategory.BOBINEK, "Bobinek", "Rzeczy dla dziecka"),
            new CategoryInfo(SpendCategory.SUPLE, "Suple", "Suplementy")
    );

    public List<CategoryInfo> listAll() {
        return CATEGORIES;
    }

    public List<SpendCategory> canonicalOrder() {
        return CATEGORIES.stream().map(CategoryInfo::code).toList();
    }
}
