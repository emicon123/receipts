import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { AppShell } from "@/components/layout/AppShell";
import { CategoryBreakdownChart } from "@/components/dashboard/CategoryBreakdownChart";
import { CategoryTrendGrid } from "@/components/dashboard/CategoryTrendGrid";
import { MonthPicker } from "@/components/dashboard/MonthPicker";
import { SubcategoryBreakdownPanel } from "@/components/dashboard/SubcategoryBreakdownPanel";
import { YearPicker } from "@/components/dashboard/YearPicker";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useCategories } from "@/hooks/useCategories";
import { useSpendingSummary } from "@/hooks/useSpendingSummary";
import { useSpendingTrend } from "@/hooks/useSpendingTrend";
import { formatCurrency } from "@/lib/utils";
import type { SpendCategory } from "@/lib/types";

const today = new Date();

/** Summary-tab view, kept in the URL (`?widok=szczegoly`) so a reload or browser-back from the
 * drill-down restores it. "Kategorie" (plain bars) is the default and has no param. */
type SummaryView = "kategorie" | "szczegoly";
const VIEW_PARAM = "widok";

function parseSummaryView(value: string | null): SummaryView {
  return value === "szczegoly" ? "szczegoly" : "kategorie";
}

export function DashboardRoute() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const summaryView = parseSummaryView(searchParams.get(VIEW_PARAM));

  function setSummaryView(view: string) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (view === "szczegoly") next.set(VIEW_PARAM, "szczegoly");
        else next.delete(VIEW_PARAM);
        return next;
      },
      { replace: true },
    );
  }
  const [summaryYear, setSummaryYear] = useState(today.getFullYear());
  const [summaryMonth, setSummaryMonth] = useState(today.getMonth() + 1);
  const [trendYear, setTrendYear] = useState(today.getFullYear());

  function goToCategoryDrilldown(category: SpendCategory) {
    navigate(
      `/dashboard/category/${encodeURIComponent(category)}?year=${summaryYear}&month=${summaryMonth}`,
    );
  }

  const { data: categories, isPending: categoriesPending } = useCategories();
  const summary = useSpendingSummary(summaryYear, summaryMonth);
  const trend = useSpendingTrend(trendYear);

  return (
    <AppShell title="Wydatki">
      <Tabs defaultValue="summary">
        <TabsList className="w-full">
          <TabsTrigger value="summary">Podsumowanie</TabsTrigger>
          <TabsTrigger value="trend">Trend</TabsTrigger>
        </TabsList>

        <TabsContent value="summary" className="flex flex-col gap-4">
          <MonthPicker
            year={summaryYear}
            month={summaryMonth}
            onChange={(y, m) => {
              setSummaryYear(y);
              setSummaryMonth(m);
            }}
          />

          {summary.isPending || categoriesPending ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Ładowanie…</p>
          ) : summary.isError || !categories ? (
            <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
              Nie udało się wczytać wydatków za ten miesiąc.
            </p>
          ) : (
            <>
              <div>
                <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                  Łączne wydatki
                </p>
                <p className="text-3xl font-semibold tabular-nums">
                  {formatCurrency(summary.data.totalAmount)}
                </p>
              </div>
              <Tabs value={summaryView} onValueChange={setSummaryView}>
                <TabsList aria-label="Widok podsumowania" className="h-9 w-full">
                  <TabsTrigger value="kategorie" className="py-1 text-xs">
                    Kategorie
                  </TabsTrigger>
                  <TabsTrigger value="szczegoly" className="py-1 text-xs">
                    Szczegóły
                  </TabsTrigger>
                </TabsList>
                <TabsContent value="kategorie" className="mt-3">
                  <CategoryBreakdownChart
                    categories={categories}
                    amounts={summary.data.categories}
                    onCategorySelect={goToCategoryDrilldown}
                  />
                </TabsContent>
                <TabsContent value="szczegoly" className="mt-3">
                  <SubcategoryBreakdownPanel
                    year={summaryYear}
                    month={summaryMonth}
                    categories={categories}
                    onCategorySelect={goToCategoryDrilldown}
                  />
                </TabsContent>
              </Tabs>
            </>
          )}
        </TabsContent>

        <TabsContent value="trend" className="flex flex-col gap-4">
          <YearPicker year={trendYear} onChange={setTrendYear} />

          {trend.isPending || categoriesPending ? (
            <p className="py-8 text-center text-sm text-muted-foreground">Ładowanie…</p>
          ) : trend.isError || !categories ? (
            <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
              Nie udało się wczytać trendu rocznego.
            </p>
          ) : (
            <CategoryTrendGrid categories={categories} months={trend.data.months} />
          )}
        </TabsContent>
      </Tabs>
    </AppShell>
  );
}
