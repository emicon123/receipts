import { PencilLine } from "lucide-react";
import { useState } from "react";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { cn, formatCurrency } from "@/lib/utils";
import type { CategoryInfo, LineItem, LineItemCorrectionRequest, SpendCategory } from "@/lib/types";

interface LineItemRowProps {
  item: LineItem;
  categories: CategoryInfo[];
  onSave: (body: LineItemCorrectionRequest) => void;
  isSaving: boolean;
}

/**
 * The caller remounts this component (via a `key` derived from the persisted field values —
 * see ReceiptDetailRoute) whenever `item` changes underneath it from outside this row (a
 * reprocess, a background refetch). That lets local edit buffers below be plain `useState`
 * initializers instead of a `useState` + effect re-sync.
 */
export function LineItemRow({ item, categories, onSave, isSaving }: LineItemRowProps) {
  const [productName, setProductName] = useState(item.productName);
  const [amount, setAmount] = useState(String(item.amount));

  function commitProductName() {
    const trimmed = productName.trim();
    if (trimmed && trimmed !== item.productName) {
      onSave({ productName: trimmed });
    } else {
      setProductName(item.productName);
    }
  }

  function commitAmount() {
    const parsed = Number.parseFloat(amount);
    if (Number.isFinite(parsed) && parsed >= 0 && parsed !== item.amount) {
      onSave({ amount: parsed });
    } else {
      setAmount(String(item.amount));
    }
  }

  function handleCategoryChange(category: string) {
    if (category !== item.category) {
      onSave({ category: category as SpendCategory });
    }
  }

  return (
    <li
      className={cn(
        "flex flex-col gap-2 rounded-lg border border-border bg-card p-3",
        isSaving && "opacity-60",
      )}
    >
      <div className="flex items-start justify-between gap-2">
        <Input
          value={productName}
          onChange={(e) => setProductName(e.target.value)}
          onBlur={commitProductName}
          aria-label="Product name"
          className="h-9 flex-1"
          disabled={isSaving}
        />
        {item.corrected && (
          <span className="mt-1 flex shrink-0 items-center gap-1 text-xs font-medium text-muted-foreground">
            <PencilLine className="size-3" />
            edited
          </span>
        )}
      </div>

      <div className="flex items-center gap-2">
        <Select value={item.category} onValueChange={handleCategoryChange} disabled={isSaving}>
          <SelectTrigger className="h-9 flex-1 text-sm" aria-label="Category">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {categories.map((category) => (
              <SelectItem key={category.code} value={category.code}>
                {category.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        <div className="flex items-center gap-1">
          <Input
            type="number"
            inputMode="decimal"
            step="0.01"
            min={0}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            onBlur={commitAmount}
            aria-label="Amount"
            className="h-9 w-24 text-right tabular-nums"
            disabled={isSaving}
          />
        </div>
      </div>

      <p className="sr-only" aria-live="polite">
        {formatCurrency(item.amount)}
      </p>
    </li>
  );
}
