import { useQuery } from "@tanstack/react-query";
import { getSpendingSubcategorySummary } from "@/lib/api";
import { spendingKeys } from "@/lib/queryKeys";

/**
 * Subcategory breakdown for the dashboard's "Szczegóły" mode (ADR-013). Lazy by construction:
 * only the "Szczegóły" panel mounts this hook, and `enabled` lets a caller hold it off further.
 * `/spending/summary` stays the only eager dashboard call.
 */
export function useSpendingSubcategorySummary(
  year: number,
  month: number,
  options?: { enabled?: boolean },
) {
  return useQuery({
    queryKey: spendingKeys.subcategorySummary(year, month),
    queryFn: () => getSpendingSubcategorySummary(year, month),
    enabled: options?.enabled ?? true,
  });
}
