import { useMutation, useQueryClient } from "@tanstack/react-query";
import { uploadReceipt } from "@/lib/api";
import { receiptsKeys } from "@/lib/queryKeys";

export function useUploadReceipt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ image, capturedAt }: { image: File; capturedAt?: Date }) =>
      uploadReceipt(image, capturedAt),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.all });
    },
  });
}
