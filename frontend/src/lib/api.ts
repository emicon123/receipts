import axios, { AxiosError, type AxiosInstance } from "axios";
import type {
  CategoryInfo,
  ErrorDetail,
  ErrorResponse,
  LineItem,
  LineItemCorrectionRequest,
  ListReceiptsParams,
  ManualReceiptRequest,
  PageInfo,
  ReceiptDetail,
  ReceiptSummary,
  ReprocessRequest,
  SpendingSummaryData,
  SpendingTrendData,
} from "@/lib/types";

/** Every envelope in docs/openapi.yaml wraps its payload as `{ data, meta }` (+ `page` for
 * paginated lists). These generics describe that envelope shape for response typing only —
 * callers below always unwrap to the plain payload before returning. */
interface Envelope<T> {
  data: T;
}
interface PagedEnvelope<T> extends Envelope<T> {
  page: PageInfo;
}

/** Normalized client-side error carrying the backend's structured error detail list, so UI
 * code can render `role="alert"` messages without re-parsing the axios error each time. */
export class ApiError extends Error {
  readonly status: number | undefined;
  readonly errors: ErrorDetail[];

  constructor(message: string, status: number | undefined, errors: ErrorDetail[]) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.errors = errors;
  }
}

// Relative to Vite's configured `base` (e.g. "/paragony/") rather than a hardcoded "/api" —
// this app is reverse-proxied under a path prefix by investing-app's shared nginx, not served
// from the domain root. See vite.config.ts's `base` and infra/nginx path-prefix notes.
export const apiClient: AxiosInstance = axios.create({
  baseURL: `${import.meta.env.BASE_URL}api`,
});

apiClient.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ErrorResponse>) => {
    const errors = error.response?.data?.errors ?? [];
    const message =
      errors[0]?.message ?? error.message ?? "Nieoczekiwany błąd sieci";
    return Promise.reject(new ApiError(message, error.response?.status, errors));
  },
);

// ---- Categories ----

export async function getCategories(): Promise<CategoryInfo[]> {
  const { data } = await apiClient.get<Envelope<CategoryInfo[]>>("/categories");
  return data.data;
}

// ---- Receipts ----

export async function listReceipts(
  params: ListReceiptsParams,
): Promise<{ data: ReceiptSummary[]; page: PageInfo }> {
  const { data } = await apiClient.get<PagedEnvelope<ReceiptSummary[]>>("/receipts", {
    params,
  });
  return { data: data.data, page: data.page };
}

export async function getReceipt(id: number): Promise<ReceiptDetail> {
  const { data } = await apiClient.get<Envelope<ReceiptDetail>>(`/receipts/${id}`);
  return data.data;
}

export async function uploadReceipt(
  image: File,
  capturedAt?: Date,
): Promise<ReceiptSummary> {
  const form = new FormData();
  form.append("image", image);
  if (capturedAt) {
    form.append("capturedAt", capturedAt.toISOString());
  }
  const { data } = await apiClient.post<Envelope<ReceiptSummary>>("/receipts", form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return data.data;
}

export async function createManualReceipt(
  body: ManualReceiptRequest,
): Promise<ReceiptDetail> {
  const { data } = await apiClient.post<Envelope<ReceiptDetail>>("/receipts/manual", body);
  return data.data;
}

export async function deleteReceipt(id: number): Promise<void> {
  await apiClient.delete(`/receipts/${id}`);
}

export async function correctLineItem(
  receiptId: number,
  itemId: number,
  body: LineItemCorrectionRequest,
): Promise<LineItem> {
  const { data } = await apiClient.put<Envelope<LineItem>>(
    `/receipts/${receiptId}/line-items/${itemId}`,
    body,
  );
  return data.data;
}

export async function reprocessReceipt(
  id: number,
  body?: ReprocessRequest,
): Promise<ReceiptSummary> {
  const { data } = await apiClient.post<Envelope<ReceiptSummary>>(
    `/receipts/${id}/reprocess`,
    body ?? {},
  );
  return data.data;
}

// ---- Spending aggregates ----

export async function getSpendingSummary(
  year: number,
  month: number,
): Promise<SpendingSummaryData> {
  const { data } = await apiClient.get<Envelope<SpendingSummaryData>>("/spending/summary", {
    params: { year, month },
  });
  return data.data;
}

export async function getSpendingTrend(year: number): Promise<SpendingTrendData> {
  const { data } = await apiClient.get<Envelope<SpendingTrendData>>("/spending/trend", {
    params: { year },
  });
  return data.data;
}
