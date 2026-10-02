import { describe, expect, it } from "vitest";
import { fallbackReceiptTitle } from "@/lib/receiptLabels";
import type { ReceiptSource } from "@/lib/types";

describe("fallbackReceiptTitle", () => {
  it.each<[ReceiptSource, string]>([
    ["IMAGE_IMPORT", "Zaimportowany obraz"],
    ["MANUAL", "Wpis ręczny"],
    ["CAMERA", "Paragon"],
  ])("labels %s as %s", (source, title) => {
    expect(fallbackReceiptTitle(source)).toBe(title);
  });

  it("falls back to Paragon for a source this client does not know yet (e.g. BANK_IMPORT)", () => {
    expect(fallbackReceiptTitle("BANK_IMPORT" as ReceiptSource)).toBe("Paragon");
  });
});
