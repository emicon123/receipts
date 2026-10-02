import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ReceiptCard } from "@/components/receipts/ReceiptCard";
import { receiptSummary } from "@/test/fixtures";
import { renderWithProviders } from "@/test/utils";

describe("ReceiptCard title", () => {
  it("labels an image import without a store name as Zaimportowany obraz", () => {
    renderWithProviders(<ReceiptCard receipt={receiptSummary({ source: "IMAGE_IMPORT" })} />);

    expect(screen.getByText("Zaimportowany obraz")).toBeTruthy();
    expect(screen.queryByText("Paragon")).toBeNull();
  });

  it("keeps Paragon for a camera receipt and Wpis ręczny for a manual one", () => {
    const camera = renderWithProviders(<ReceiptCard receipt={receiptSummary({ source: "CAMERA" })} />);
    expect(screen.getByText("Paragon")).toBeTruthy();
    camera.unmount();

    renderWithProviders(
      <ReceiptCard receipt={receiptSummary({ source: "MANUAL", imageUrl: null })} />,
    );
    expect(screen.getByText("Wpis ręczny")).toBeTruthy();
  });

  it("prefers the store name once the classifier has set one", () => {
    renderWithProviders(
      <ReceiptCard receipt={receiptSummary({ source: "IMAGE_IMPORT", storeName: "Żabka" })} />,
    );

    expect(screen.getByText("Żabka")).toBeTruthy();
    expect(screen.queryByText("Zaimportowany obraz")).toBeNull();
  });
});
