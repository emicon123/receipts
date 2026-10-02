import { screen } from "@testing-library/react";
import { Route, Routes } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { apiClient } from "@/lib/api";
import type { ReceiptDetail } from "@/lib/types";
import { ReceiptDetailRoute } from "@/routes/ReceiptDetailRoute";
import { receiptDetail } from "@/test/fixtures";
import { renderWithProviders } from "@/test/utils";

function renderDetail(receipt: ReceiptDetail) {
  vi.spyOn(apiClient, "get").mockImplementation((url: string) =>
    Promise.resolve({
      data: { data: url.startsWith("/receipts/") ? receipt : [] },
    } as never),
  );
  return renderWithProviders(
    <Routes>
      <Route path="/receipts/:id" element={<ReceiptDetailRoute />} />
    </Routes>,
    `/receipts/${receipt.id}`,
  );
}

describe("ReceiptDetailRoute labels", () => {
  it("titles an image import Zaimportowany obraz and uses it as the image alt text", async () => {
    renderDetail(receiptDetail({ id: 44, source: "IMAGE_IMPORT" }));

    const image = await screen.findByRole("img", { name: "Zaimportowany obraz" });
    expect(image.getAttribute("src")).toBe("/api/receipts/44/image");
    // The card title carries the same fallback label.
    expect(screen.getAllByText("Zaimportowany obraz").length).toBeGreaterThan(0);
  });

  it("keeps Paragon for a camera receipt", async () => {
    renderDetail(receiptDetail({ id: 7, source: "CAMERA", imageUrl: "/api/receipts/7/image" }));

    expect(await screen.findByRole("img", { name: "Paragon" })).toBeTruthy();
  });

  it("shows the store name as the title once known", async () => {
    renderDetail(receiptDetail({ source: "IMAGE_IMPORT", storeName: "Biedronka" }));

    expect(await screen.findByText("Biedronka")).toBeTruthy();
    expect(screen.queryByText("Zaimportowany obraz", { selector: "p" })).toBeNull();
  });
});
