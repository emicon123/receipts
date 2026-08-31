import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { AppShell } from "@/components/layout/AppShell";
import { ReceiptCard } from "@/components/receipts/ReceiptCard";
import { Button } from "@/components/ui/button";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useReceipts } from "@/hooks/useReceipts";
import type { ReceiptStatus } from "@/lib/types";

const STATUS_TABS: { value: string; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "PROCESSED", label: "Processed" },
  { value: "FAILED", label: "Failed" },
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
    <AppShell title="Receipts">
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
            Couldn't load receipts. Pull to refresh or try again shortly.
          </p>
        )}

        {isPending && <p className="py-8 text-center text-sm text-muted-foreground">Loading…</p>}

        {data && data.data.length === 0 && (
          <p className="py-8 text-center text-sm text-muted-foreground">
            No receipts yet in this view.
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
              Prev
            </Button>
            <span className="text-sm text-muted-foreground">
              Page {page + 1} of {totalPages}
            </span>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => p + 1)}
              disabled={isPlaceholderData || page + 1 >= totalPages}
            >
              Next
              <ChevronRight />
            </Button>
          </div>
        )}
      </div>
    </AppShell>
  );
}
