import type { ReceiptDetail, ReceiptSummary } from "@/lib/types";

export function receiptSummary(overrides: Partial<ReceiptSummary> = {}): ReceiptSummary {
  return {
    id: 44,
    status: "PENDING",
    source: "CAMERA",
    capturedAt: "2026-10-02T10:00:00Z",
    storeName: null,
    totalAmount: 0,
    imageUrl: "/api/receipts/44/image",
    failureReason: null,
    createdAt: "2026-10-02T10:00:00Z",
    ...overrides,
  };
}

export function receiptDetail(overrides: Partial<ReceiptDetail> = {}): ReceiptDetail {
  return { ...receiptSummary(), processedAt: null, lineItems: [], ...overrides };
}

export function pngFile(name = "zrzut.png"): File {
  return new File(["png-bytes"], name, { type: "image/png" });
}
