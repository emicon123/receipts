import { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { CategoryBreakdownChart } from "@/components/dashboard/CategoryBreakdownChart";
import { CategoryTrendGrid } from "@/components/dashboard/CategoryTrendGrid";
import { MonthPicker } from "@/components/dashboard/MonthPicker";
import { YearPicker } from "@/components/dashboard/YearPicker";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useCategories } from "@/hooks/useCategories";
import { useSpendingSummary } from "@/hooks/useSpendingSummary";
import { useSpendingTrend } from "@/hooks/useSpendingTrend";
import { formatCurrency } from "@/lib/utils";

const today = new Date();

export function DashboardRoute() {
  const [summaryYear, setSummaryYear] = useState(today.getFullYear());
  const [summaryMonth, setSummaryMonth] = useState(today.getMonth() + 1);
  const [trendYear, setTrendYear] = useState(today.getFullYear());

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
              <CategoryBreakdownChart categories={categories} amounts={summary.data.categories} />
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
