import { format } from "date-fns";
import { Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { z } from "zod";
import { AppShell } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { useCategories } from "@/hooks/useCategories";
import { useCreateManualReceipt } from "@/hooks/useManualReceipt";
import { ApiError } from "@/lib/api";
import type { LineItemInput } from "@/lib/types";

interface DraftLineItem {
  clientId: string;
  productName: string;
  category: string;
  amount: string;
}

function emptyDraft(): DraftLineItem {
  return { clientId: crypto.randomUUID(), productName: "", category: "", amount: "" };
}

/** Local noon avoids a date-only input drifting to the previous/next day across a UTC
 * offset when converted with `toISOString()`. */
function dateInputToIso(dateInput: string): string {
  const parts = dateInput.split("-").map(Number);
  const [year = 1970, month = 1, day = 1] = parts;
  return new Date(year, month - 1, day, 12, 0, 0).toISOString();
}

const draftSchema = z.object({
  productName: z.string().trim().min(1, { error: "Wymagane" }).max(300),
  category: z.string().min(1, { error: "Wymagane" }),
  amount: z
    .string()
    .refine((v) => Number.isFinite(Number(v)) && Number(v) >= 0, { error: "Musi być kwotą dodatnią" }),
});

export function ManualEntryRoute() {
  const location = useLocation() as {
    state?: { prefill?: { capturedAt?: string; storeName?: string | null } };
  };
  const prefill = location.state?.prefill;

  const navigate = useNavigate();
  const { data: categories } = useCategories();
  const createManualReceipt = useCreateManualReceipt();

  const [capturedAt, setCapturedAt] = useState(
    prefill?.capturedAt ? format(new Date(prefill.capturedAt), "yyyy-MM-dd") : format(new Date(), "yyyy-MM-dd"),
  );
  const [storeName, setStoreName] = useState(prefill?.storeName ?? "");
  const [items, setItems] = useState<DraftLineItem[]>([emptyDraft()]);
  const [rowErrors, setRowErrors] = useState<Record<string, string>>({});

  function updateItem(clientId: string, patch: Partial<DraftLineItem>) {
    setItems((prev) => prev.map((it) => (it.clientId === clientId ? { ...it, ...patch } : it)));
  }

  function addItem() {
    setItems((prev) => [...prev, emptyDraft()]);
  }

  function removeItem(clientId: string) {
    setItems((prev) => (prev.length > 1 ? prev.filter((it) => it.clientId !== clientId) : prev));
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();

    const errors: Record<string, string> = {};
    for (const item of items) {
      const result = draftSchema.safeParse(item);
      if (!result.success) {
        errors[item.clientId] = result.error.issues[0]?.message ?? "Nieprawidłowe";
      }
    }
    setRowErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const lineItems: LineItemInput[] = items.map((item) => ({
      productName: item.productName.trim(),
      category: item.category as LineItemInput["category"],
      amount: Number(item.amount),
    }));

    createManualReceipt.mutate(
      {
        capturedAt: dateInputToIso(capturedAt),
        storeName: storeName.trim() || null,
        lineItems,
      },
      {
        onSuccess: (receipt) => navigate(`/receipts/${receipt.id}`),
      },
    );
  }

  return (
    <AppShell title="Dodaj ręcznie">
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        <p className="text-sm text-muted-foreground">
          Dla wpisów bez zdjęcia — najczęściej rachunki (Rachunki).
        </p>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="capturedAt">Data</Label>
          <Input
            id="capturedAt"
            type="date"
            value={capturedAt}
            onChange={(e) => setCapturedAt(e.target.value)}
            required
          />
        </div>

        <div className="flex flex-col gap-1.5">
          <Label htmlFor="storeName">Sklep / dostawca (opcjonalnie)</Label>
          <Input
            id="storeName"
            value={storeName}
            onChange={(e) => setStoreName(e.target.value)}
            placeholder="np. dostawca prądu"
          />
        </div>

        <div className="flex flex-col gap-3">
          <h2 className="text-sm font-semibold text-muted-foreground">Pozycje</h2>
          {items.map((item, index) => (
            <div key={item.clientId} className="flex flex-col gap-2 rounded-lg border border-border bg-card p-3">
              <div className="flex items-start justify-between gap-2">
                <Input
                  value={item.productName}
                  onChange={(e) => updateItem(item.clientId, { productName: e.target.value })}
                  placeholder="Produkt / opis"
                  aria-label={`Pozycja ${index + 1} nazwa`}
                  className="h-9 flex-1"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="size-9 shrink-0 text-muted-foreground"
                  onClick={() => removeItem(item.clientId)}
                  disabled={items.length === 1}
                  aria-label="Usuń pozycję"
                >
                  <Trash2 className="size-4" />
                </Button>
              </div>

              <div className="flex items-center gap-2">
                <Select
                  value={item.category}
                  onValueChange={(value) => updateItem(item.clientId, { category: value })}
                >
                  <SelectTrigger className="h-9 flex-1 text-sm" aria-label={`Pozycja ${index + 1} kategoria`}>
                    <SelectValue placeholder="Kategoria" />
                  </SelectTrigger>
                  <SelectContent>
                    {categories?.map((category) => (
                      <SelectItem key={category.code} value={category.code}>
                        {category.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  min={0}
                  value={item.amount}
                  onChange={(e) => updateItem(item.clientId, { amount: e.target.value })}
                  placeholder="0,00"
                  aria-label={`Pozycja ${index + 1} kwota`}
                  className="h-9 w-24 text-right tabular-nums"
                />
              </div>

              {rowErrors[item.clientId] && (
                <p role="alert" className="text-xs text-destructive">
                  {rowErrors[item.clientId]}
                </p>
              )}
            </div>
          ))}

          <Button type="button" variant="outline" size="sm" onClick={addItem} className="self-start">
            <Plus />
            Dodaj pozycję
          </Button>
        </div>

        {createManualReceipt.isError && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {createManualReceipt.error instanceof ApiError
              ? createManualReceipt.error.message
              : "Nie udało się zapisać wpisu."}
          </p>
        )}

        <Button type="submit" size="lg" disabled={createManualReceipt.isPending}>
          {createManualReceipt.isPending ? "Zapisywanie…" : "Zapisz wpis"}
        </Button>
      </form>
    </AppShell>
  );
}
