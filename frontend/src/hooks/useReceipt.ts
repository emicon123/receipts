import { useQuery } from "@tanstack/react-query";
import { getReceipt } from "@/lib/api";
import { receiptsKeys } from "@/lib/queryKeys";

export function useReceipt(id: number) {
  return useQuery({
    queryKey: receiptsKeys.detail(id),
    queryFn: () => getReceipt(id),
    enabled: Number.isInteger(id) && id > 0,
  });
}
