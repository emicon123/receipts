import { useQuery } from "@tanstack/react-query";
import { getSpendingTrend } from "@/lib/api";
import { spendingKeys } from "@/lib/queryKeys";

export function useSpendingTrend(year: number) {
  return useQuery({
    queryKey: spendingKeys.trend(year),
    queryFn: () => getSpendingTrend(year),
  });
}
