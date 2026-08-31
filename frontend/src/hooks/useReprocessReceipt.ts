import { useMutation, useQueryClient } from "@tanstack/react-query";
import { reprocessReceipt } from "@/lib/api";
import { receiptsKeys } from "@/lib/queryKeys";
import type { ReprocessRequest } from "@/lib/types";

export function useReprocessReceipt(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body?: ReprocessRequest) => reprocessReceipt(id, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.detail(id) });
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.all });
    },
  });
}
