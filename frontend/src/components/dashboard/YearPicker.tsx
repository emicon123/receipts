import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

interface YearPickerProps {
  year: number;
  onChange: (year: number) => void;
}

export function YearPicker({ year, onChange }: YearPickerProps) {
  return (
    <div className="flex items-center justify-between">
      <Button type="button" variant="outline" size="icon" onClick={() => onChange(year - 1)} aria-label="Poprzedni rok">
        <ChevronLeft />
      </Button>
      <p className="text-sm font-semibold tabular-nums">{year}</p>
      <Button type="button" variant="outline" size="icon" onClick={() => onChange(year + 1)} aria-label="Następny rok">
        <ChevronRight />
      </Button>
    </div>
  );
}
