import { CategoryLineItemRow } from "@/components/dashboard/CategoryLineItemRow";
import { formatCurrency } from "@/lib/utils";
import type { SpendingLineItem } from "@/lib/types";

/** Fallback bucket for a null/blank subcategory or subSubcategory — historical line items
 * predating ADR-010 never get one (no backfill), and future entries may still omit it. */
const UNGROUPED_LABEL = "Inne";

interface SubSubcategoryGroup {
  key: string;
  label: string;
  items: SpendingLineItem[];
  total: number;
}

interface SubcategoryGroup {
  key: string;
  label: string;
  total: number;
  subgroups: SubSubcategoryGroup[];
}

/** Grouping key for the null/blank bucket — distinct from every real key (those are non-empty),
 * so a label literally spelled "Inne" never merges with the ungrouped bucket by accident. */
const UNGROUPED_KEY = "";

/** Same normalization as the server's ADR-010 §5 / ADR-013 rule: group by `lower(trim())`, so
 * the drill-down's groups match the "Szczegóły" stacked bar's segments. */
function groupKey(value: string | null | undefined): string {
  return value?.trim().toLowerCase() ?? UNGROUPED_KEY;
}

/**
 * Display label for one normalized group: the most-used exact (trimmed) spelling among its
 * items, tie-broken by the most recent `capturedAt`, then by the spelling ascending — the
 * ADR-010 §5 casing rule, applied to the drill-down's own (single category/month) slice.
 */
function displayLabel(key: string, items: SpendingLineItem[], pick: (item: SpendingLineItem) => string | null | undefined): string {
  if (key === UNGROUPED_KEY) return UNGROUPED_LABEL;
  const variants = new Map<string, { count: number; latest: number }>();
  for (const item of items) {
    const variant = pick(item)?.trim();
    if (!variant) continue;
    const capturedAt = Date.parse(item.capturedAt) || 0;
    const stats = variants.get(variant);
    if (stats) {
      stats.count += 1;
      stats.latest = Math.max(stats.latest, capturedAt);
    } else {
      variants.set(variant, { count: 1, latest: capturedAt });
    }
  }
  const [best] = Array.from(variants.entries()).sort(
    ([aVariant, a], [bVariant, b]) =>
      b.count - a.count || b.latest - a.latest || (aVariant < bVariant ? -1 : aVariant > bVariant ? 1 : 0),
  );
  return best?.[0] ?? UNGROUPED_LABEL;
}

/** Highest-spend group first; "Inne" always sinks to the bottom regardless of its total, since
 * it's a catch-all rather than a meaningful grouping the user chose to compare against others. */
function byTotalDesc(a: { key: string; total: number }, b: { key: string; total: number }): number {
  if (a.key === UNGROUPED_KEY && b.key !== UNGROUPED_KEY) return 1;
  if (b.key === UNGROUPED_KEY && a.key !== UNGROUPED_KEY) return -1;
  return b.total - a.total;
}

function sum(items: SpendingLineItem[]): number {
  return items.reduce((total, item) => total + item.amount, 0);
}

function bucketBy(
  items: SpendingLineItem[],
  pick: (item: SpendingLineItem) => string | null | undefined,
): Map<string, SpendingLineItem[]> {
  const buckets = new Map<string, SpendingLineItem[]>();
  for (const item of items) {
    const key = groupKey(pick(item));
    const bucket = buckets.get(key);
    if (bucket) bucket.push(item);
    else buckets.set(key, [item]);
  }
  return buckets;
}

const pickSubcategory = (item: SpendingLineItem) => item.subcategory;
const pickSubSubcategory = (item: SpendingLineItem) => item.subSubcategory;

function groupItems(items: SpendingLineItem[]): SubcategoryGroup[] {
  const groups = Array.from(bucketBy(items, pickSubcategory).entries()).map(
    ([key, subcategoryItems]) => {
      const subgroups: SubSubcategoryGroup[] = Array.from(
        bucketBy(subcategoryItems, pickSubSubcategory).entries(),
      )
        .map(([subKey, subSubcategoryItems]) => ({
          key: subKey,
          label: displayLabel(subKey, subSubcategoryItems, pickSubSubcategory),
          items: subSubcategoryItems,
          total: sum(subSubcategoryItems),
        }))
        .sort(byTotalDesc);

      return {
        key,
        label: displayLabel(key, subcategoryItems, pickSubcategory),
        subgroups,
        total: sum(subcategoryItems),
      };
    },
  );

  return groups.sort(byTotalDesc);
}

export function CategoryLineItemGroups({ items }: { items: SpendingLineItem[] }) {
  const groups = groupItems(items);

  return (
    <div className="flex flex-col gap-5">
      {groups.map((group) => (
        <section key={group.key}>
          <div className="mb-2 flex items-baseline justify-between">
            <h2 className="text-sm font-semibold">{group.label}</h2>
            <p className="text-sm font-medium tabular-nums text-muted-foreground">
              {formatCurrency(group.total)}
            </p>
          </div>

          <div className="flex flex-col gap-3">
            {group.subgroups.map((subgroup) => {
              // Skip a redundant "Inne" sub-heading directly under an "Inne" group — that's
              // just the fully-ungrouped (both fields null) case, not two distinct buckets.
              const showSubheading = !(group.key === UNGROUPED_KEY && subgroup.key === UNGROUPED_KEY);
              return (
                <div key={subgroup.key}>
                  {showSubheading && (
                    <div className="mb-1.5 flex items-baseline justify-between px-1">
                      <h3 className="text-xs font-medium text-muted-foreground">{subgroup.label}</h3>
                      <p className="text-xs tabular-nums text-muted-foreground">
                        {formatCurrency(subgroup.total)}
                      </p>
                    </div>
                  )}
                  <ul className="flex flex-col gap-2">
                    {subgroup.items.map((item) => (
                      <CategoryLineItemRow key={item.id} item={item} />
                    ))}
                  </ul>
                </div>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}
