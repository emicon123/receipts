import { useMutation, useQueryClient } from "@tanstack/react-query";
import { correctLineItem } from "@/lib/api";
import { receiptsKeys, spendingKeys } from "@/lib/queryKeys";
import type { LineItemCorrectionRequest } from "@/lib/types";

export function useCorrectLineItem(receiptId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      itemId,
      body,
    }: {
      itemId: number;
      body: LineItemCorrectionRequest;
    }) => correctLineItem(receiptId, itemId, body),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.detail(receiptId) });
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.all });
      // A corrected amount/category changes recomputed totals shown on the dashboard.
      void queryClient.invalidateQueries({ queryKey: spendingKeys.all });
    },
  });
}
