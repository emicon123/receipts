import { SubcategoryStackedChart } from "@/components/dashboard/SubcategoryStackedChart";
import { useSpendingSubcategorySummary } from "@/hooks/useSpendingSubcategorySummary";
import type { CategoryInfo, SpendCategory } from "@/lib/types";

interface SubcategoryBreakdownPanelProps {
  year: number;
  month: number;
  categories: CategoryInfo[];
  onCategorySelect: (category: SpendCategory) => void;
}

/**
 * "Szczegóły" view body. Owns the lazy GET /spending/subcategory-summary fetch (ADR-013): this
 * component is only mounted while "Szczegóły" is the active view, so the request never fires
 * on a plain dashboard visit.
 */
export function SubcategoryBreakdownPanel({
  year,
  month,
  categories,
  onCategorySelect,
}: SubcategoryBreakdownPanelProps) {
  const breakdown = useSpendingSubcategorySummary(year, month);

  if (breakdown.isPending) {
    return <p className="py-8 text-center text-sm text-muted-foreground">Ładowanie…</p>;
  }

  if (breakdown.isError) {
    return (
      <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
        Nie udało się wczytać podkategorii za ten miesiąc.
      </p>
    );
  }

  const { categories: breakdowns, totalAmount } = breakdown.data;

  if (totalAmount === 0) {
    return (
      <p className="py-8 text-center text-sm text-muted-foreground">
        Brak wydatków w tym miesiącu.
      </p>
    );
  }

  const hasAnyLabel = breakdowns.some((b) => b.subcategories.length > 0);

  return (
    <div className="flex flex-col gap-2">
      {!hasAnyLabel && (
        <p className="text-xs text-muted-foreground">
          Pozycje z tego miesiąca nie mają jeszcze przypisanych podkategorii — całość trafia do
          „Reszta”.
        </p>
      )}
      <SubcategoryStackedChart
        categories={categories}
        breakdowns={breakdowns}
        onCategorySelect={onCategorySelect}
      />
    </div>
  );
}
