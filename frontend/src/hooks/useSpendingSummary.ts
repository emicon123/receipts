import { useQuery } from "@tanstack/react-query";
import { getSpendingSummary } from "@/lib/api";
import { spendingKeys } from "@/lib/queryKeys";

export function useSpendingSummary(year: number, month: number) {
  return useQuery({
    queryKey: spendingKeys.summary(year, month),
    queryFn: () => getSpendingSummary(year, month),
  });
}
