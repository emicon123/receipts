import { Fragment, type ReactNode } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  Rectangle,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { BarRectangleItem, BarShapeProps } from "recharts";
import { bucketSubcategories, type Segment } from "@/lib/subcategoryBuckets";
import { formatCurrency } from "@/lib/utils";
import type {
  CategoryInfo,
  CategorySubcategoryBreakdown,
  SpendCategory,
} from "@/lib/types";

/** Stack slots, left to right: up to 4 ranked shades, then "Reszta". Colour belongs to the
 * slot (rank within the bar), never to a subcategory label — ADR-013 §1. */
const SLOTS = ["s0", "s1", "s2", "s3", "rest"] as const;
type SlotKey = (typeof SLOTS)[number];

const SLOT_FILL: Record<SlotKey, string> = {
  s0: "var(--chart-rank-1)",
  s1: "var(--chart-rank-2)",
  s2: "var(--chart-rank-3)",
  s3: "var(--chart-rank-4)",
  rest: "var(--chart-muted)",
};

/** Surface gap between adjacent stacked segments (dataviz mark spec: 2px, no strokes). */
const SEGMENT_GAP = 2;
const MAX_REST_NAMES = 4;
const UNLABELED_LABEL = "Bez podkategorii";

const percentFormatter = new Intl.NumberFormat("pl-PL", {
  style: "percent",
  maximumFractionDigits: 0,
});

type StackedRow = {
  code: SpendCategory;
  label: string;
  total: number;
  segments: Partial<Record<SlotKey, Segment>>;
  /** Right-most non-empty slot — the only one that gets the rounded data-end and no gap. */
  lastSlot: SlotKey | null;
} & Record<SlotKey, number>;

function toRow(category: CategoryInfo, breakdown: CategorySubcategoryBreakdown | undefined): StackedRow {
  const row: StackedRow = {
    code: category.code,
    label: category.label,
    total: breakdown?.totalAmount ?? 0,
    segments: {},
    lastSlot: null,
    s0: 0,
    s1: 0,
    s2: 0,
    s3: 0,
    rest: 0,
  };
  if (!breakdown) return row;

  const { shades, rest } = bucketSubcategories(breakdown);
  for (const shade of shades) {
    const slot = SLOTS[shade.rank];
    if (!slot || slot === "rest") continue;
    row[slot] = shade.amount;
    row.segments[slot] = shade;
  }
  if (rest) {
    row.rest = rest.amount;
    row.segments.rest = rest;
  }
  row.lastSlot = [...SLOTS].reverse().find((slot) => row[slot] > 0) ?? null;
  return row;
}

function isSlotKey(value: unknown): value is SlotKey {
  return typeof value === "string" && (SLOTS as readonly string[]).includes(value);
}

function renderSegmentShape(props: BarShapeProps, slot: SlotKey): ReactNode {
  const row = props.payload as StackedRow | undefined;
  if (!row || props.width <= 0 || props.height <= 0) return null;
  const isLast = row.lastSlot === slot;
  return (
    <Rectangle
      x={props.x}
      y={props.y}
      width={isLast ? props.width : Math.max(props.width - SEGMENT_GAP, 1)}
      height={props.height}
      radius={isLast ? [0, 4, 4, 0] : 0}
      fill={SLOT_FILL[slot]}
      fillOpacity={props.isActive ? 0.75 : 1}
    />
  );
}

/** One module-level shape renderer per slot (not inline in JSX). A custom shape also keeps
 * Recharts from dropping zero-width rects, so the "rest" slot's LabelList still has an anchor
 * at the end of every bar for the total label. */
const SEGMENT_SHAPES: Record<SlotKey, (props: BarShapeProps) => ReactNode> = {
  s0: (props) => renderSegmentShape(props, "s0"),
  s1: (props) => renderSegmentShape(props, "s1"),
  s2: (props) => renderSegmentShape(props, "s2"),
  s3: (props) => renderSegmentShape(props, "s3"),
  rest: (props) => renderSegmentShape(props, "rest"),
};

function RestBreakdown({ segment }: { segment: Extract<Segment, { kind: "rest" }> }) {
  const shown = segment.subcategories.slice(0, MAX_REST_NAMES);
  const hiddenCount = segment.subcategories.length - shown.length;
  return (
    <ul className="mt-1 flex flex-col gap-0.5 text-xs text-muted-foreground">
      {shown.map((entry) => (
        <li key={entry.subcategory} className="flex justify-between gap-3">
          <span className="truncate">{entry.subcategory}</span>
          <span className="tabular-nums">{formatCurrency(entry.amount)}</span>
        </li>
      ))}
      {hiddenCount > 0 && <li>…i {hiddenCount} więcej</li>}
      {segment.unlabeledAmount !== 0 && (
        <li className="flex justify-between gap-3">
          <span className="italic">{UNLABELED_LABEL}</span>
          <span className="tabular-nums">{formatCurrency(segment.unlabeledAmount)}</span>
        </li>
      )}
    </ul>
  );
}

/** Per-segment tooltip (Tooltip shared={false}): names this bar's own segment, since colour
 * encodes rank-within-bar rather than identity and there is deliberately no global legend. */
function SegmentTooltip({
  active,
  payload,
}: {
  active?: boolean;
  payload?: { dataKey?: unknown; payload?: StackedRow }[];
}) {
  if (!active || !payload?.length) return null;
  const entry = payload[0];
  const row = entry?.payload;
  if (!row || !isSlotKey(entry.dataKey)) return null;
  const segment = row.segments[entry.dataKey];
  if (!segment) return null;
  const share = row.total > 0 ? segment.amount / row.total : 0;

  return (
    <div className="max-w-64 rounded-lg border border-border bg-popover px-3 py-2 text-sm shadow-md">
      <p className="text-xs text-muted-foreground">{row.label}</p>
      <p className="font-medium text-foreground">{segment.label}</p>
      <p className="tabular-nums text-muted-foreground">
        {formatCurrency(segment.amount)} · {percentFormatter.format(share)}
      </p>
      {segment.kind === "rest" && <RestBreakdown segment={segment} />}
    </div>
  );
}

function formatTotalLabel(value: unknown): string {
  const amount = Number(value ?? 0);
  return amount === 0 ? "" : formatCurrency(amount);
}

interface SubcategoryStackedChartProps {
  categories: CategoryInfo[];
  breakdowns: CategorySubcategoryBreakdown[];
  /** Same drill-down as the plain chart (ADR-010) — from any segment, or the table fallback. */
  onCategorySelect: (category: SpendCategory) => void;
}

/**
 * "Szczegóły" mode (ADR-013): one horizontal stacked bar per category, split into its top
 * subcategories (ranked shades of one hue) plus a grey "Reszta". Category order and labels
 * always follow GET /api/categories; bar length equals the category total.
 */
export function SubcategoryStackedChart({
  categories,
  breakdowns,
  onCategorySelect,
}: SubcategoryStackedChartProps) {
  const breakdownByCode = new Map(breakdowns.map((b) => [b.category, b]));
  const rows = categories.map((c) => toRow(c, breakdownByCode.get(c.code)));

  function handleSegmentClick(data: BarRectangleItem) {
    const row = data.payload as StackedRow | undefined;
    if (row) onCategorySelect(row.code);
  }

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
            <Tooltip shared={false} content={<SegmentTooltip />} cursor={{ fill: "var(--chart-grid)" }} />
            {SLOTS.map((slot) => (
              <Bar
                key={slot}
                dataKey={slot}
                stackId="subcategories"
                fill={SLOT_FILL[slot]}
                barSize={20}
                cursor="pointer"
                shape={SEGMENT_SHAPES[slot]}
                activeBar={SEGMENT_SHAPES[slot]}
                isAnimationActive={false}
                onClick={handleSegmentClick}
              >
                {slot === "rest" && (
                  <LabelList
                    valueAccessor={(entry) => (entry.payload as StackedRow | undefined)?.total ?? 0}
                    position="right"
                    formatter={formatTotalLabel}
                    style={{ fill: "var(--chart-text-primary)", fontSize: 11, fontWeight: 600 }}
                  />
                )}
              </Bar>
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>

      <details className="mt-2">
        <summary className="cursor-pointer text-xs font-medium text-muted-foreground">
          Pokaż jako tabelę
        </summary>
        <table className="mt-2 w-full text-sm">
          <caption className="sr-only">
            Wydatki na kategorię i podkategorię w wybranym miesiącu
          </caption>
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th scope="col" className="py-1 font-medium">
                Kategoria / podkategoria
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                Kwota
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const breakdown = breakdownByCode.get(row.code);
              return (
                <Fragment key={row.code}>
                  <tr
                    role="link"
                    tabIndex={0}
                    onClick={() => onCategorySelect(row.code)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onCategorySelect(row.code);
                      }
                    }}
                    className="cursor-pointer border-t border-border first:border-0 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset"
                  >
                    <td className="py-1.5 font-medium">{row.label}</td>
                    <td className="py-1.5 text-right font-medium tabular-nums">
                      {formatCurrency(row.total)}
                    </td>
                  </tr>
                  {breakdown?.subcategories.map((entry) => (
                    <tr key={entry.subcategory} className="text-muted-foreground">
                      <td className="py-0.5 pl-4">{entry.subcategory}</td>
                      <td className="py-0.5 text-right tabular-nums">{formatCurrency(entry.amount)}</td>
                    </tr>
                  ))}
                  {breakdown && breakdown.unlabeledAmount !== 0 && (
                    <tr className="text-muted-foreground">
                      <td className="py-0.5 pl-4 italic">{UNLABELED_LABEL}</td>
                      <td className="py-0.5 text-right tabular-nums">
                        {formatCurrency(breakdown.unlabeledAmount)}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </details>
    </div>
  );
}
