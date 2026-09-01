import { format } from "date-fns";
import { pl } from "date-fns/locale";
import { NotebookPen } from "lucide-react";
import { Link } from "react-router-dom";
import { StatusBadge } from "@/components/receipts/StatusBadge";
import { formatCurrency } from "@/lib/utils";
import type { ReceiptSummary } from "@/lib/types";

export function ReceiptCard({ receipt }: { receipt: ReceiptSummary }) {
  return (
    <Link
      to={`/receipts/${receipt.id}`}
      className="flex items-center gap-3 rounded-xl border border-border bg-card p-3 transition-colors hover:bg-accent"
    >
      <div className="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-muted">
        {receipt.imageUrl ? (
          <img
            src={receipt.imageUrl}
            alt=""
            className="size-full object-cover"
            loading="lazy"
          />
        ) : (
          <NotebookPen className="size-6 text-muted-foreground" aria-hidden="true" />
        )}
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <p className="truncate text-sm font-medium">
            {receipt.storeName ?? (receipt.source === "MANUAL" ? "Wpis ręczny" : "Paragon")}
          </p>
        </div>
        <p className="text-xs text-muted-foreground">
          {format(new Date(receipt.capturedAt), "d MMM yyyy", { locale: pl })}
        </p>
        <div className="mt-1.5">
          <StatusBadge status={receipt.status} />
        </div>
      </div>

      <div className="flex shrink-0 flex-col items-end">
        <p className="font-semibold tabular-nums">{formatCurrency(receipt.totalAmount)}</p>
      </div>
    </Link>
  );
}
