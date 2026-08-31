import { useMutation, useQueryClient } from "@tanstack/react-query";
import { deleteReceipt } from "@/lib/api";
import { receiptsKeys, spendingKeys } from "@/lib/queryKeys";

export function useDeleteReceipt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => deleteReceipt(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.all });
      void queryClient.invalidateQueries({ queryKey: spendingKeys.all });
    },
  });
}
