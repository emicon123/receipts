import type { ListReceiptsParams } from "@/lib/types";

export const categoriesKeys = {
  all: ["categories"] as const,
};

export const receiptsKeys = {
  all: ["receipts"] as const,
  list: (filters: ListReceiptsParams) => [...receiptsKeys.all, "list", filters] as const,
  detail: (id: number) => [...receiptsKeys.all, "detail", id] as const,
};

export const spendingKeys = {
  all: ["spending"] as const,
  summary: (year: number, month: number) =>
    [...spendingKeys.all, "summary", year, month] as const,
  trend: (year: number) => [...spendingKeys.all, "trend", year] as const,
};
