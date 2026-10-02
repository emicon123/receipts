import type { ReceiptSource } from "@/lib/types";

/**
 * Title shown for a receipt that has no store name yet (the classifier fills `storeName` later),
 * chosen by where the receipt came from (ADR-014). Shared by the list card and the detail screen.
 * Anything that is not an import or a manual entry (CAMERA, and any source this client does not
 * know yet, e.g. the design-only BANK_IMPORT) falls back to "Paragon".
 *
 * The dashboard drill-down row keeps its own plain "Paragon" fallback: `SpendingLineItem` carries
 * no `source`.
 */
export function fallbackReceiptTitle(source: ReceiptSource): string {
  if (source === "IMAGE_IMPORT") return "Zaimportowany obraz";
  if (source === "MANUAL") return "Wpis ręczny";
  return "Paragon";
}
