import { Bar, BarChart, ResponsiveContainer, Tooltip } from "recharts";
import { formatCurrency, formatMonthShort } from "@/lib/utils";
import type { CategoryInfo, SpendingMonth } from "@/lib/types";

interface CategoryTrendGridProps {
  categories: CategoryInfo[];
  months: SpendingMonth[];
}

interface MonthPoint {
  month: number;
  monthLabel: string;
  amount: number;
}

function TrendTooltip({ active, payload }: { active?: boolean; payload?: { payload: MonthPoint }[] }) {
  if (!active || !payload?.length) return null;
  const point = payload[0]?.payload;
  if (!point) return null;
  return (
    <div className="rounded-md border border-border bg-popover px-2 py-1 text-xs shadow-md">
      <p className="font-medium text-foreground">{point.monthLabel}</p>
      <p className="tabular-nums text-muted-foreground">{formatCurrency(point.amount)}</p>
    </div>
  );
}

/**
 * One mini chart per category (small multiples), each a single-hue series — the dataviz
 * skill's prescribed way to show 11 simultaneous series over time without exceeding the
 * 8-hue categorical palette or overlaying 11 lines/stacked segments on one axis.
 */
export function CategoryTrendGrid({ categories, months }: CategoryTrendGridProps) {
  const series = categories.map((category) => {
    const points: MonthPoint[] = months.map((m) => ({
      month: m.month,
      monthLabel: formatMonthShort(m.month),
      amount: m.categories.find((c) => c.category === category.code)?.amount ?? 0,
    }));
    const yearTotal = points.reduce((sum, p) => sum + p.amount, 0);
    return { category, points, yearTotal };
  });

  return (
    <div className="flex flex-col gap-4">
      {series.map(({ category, points, yearTotal }) => (
        <div key={category.code} className="rounded-lg border border-border bg-card p-3">
          <div className="mb-1 flex items-baseline justify-between gap-2">
            <p className="text-sm font-medium">{category.label}</p>
            <p className="text-xs tabular-nums text-muted-foreground">{formatCurrency(yearTotal)}</p>
          </div>
          <div className="h-16">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={points} margin={{ top: 2, right: 2, bottom: 0, left: 2 }}>
                <Tooltip content={<TrendTooltip />} cursor={{ fill: "var(--chart-grid)" }} />
                <Bar dataKey="amount" fill="var(--chart-series-1)" radius={[2, 2, 0, 0]} maxBarSize={14} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      ))}

      <details>
        <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
          Pokaż jako tabelę
        </summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-[640px] text-xs">
            <caption className="sr-only">Miesięczne wydatki na kategorię w wybranym roku</caption>
            <thead>
              <tr className="border-b border-border text-left text-muted-foreground">
                <th scope="col" className="py-1 pr-2 font-medium">
                  Kategoria
                </th>
                {months.map((m) => (
                  <th key={m.month} scope="col" className="px-1.5 py-1 text-right font-medium">
                    {formatMonthShort(m.month)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {series.map(({ category, points }) => (
                <tr key={category.code} className="border-b border-border last:border-0">
                  <td className="py-1 pr-2">{category.label}</td>
                  {points.map((p) => (
                    <td key={p.month} className="px-1.5 py-1 text-right tabular-nums">
                      {formatCurrency(p.amount)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  );
}
