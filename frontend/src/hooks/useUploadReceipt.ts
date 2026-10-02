import { useMutation, useQueryClient } from "@tanstack/react-query";
import { importReceiptImage, uploadReceipt } from "@/lib/api";
import { receiptsKeys } from "@/lib/queryKeys";
import type { ImageReceiptSource } from "@/lib/types";

export interface UploadReceiptVariables {
  image: File;
  /** Only sent for `CAMERA`; an import never sends it (the server defaults it to now). */
  capturedAt?: Date;
  source: ImageReceiptSource;
}

/** One mutation for every capture path; only the endpoint differs by `source` (ADR-014). */
export function useUploadReceipt() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ image, capturedAt, source }: UploadReceiptVariables) =>
      source === "IMAGE_IMPORT" ? importReceiptImage(image) : uploadReceipt(image, capturedAt),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: receiptsKeys.all });
    },
  });
}
