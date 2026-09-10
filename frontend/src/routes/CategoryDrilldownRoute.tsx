import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { AppShell } from "@/components/layout/AppShell";
import { CategoryLineItemGroups } from "@/components/dashboard/CategoryLineItemGroups";
import { useCategories } from "@/hooks/useCategories";
import { useSpendingLineItems } from "@/hooks/useSpendingLineItems";
import { formatCurrency, formatMonthLong } from "@/lib/utils";
import type { SpendCategory } from "@/lib/types";

/**
 * Category drill-down (ADR-010) — what actually makes up one category's monthly total, grouped
 * by subcategory -> subSubcategory. Reached by clicking a bar/row in CategoryBreakdownChart;
 * linkable and back-button-friendly via a real route + query params rather than in-page state.
 */
export function CategoryDrilldownRoute() {
  const params = useParams<{ category: string }>();
  const [searchParams] = useSearchParams();
  const year = Number(searchParams.get("year"));
  const month = Number(searchParams.get("month"));

  const { data: categories, isPending: categoriesPending, isError: categoriesError } = useCategories();

  const validRange =
    Number.isInteger(year) &&
    year >= 2000 &&
    year <= 2100 &&
    Number.isInteger(month) &&
    month >= 1 &&
    month <= 12;

  const categoryInfo = categories?.find((c) => c.code === params.category);
  // Category param is only ever real once `categoryInfo` resolves; until then this is a throwaway
  // value gated off by `enabled` below so the query never fires against a bogus category.
  const category = (params.category ?? "") as SpendCategory;

  const lineItems = useSpendingLineItems(year, month, category, {
    enabled: validRange && !!categoryInfo,
  });

  // Redirect once we know for sure the route params don't resolve to a real category/month —
  // never before `categories` has actually loaded, or every direct link would bounce instantly.
  const invalidRoute = !validRange || (!categoriesPending && !categoriesError && !categoryInfo);
  if (invalidRoute) {
    return <Navigate to="/dashboard" replace />;
  }

  const total = lineItems.data ? lineItems.data.reduce((sum, item) => sum + item.amount, 0) : 0;

  return (
    <AppShell title={categoryInfo?.label ?? "Kategoria"} backTo="/dashboard">
      <div className="flex flex-col gap-4">
        <p className="text-sm capitalize text-muted-foreground">{formatMonthLong(year, month)}</p>

        {(categoriesPending || lineItems.isPending) && (
          <p className="py-8 text-center text-sm text-muted-foreground">Ładowanie…</p>
        )}

        {categoriesError && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            Nie udało się wczytać listy kategorii.
          </p>
        )}

        {lineItems.isError && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            Nie udało się wczytać pozycji dla tej kategorii.
          </p>
        )}

        {lineItems.isSuccess && lineItems.data.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            Brak pozycji w tej kategorii w wybranym miesiącu.
          </p>
        )}

        {lineItems.isSuccess && lineItems.data.length > 0 && (
          <>
            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Razem</p>
              <p className="text-2xl font-semibold tabular-nums">{formatCurrency(total)}</p>
            </div>
            <CategoryLineItemGroups items={lineItems.data} />
          </>
        )}
      </div>
    </AppShell>
  );
}
