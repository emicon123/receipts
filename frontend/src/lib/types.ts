/**
 * Types mirroring docs/openapi.yaml (components/schemas). Field names and shapes here must
 * match that spec exactly — it is the single source of truth for this app's REST contract.
 */

export const RECEIPT_STATUSES = ["PENDING", "PROCESSING", "PROCESSED", "FAILED"] as const;
export type ReceiptStatus = (typeof RECEIPT_STATUSES)[number];

export const RECEIPT_SOURCES = ["CAMERA", "MANUAL"] as const;
export type ReceiptSource = (typeof RECEIPT_SOURCES)[number];

/**
 * The fixed 11-value category enum (see openapi.yaml SpendCategory / CLAUDE.md § Categories).
 * Listed here only as a TypeScript union for typing API payloads — the canonical, orderable,
 * label-bearing list is always fetched from GET /api/categories, never hardcoded for display.
 */
export type SpendCategory =
  | "ALKO"
  | "JEDZENIE_KONIECZNE"
  | "JEDZENIE_SREDNIE"
  | "JEDZENIE_PIERDOLOWATE"
  | "RZECZY_PALIWO_INNE_ROZNE"
  | "RZECZY_LUKSUSOWE"
  | "MYCIE_CHEMIA"
  | "ROZRYWKA_RESTAURACJE"
  | "RACHUNKI"
  | "BOBINEK"
  | "SUPLE";

export interface Meta {
  requestId: string;
  timestamp: string;
}

export interface PageInfo {
  number: number;
  size: number;
  total: number;
}

export interface ErrorDetail {
  code: string;
  field?: string | null;
  message: string;
}

export interface ErrorResponse {
  errors: ErrorDetail[];
  meta: Meta;
}

// ---- Categories ----

export interface CategoryInfo {
  code: SpendCategory;
  label: string;
  gloss: string;
}

// ---- Line items ----

export interface LineItem {
  id: number;
  productName: string;
  category: SpendCategory;
  amount: number;
  quantity?: number | null;
  corrected: boolean;
}

export interface LineItemInput {
  productName: string;
  category: SpendCategory;
  amount: number;
  quantity?: number | null;
}

export interface LineItemCorrectionRequest {
  productName?: string;
  category?: SpendCategory;
  amount?: number;
  quantity?: number | null;
}

// ---- Receipts ----

export interface ReceiptSummary {
  id: number;
  status: ReceiptStatus;
  source: ReceiptSource;
  capturedAt: string;
  storeName?: string | null;
  totalAmount: number;
  imageUrl?: string | null;
  failureReason?: string | null;
  createdAt: string;
}

export interface ReceiptDetail extends ReceiptSummary {
  processedAt?: string | null;
  lineItems: LineItem[];
}

export interface ManualReceiptRequest {
  capturedAt: string;
  storeName?: string | null;
  lineItems: LineItemInput[];
}

export interface ReprocessRequest {
  force?: boolean;
}

// ---- Spending aggregates ----

export interface CategoryAmount {
  category: SpendCategory;
  amount: number;
}

export interface SpendingSummaryData {
  year: number;
  month: number;
  totalAmount: number;
  categories: CategoryAmount[];
}

export interface SpendingMonth {
  month: number;
  totalAmount: number;
  categories: CategoryAmount[];
}

export interface SpendingTrendData {
  year: number;
  months: SpendingMonth[];
}

// ---- List filters ----

export interface ListReceiptsParams {
  year?: number;
  month?: number;
  status?: ReceiptStatus;
  page?: number;
  size?: number;
}
