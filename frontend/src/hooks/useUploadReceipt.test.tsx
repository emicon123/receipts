import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { useUploadReceipt } from "@/hooks/useUploadReceipt";
import { apiClient } from "@/lib/api";
import { receiptsKeys } from "@/lib/queryKeys";
import { pngFile, receiptSummary } from "@/test/fixtures";
import { TestProviders } from "@/test/TestProviders";
import { createTestQueryClient } from "@/test/utils";

function setup() {
  const client = createTestQueryClient();
  const invalidate = vi.spyOn(client, "invalidateQueries");
  const post = vi
    .spyOn(apiClient, "post")
    .mockResolvedValue({ data: { data: receiptSummary() } } as never);
  const wrapper = ({ children }: { children: ReactNode }) => (
    <TestProviders client={client}>{children}</TestProviders>
  );
  const hook = renderHook(() => useUploadReceipt(), { wrapper });
  return { invalidate, post, ...hook };
}

function sentForm(post: ReturnType<typeof setup>["post"]): FormData {
  return post.mock.calls[0]?.[1] as FormData;
}

describe("useUploadReceipt source routing", () => {
  it("posts a CAMERA draft to /receipts, including capturedAt", async () => {
    const { result, post } = setup();
    const image = pngFile("camera.png");
    const capturedAt = new Date("2026-10-02T08:15:00Z");

    result.current.mutate({ image, source: "CAMERA", capturedAt });

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0]?.[0]).toBe("/receipts");
    const form = sentForm(post);
    expect((form.get("image") as File).name).toBe("camera.png");
    expect(form.get("capturedAt")).toBe("2026-10-02T08:15:00.000Z");
  });

  it("posts an IMAGE_IMPORT draft to /receipts/image-import without capturedAt", async () => {
    const { result, post } = setup();

    // Even if a caller passes a capturedAt, an import never sends it (server defaults to now).
    result.current.mutate({
      image: pngFile("import.png"),
      source: "IMAGE_IMPORT",
      capturedAt: new Date(),
    });

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0]?.[0]).toBe("/receipts/image-import");
    const form = sentForm(post);
    expect((form.get("image") as File).name).toBe("import.png");
    expect(form.has("capturedAt")).toBe(false);
  });

  it("invalidates the receipts queries after either upload", async () => {
    const { result, invalidate } = setup();

    result.current.mutate({ image: pngFile(), source: "IMAGE_IMPORT" });
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: receiptsKeys.all }),
    );

    invalidate.mockClear();
    result.current.mutate({ image: pngFile(), source: "CAMERA" });
    await waitFor(() =>
      expect(invalidate).toHaveBeenCalledWith({ queryKey: receiptsKeys.all }),
    );
  });
});
