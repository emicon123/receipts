import { Check, RotateCcw } from "lucide-react";
import { Button } from "@/components/ui/button";

interface CapturePreviewProps {
  previewUrl: string;
  isUploading: boolean;
  onRetake: () => void;
  onAccept: () => void;
}

/** Client-side preview of a just-captured photo, with the explicit Retake/Accept
 * confirmation step required before anything is uploaded. */
export function CapturePreview({
  previewUrl,
  isUploading,
  onRetake,
  onAccept,
}: CapturePreviewProps) {
  return (
    <div className="flex flex-1 flex-col gap-4">
      <div className="relative flex-1 overflow-hidden rounded-xl border border-border bg-card">
        <img
          src={previewUrl}
          alt="Captured receipt preview"
          className="size-full object-contain"
        />
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Button
          type="button"
          variant="outline"
          size="lg"
          onClick={onRetake}
          disabled={isUploading}
        >
          <RotateCcw />
          Retake
        </Button>
        <Button type="button" size="lg" onClick={onAccept} disabled={isUploading}>
          <Check />
          {isUploading ? "Uploading…" : "Accept"}
        </Button>
      </div>
    </div>
  );
}
