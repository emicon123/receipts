import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { formatMonthLong } from "@/lib/utils";

interface MonthPickerProps {
  year: number;
  month: number;
  onChange: (year: number, month: number) => void;
}

export function MonthPicker({ year, month, onChange }: MonthPickerProps) {
  function shift(delta: number) {
    const date = new Date(year, month - 1 + delta, 1);
    onChange(date.getFullYear(), date.getMonth() + 1);
  }

  return (
    <div className="flex items-center justify-between">
      <Button type="button" variant="outline" size="icon" onClick={() => shift(-1)} aria-label="Poprzedni miesiąc">
        <ChevronLeft />
      </Button>
      <p className="text-sm font-semibold capitalize">{formatMonthLong(year, month)}</p>
      <Button type="button" variant="outline" size="icon" onClick={() => shift(1)} aria-label="Następny miesiąc">
        <ChevronRight />
      </Button>
    </div>
  );
}
