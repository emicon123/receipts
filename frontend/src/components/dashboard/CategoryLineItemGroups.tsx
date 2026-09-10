import { CategoryLineItemRow } from "@/components/dashboard/CategoryLineItemRow";
import { formatCurrency } from "@/lib/utils";
import type { SpendingLineItem } from "@/lib/types";

/** Fallback bucket for a null/blank subcategory or subSubcategory — historical line items
 * predating ADR-010 never get one (no backfill), and future entries may still omit it. */
const UNGROUPED_LABEL = "Inne";

interface SubSubcategoryGroup {
  label: string;
  items: SpendingLineItem[];
  total: number;
}

interface SubcategoryGroup {
  label: string;
  total: number;
  subgroups: SubSubcategoryGroup[];
}

function normalizeLabel(value: string | null | undefined): string {
  const trimmed = value?.trim();
  return trimmed ? trimmed : UNGROUPED_LABEL;
}

/** Highest-spend group first; "Inne" always sinks to the bottom regardless of its total, since
 * it's a catch-all rather than a meaningful grouping the user chose to compare against others. */
function byTotalDesc(a: { label: string; total: number }, b: { label: string; total: number }): number {
  if (a.label === UNGROUPED_LABEL && b.label !== UNGROUPED_LABEL) return 1;
  if (b.label === UNGROUPED_LABEL && a.label !== UNGROUPED_LABEL) return -1;
  return b.total - a.total;
}

function sum(items: SpendingLineItem[]): number {
  return items.reduce((total, item) => total + item.amount, 0);
}

function groupItems(items: SpendingLineItem[]): SubcategoryGroup[] {
  const bySubcategory = new Map<string, SpendingLineItem[]>();
  for (const item of items) {
    const key = normalizeLabel(item.subcategory);
    const bucket = bySubcategory.get(key);
    if (bucket) bucket.push(item);
    else bySubcategory.set(key, [item]);
  }

  const groups = Array.from(bySubcategory.entries()).map(([label, subcategoryItems]) => {
    const bySubSubcategory = new Map<string, SpendingLineItem[]>();
    for (const item of subcategoryItems) {
      const key = normalizeLabel(item.subSubcategory);
      const bucket = bySubSubcategory.get(key);
      if (bucket) bucket.push(item);
      else bySubSubcategory.set(key, [item]);
    }

    const subgroups: SubSubcategoryGroup[] = Array.from(bySubSubcategory.entries())
      .map(([subLabel, subSubcategoryItems]) => ({
        label: subLabel,
        items: subSubcategoryItems,
        total: sum(subSubcategoryItems),
      }))
      .sort(byTotalDesc);

    return { label, subgroups, total: sum(subcategoryItems) };
  });

  return groups.sort(byTotalDesc);
}

export function CategoryLineItemGroups({ items }: { items: SpendingLineItem[] }) {
  const groups = groupItems(items);

  return (
    <div className="flex flex-col gap-5">
      {groups.map((group) => (
        <section key={group.label}>
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
              const showSubheading = !(group.label === UNGROUPED_LABEL && subgroup.label === UNGROUPED_LABEL);
              return (
                <div key={subgroup.label}>
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
