import { useQuery } from "@tanstack/react-query";
import { getSpendingLineItems } from "@/lib/api";
import { spendingKeys } from "@/lib/queryKeys";
import type { SpendCategory } from "@/lib/types";

/**
 * Category drill-down data (ADR-010) — fetched lazily only when the user clicks a category bar
 * on the dashboard, never eagerly alongside `useSpendingSummary`. `enabled` lets the caller hold
 * off fetching until it has a resolved, valid category (see CategoryDrilldownRoute).
 */
export function useSpendingLineItems(
  year: number,
  month: number,
  category: SpendCategory,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: spendingKeys.lineItems(year, month, category),
    queryFn: () => getSpendingLineItems(year, month, category),
    enabled: options?.enabled ?? true,
  });
}
