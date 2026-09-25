import type { CategorySubcategoryBreakdown, SubcategoryAmount } from "@/lib/types";

/**
 * Client-side top-N + "Reszta" bucketing for the "Szczegóły" stacked bars (ADR-013 §2).
 * Pure and framework-free so the rule can be sanity-checked in isolation.
 *
 * Per category bar, left to right:
 *  - the top 3 labeled subcategories by amount, each its own shaded segment (rank 0 = largest);
 *  - one grey "Reszta" segment holding every lower-ranked labeled subcategory plus the
 *    category's `unlabeledAmount`;
 *  - edge rule: if the remainder is exactly one labeled subcategory and `unlabeledAmount == 0`,
 *    that subcategory becomes a 4th shade instead of a one-item "Reszta".
 * Unlabeled spend is never its own shaded segment. Segments sum to the category total, so the
 * bar length matches the plain "Kategorie" chart.
 */

export const TOP_SHADED_SEGMENTS = 3;
export const REST_LABEL = "Reszta";

export interface ShadedSegment {
  kind: "shade";
  /** 0-based rank within this bar — drives the shade (0 = strongest). At most 3. */
  rank: number;
  label: string;
  amount: number;
}

export interface RestSegment {
  kind: "rest";
  label: typeof REST_LABEL;
  amount: number;
  /** The labeled subcategories folded into "Reszta", amount desc. */
  subcategories: SubcategoryAmount[];
  /** NULL/blank-subcategory spend folded into "Reszta". */
  unlabeledAmount: number;
}

export type Segment = ShadedSegment | RestSegment;

export interface BucketedBar {
  shades: ShadedSegment[];
  rest: RestSegment | null;
}

/** Sums money amounts and rounds to cents so float noise never shows up as a sliver segment. */
function sumAmounts(amounts: number[]): number {
  return Math.round(amounts.reduce((total, amount) => total + amount, 0) * 100) / 100;
}

export function bucketSubcategories(
  breakdown: Pick<CategorySubcategoryBreakdown, "subcategories" | "unlabeledAmount">,
  topN: number = TOP_SHADED_SEGMENTS,
): BucketedBar {
  // The server already sorts amount desc; re-sorting (stable) keeps the rule correct even if
  // that ever regresses, and costs nothing for ~13 entries.
  const sorted = [...breakdown.subcategories].sort((a, b) => b.amount - a.amount);
  const unlabeledAmount = breakdown.unlabeledAmount;

  let shadedCount = Math.min(topN, sorted.length);
  const remainderCount = sorted.length - shadedCount;
  if (remainderCount === 1 && unlabeledAmount === 0) {
    shadedCount += 1;
  }

  const shades: ShadedSegment[] = sorted.slice(0, shadedCount).map((entry, rank) => ({
    kind: "shade",
    rank,
    label: entry.subcategory,
    amount: entry.amount,
  }));

  const remainder = sorted.slice(shadedCount);
  const rest: RestSegment | null =
    remainder.length > 0 || unlabeledAmount !== 0
      ? {
          kind: "rest",
          label: REST_LABEL,
          amount: sumAmounts([...remainder.map((entry) => entry.amount), unlabeledAmount]),
          subcategories: remainder,
          unlabeledAmount,
        }
      : null;

  return { shades, rest };
}
