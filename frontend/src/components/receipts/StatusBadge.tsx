import { CheckCircle2, Clock, Loader2, TriangleAlert } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { ReceiptStatus } from "@/lib/types";

/**
 * Status color follows the dataviz skill's fixed status scale (good/warning/critical) —
 * never color alone, always paired with an icon and text label.
 */
const STATUS_CONFIG: Record<
  ReceiptStatus,
  { label: string; variant: "good" | "warning" | "critical" | "secondary"; icon: typeof Clock }
> = {
  PENDING: { label: "Pending", variant: "warning", icon: Clock },
  PROCESSING: { label: "Processing", variant: "secondary", icon: Loader2 },
  PROCESSED: { label: "Processed", variant: "good", icon: CheckCircle2 },
  FAILED: { label: "Failed", variant: "critical", icon: TriangleAlert },
};

export function StatusBadge({ status }: { status: ReceiptStatus }) {
  const { label, variant, icon: Icon } = STATUS_CONFIG[status];
  return (
    <Badge variant={variant}>
      <Icon className={status === "PROCESSING" ? "size-3 animate-spin" : "size-3"} />
      {label}
    </Badge>
  );
}
