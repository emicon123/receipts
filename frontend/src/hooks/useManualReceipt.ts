import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createManualReceipt } from "@/lib/api";
import { receiptsKeys, spendingKeys } from "@/lib/queryKeys";
import type { ManualReceiptRequest } from "@/lib/types";

export function useCreateManualReceipt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: ManualReceiptRequest) => createManualReceipt(body),
    onSuccess: () => {
      // A manual receipt lands PROCESSED immediately, so it affects spend totals too.
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.all });
      void queryClient.invalidateQueries({ queryKey: spendingKeys.all });
    },
  });
}
