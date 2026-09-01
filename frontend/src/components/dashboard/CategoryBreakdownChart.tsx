import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { formatCurrency } from "@/lib/utils";
import type { CategoryAmount, CategoryInfo } from "@/lib/types";

interface ChartRow {
  code: string;
  label: string;
  amount: number;
}

interface CategoryBreakdownChartProps {
  categories: CategoryInfo[];
  amounts: CategoryAmount[];
}

function BreakdownTooltip({ active, payload }: { active?: boolean; payload?: { payload: ChartRow }[] }) {
  if (!active || !payload?.length) return null;
  const row = payload[0]?.payload;
  if (!row) return null;
  return (
    <div className="rounded-lg border border-border bg-popover px-3 py-2 text-sm shadow-md">
      <p className="font-medium text-foreground">{row.label}</p>
      <p className="tabular-nums text-muted-foreground">{formatCurrency(row.amount)}</p>
    </div>
  );
}

/**
 * Single-series horizontal bar chart: every bar shares the same categorical hue (dataviz
 * skill: identity is already carried by the axis label, so an 11-way category comparison
 * doesn't need — and per the skill's 8-hue categorical cap, can't safely have — a distinct
 * color per bar). Category order always follows GET /api/categories, never re-sorted by value.
 */
export function CategoryBreakdownChart({ categories, amounts }: CategoryBreakdownChartProps) {
  const amountByCode = new Map(amounts.map((a) => [a.category, a.amount]));
  const rows: ChartRow[] = categories.map((c) => ({
    code: c.code,
    label: c.label,
    amount: amountByCode.get(c.code) ?? 0,
  }));

  return (
    <div className="viz-root">
      <div style={{ height: rows.length * 34 + 16 }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 44, bottom: 4, left: 4 }}>
            <CartesianGrid horizontal={false} stroke="var(--chart-grid)" />
            <XAxis type="number" hide />
            <YAxis
              type="category"
              dataKey="label"
              width={132}
              tickLine={false}
              axisLine={{ stroke: "var(--chart-baseline)" }}
              tick={{ fill: "var(--chart-text-secondary)", fontSize: 12 }}
            />
            <Tooltip content={<BreakdownTooltip />} cursor={{ fill: "var(--chart-grid)" }} />
            <Bar dataKey="amount" fill="var(--chart-series-1)" barSize={20} radius={[0, 4, 4, 0]}>
              <LabelList
                dataKey="amount"
                position="right"
                formatter={(label) => formatCurrency(Number(label ?? 0))}
                style={{ fill: "var(--chart-text-primary)", fontSize: 11, fontWeight: 600 }}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <details className="mt-2">
        <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
          Pokaż jako tabelę
        </summary>
        <table className="mt-2 w-full text-sm">
          <caption className="sr-only">Wydatki na kategorię w wybranym miesiącu</caption>
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th scope="col" className="py-1 font-medium">
                Kategoria
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                Kwota
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.code} className="border-b border-border last:border-0">
                <td className="py-1.5">{row.label}</td>
                <td className="py-1.5 text-right tabular-nums">{formatCurrency(row.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
