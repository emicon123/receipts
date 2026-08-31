import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { listReceipts } from "@/lib/api";
import { receiptsKeys } from "@/lib/queryKeys";
import type { ListReceiptsParams } from "@/lib/types";

export function useReceipts(params: ListReceiptsParams) {
  return useQuery({
    queryKey: receiptsKeys.list(params),
    queryFn: () => listReceipts(params),
    placeholderData: keepPreviousData,
  });
}
