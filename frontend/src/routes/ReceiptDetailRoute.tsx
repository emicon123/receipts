import { format } from "date-fns";
import { pl } from "date-fns/locale";
import { RefreshCw, Trash2 } from "lucide-react";
import { useState } from "react";
import { Navigate, useNavigate, useParams } from "react-router-dom";
import { AppShell } from "@/components/layout/AppShell";
import { StatusBadge } from "@/components/receipts/StatusBadge";
import { LineItemRow } from "@/components/receipts/LineItemRow";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useCategories } from "@/hooks/useCategories";
import { useCorrectLineItem } from "@/hooks/useCorrectLineItem";
import { useDeleteReceipt } from "@/hooks/useDeleteReceipt";
import { useReceipt } from "@/hooks/useReceipt";
import { useReprocessReceipt } from "@/hooks/useReprocessReceipt";
import { ApiError } from "@/lib/api";
import { formatCurrency } from "@/lib/utils";

export function ReceiptDetailRoute() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const navigate = useNavigate();
  const [pendingItemId, setPendingItemId] = useState<number | null>(null);
  const [confirmOpen, setConfirmOpen] = useState<"reprocess" | "delete" | null>(null);

  const { data: receipt, isPending, isError } = useReceipt(id);
  const { data: categories } = useCategories();
  const correctLineItem = useCorrectLineItem(id);
  const reprocess = useReprocessReceipt(id);
  const deleteReceipt = useDeleteReceipt();

  if (!params.id || Number.isNaN(id)) {
    return <Navigate to="/receipts" replace />;
  }

  function handleSaveLineItem(itemId: number, body: Parameters<typeof correctLineItem.mutate>[0]["body"]) {
    setPendingItemId(itemId);
    correctLineItem.mutate(
      { itemId, body },
      { onSettled: () => setPendingItemId(null) },
    );
  }

  function handleReprocess(force: boolean) {
    reprocess.mutate({ force });
    setConfirmOpen(null);
  }

  function handleDelete() {
    deleteReceipt.mutate(id, { onSuccess: () => navigate("/receipts") });
    setConfirmOpen(null);
  }

  function goToManualEntry() {
    if (!receipt) return;
    navigate("/receipts/manual", {
      state: { prefill: { capturedAt: receipt.capturedAt, storeName: receipt.storeName } },
    });
  }

  return (
    <AppShell title="Paragon">
      {isPending && <p className="py-8 text-center text-sm text-muted-foreground">Ładowanie…</p>}

      {isError && (
        <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
          Nie udało się wczytać paragonu.
        </p>
      )}

      {receipt && (
        <div className="flex flex-col gap-4">
          {receipt.imageUrl && (
            <div className="overflow-hidden rounded-xl border border-border bg-card">
              <img src={receipt.imageUrl} alt="Paragon" className="w-full object-contain" />
            </div>
          )}

          <div className="flex items-start justify-between gap-2 rounded-xl border border-border bg-card p-3">
            <div>
              <p className="font-medium">
                {receipt.storeName ?? (receipt.source === "MANUAL" ? "Wpis ręczny" : "Paragon")}
              </p>
              <p className="text-sm text-muted-foreground">
                {format(new Date(receipt.capturedAt), "d MMMM yyyy", { locale: pl })}
              </p>
              <div className="mt-2">
                <StatusBadge status={receipt.status} />
              </div>
            </div>
            <p className="text-lg font-semibold tabular-nums">
              {formatCurrency(receipt.totalAmount)}
            </p>
          </div>

          {receipt.status === "FAILED" && (
            <div className="rounded-xl border border-status-critical/30 bg-status-critical/10 p-3">
              <p className="text-sm font-medium text-status-critical">Klasyfikacja nie powiodła się</p>
              {receipt.failureReason && (
                <p className="mt-1 text-sm text-status-critical/90">{receipt.failureReason}</p>
              )}
              <div className="mt-3 flex flex-wrap gap-2">
                <Button type="button" size="sm" onClick={() => handleReprocess(false)}>
                  <RefreshCw />
                  Przetwórz ponownie
                </Button>
                <Button type="button" size="sm" variant="outline" onClick={goToManualEntry}>
                  Przepisz ręcznie
                </Button>
              </div>
            </div>
          )}

          {receipt.status === "PENDING" && (
            <p className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
              Oczekiwanie na kolejną dzienną klasyfikację.
            </p>
          )}

          {receipt.lineItems.length > 0 && categories && (
            <div>
              <h2 className="mb-2 text-sm font-semibold text-muted-foreground">Pozycje</h2>
              <ul className="flex flex-col gap-2">
                {receipt.lineItems.map((item) => (
                  <LineItemRow
                    // Remount (not just re-render) when the persisted product name/amount
                    // change from outside this row's own edits — see LineItemRow's docblock.
                    key={`${item.id}:${item.productName}:${item.amount}`}
                    item={item}
                    categories={categories}
                    isSaving={pendingItemId === item.id}
                    onSave={(body) => handleSaveLineItem(item.id, body)}
                  />
                ))}
              </ul>
              {correctLineItem.isError && (
                <p role="alert" className="mt-2 text-sm text-destructive">
                  {correctLineItem.error instanceof ApiError
                    ? correctLineItem.error.message
                    : "Nie udało się zapisać zmiany."}
                </p>
              )}
            </div>
          )}

          <div className="flex flex-wrap gap-2 pt-2">
            {(receipt.status === "PROCESSED" || receipt.status === "PROCESSING") && (
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => setConfirmOpen("reprocess")}
              >
                <RefreshCw />
                Przetwórz ponownie
              </Button>
            )}
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="text-destructive hover:text-destructive"
              onClick={() => setConfirmOpen("delete")}
            >
              <Trash2 />
              Usuń
            </Button>
          </div>
        </div>
      )}

      <Dialog open={confirmOpen === "reprocess"} onOpenChange={(open) => !open && setConfirmOpen(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Przetworzyć paragon ponownie?</DialogTitle>
            <DialogDescription>
              Spowoduje to odrzucenie bieżących wyników klasyfikacji (poprawione pozycje zostaną
              zachowane) i ponowne zakolejkowanie do następnej klasyfikacji.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfirmOpen(null)}>
              Anuluj
            </Button>
            <Button type="button" onClick={() => handleReprocess(true)}>
              Przetwórz ponownie
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirmOpen === "delete"} onOpenChange={(open) => !open && setConfirmOpen(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Usunąć paragon?</DialogTitle>
            <DialogDescription>
              Spowoduje to trwałe usunięcie paragonu, jego pozycji i zdjęcia. Tej operacji nie
              można cofnąć.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setConfirmOpen(null)}>
              Anuluj
            </Button>
            <Button type="button" variant="destructive" onClick={handleDelete}>
              Usuń
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </AppShell>
  );
}
