import { format } from "date-fns";
import { pl } from "date-fns/locale";
import { ChevronRight } from "lucide-react";
import { Link } from "react-router-dom";
import { formatCurrency } from "@/lib/utils";
import type { SpendingLineItem } from "@/lib/types";

/**
 * Read-only drill-down row — reuses the rounded-card row look from LineItemRow.tsx/
 * ReceiptCard.tsx, but with no inputs: this task adds no edit UI for subcategory/
 * subSubcategory (ADR-010 §4), so these rows are display-only plus a link back to the
 * parent receipt.
 */
export function CategoryLineItemRow({ item }: { item: SpendingLineItem }) {
  const meta = [
    item.storeName ?? "Paragon",
    format(new Date(item.capturedAt), "d MMM", { locale: pl }),
    item.quantity != null ? `${item.quantity} szt.` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <li className="flex items-center gap-2 rounded-lg border border-border bg-card p-3">
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">{item.productName}</p>
        <p className="truncate text-xs text-muted-foreground">{meta}</p>
      </div>
      <p className="shrink-0 font-semibold tabular-nums">{formatCurrency(item.amount)}</p>
      <Link
        to={`/receipts/${item.receiptId}`}
        aria-label={`Pokaż paragon: ${item.productName}`}
        className="flex size-8 shrink-0 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-accent hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <ChevronRight className="size-4" />
      </Link>
    </li>
  );
}
