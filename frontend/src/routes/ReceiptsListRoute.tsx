import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { ReceiptCard } from "@/components/receipts/ReceiptCard";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useReceipts } from "@/hooks/useReceipts";
import type { ReceiptStatus } from "@/lib/types";

const STATUS_TABS: { value: string; label: string }[] = [
  { value: "ALL", label: "Wszystkie" },
  { value: "PENDING", label: "Oczekujące" },
  { value: "PROCESSED", label: "Gotowe" },
  { value: "FAILED", label: "Błędy" },
];

const PAGE_SIZE = 20;

export function ReceiptsListRoute() {
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [page, setPage] = useState(0);

  const status = statusFilter === "ALL" ? undefined : (statusFilter as ReceiptStatus);
  const { data, isPending, isError, isPlaceholderData } = useReceipts({
    status,
    page,
    size: PAGE_SIZE,
  });

  function handleStatusChange(value: string) {
    setStatusFilter(value);
    setPage(0);
  }

  const totalPages = data ? Math.max(1, Math.ceil(data.page.total / data.page.size)) : 1;

  return (
    <AppShell title="Paragony">
      <div className="flex flex-col gap-4">
        <Tabs value={statusFilter} onValueChange={handleStatusChange}>
          <TabsList className="w-full">
            {STATUS_TABS.map((tab) => (
              <TabsTrigger key={tab.value} value={tab.value}>
                {tab.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        {isError && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            Nie udało się wczytać paragonów. Odśwież lub spróbuj ponownie.
          </p>
        )}

        {isPending && <p className="py-8 text-center text-sm text-muted-foreground">Ładowanie…</p>}

        {data && data.data.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            Brak paragonów w tym widoku.
          </p>
        )}

        <div className="flex flex-col gap-2">
          {data?.data.map((receipt) => (
            <ReceiptCard key={receipt.id} receipt={receipt} />
          ))}
        </div>

        {data && totalPages > 1 && (
          <div className="flex items-center justify-between pt-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              <ChevronLeft />
              Poprz.
            </Button>
            <span className="text-sm text-muted-foreground">
              Strona {page + 1} z {totalPages}
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => p + 1)}
              disabled={isPlaceholderData || page + 1 >= totalPages}
            >
              Nast.
              <ChevronRight />
            </Button>
          </div>
        )}
      </div>
    </AppShell>
  );
}
