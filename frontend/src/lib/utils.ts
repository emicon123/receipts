import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}

const currencyFormatter = new Intl.NumberFormat("pl-PL", {
  style: "currency",
  currency: "PLN",
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatCurrency(amount: number): string {
  return currencyFormatter.format(amount);
}

const monthLabelFormatter = new Intl.DateTimeFormat("pl-PL", {
  month: "short",
});

/** Short Polish month label for a 1-12 month number, e.g. 3 -> "mar". */
export function formatMonthShort(month: number): string {
  return monthLabelFormatter.format(new Date(2000, month - 1, 1));
}

const monthLabelFormatterLong = new Intl.DateTimeFormat("pl-PL", {
  month: "long",
  year: "numeric",
});

/** Long Polish month label, e.g. "sierpień 2026". */
export function formatMonthLong(year: number, month: number): string {
  return monthLabelFormatterLong.format(new Date(year, month - 1, 1));
}
