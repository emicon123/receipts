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
  /**
   * Free-text, classifier-assigned grouping one level finer than `category` (e.g. "Słodycze"
   * under JEDZENIE_PIERDOLOWATE). No enum, no validation — see ADR-010. `null` for line items
   * predating this field (no historical backfill) and for any batch/manual entry that omitted it.
   */
  subcategory?: string | null;
  /** Free-text grouping one level finer than `subcategory` (e.g. "żelki"). Same rules as above. */
  subSubcategory?: string | null;
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

/** One normalized (`lower(trim())`) subcategory group within a category/month (ADR-013). */
export interface SubcategoryAmount {
  /** Most-used exact-cased variant in this month's slice — never null/blank. */
  subcategory: string;
  amount: number;
}

/**
 * One category's subcategory breakdown (GET /spending/subcategory-summary, ADR-013).
 * Invariant: `totalAmount == unlabeledAmount + Σ subcategories[].amount`. `subcategories` is the
 * full list sorted by amount desc — top-N + "Reszta" bucketing is done client-side.
 */
export interface CategorySubcategoryBreakdown {
  category: SpendCategory;
  totalAmount: number;
  /** Sum for line items with a NULL/blank subcategory; `0` when none. */
  unlabeledAmount: number;
  subcategories: SubcategoryAmount[];
}

export interface SpendingSubcategorySummaryData {
  year: number;
  month: number;
  totalAmount: number;
  /** All 11 categories, zero-filled, canonical GET /categories order. */
  categories: CategorySubcategoryBreakdown[];
}

/**
 * One line item in the GET /spending/line-items drill-down (ADR-010) — the same LineItem shape
 * plus fields denormalized from the parent receipt, for display and for linking back to
 * GET /receipts/{id}. Mirrors openapi.yaml's SpendingLineItem (an `allOf` over LineItem).
 */
export interface SpendingLineItem extends LineItem {
  receiptId: number;
  storeName?: string | null;
  capturedAt: string;
}

// ---- List filters ----

export interface ListReceiptsParams {
  year?: number;
  month?: number;
  status?: ReceiptStatus;
  page?: number;
  size?: number;
}
