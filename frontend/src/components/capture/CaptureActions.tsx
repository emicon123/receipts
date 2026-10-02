import { Camera, ClipboardPaste, ImagePlus } from "lucide-react";
import { Button } from "@/components/ui/button";

interface CaptureActionsProps {
  /** True while a picked image is being decoded/normalised; blocks starting a second one. */
  disabled: boolean;
  /** `navigator.clipboard.read()` exists (absent over plain HTTP, so the button is hidden then). */
  canPasteFromClipboard: boolean;
  /** A physical keyboard is likely (fine pointer), so Ctrl/Cmd+V is worth mentioning. */
  showKeyboardPasteHint: boolean;
  onOpenCamera: () => void;
  onOpenGallery: () => void;
  onPasteFromClipboard: () => void;
}

/**
 * Idle state of the capture screen, top to bottom: the big camera button (default action), a
 * prominent full-width gallery button (the primary import path on a phone), then the secondary
 * clipboard button and a desktop-only shortcut hint (04-classification-flow.md § Capture Entry
 * Points, ADR-014).
 */
export function CaptureActions({
  disabled,
  canPasteFromClipboard,
  showKeyboardPasteHint,
  onOpenCamera,
  onOpenGallery,
  onPasteFromClipboard,
}: CaptureActionsProps) {
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-6 text-center">
      <div>
        <p className="text-lg font-semibold">Zrób zdjęcie paragonu</p>
        <p className="mt-1 text-sm text-muted-foreground">
          Dotknij przycisku i zrób wyraźne zdjęcie całego paragonu albo wybierz gotowe zdjęcie lub
          zrzut ekranu z galerii. Przed wysłaniem zobaczysz podgląd.
        </p>
      </div>

      <Button
        type="button"
        size="icon"
        className="size-28 rounded-full [&_svg]:size-11"
        onClick={onOpenCamera}
        disabled={disabled}
        aria-label="Otwórz aparat, aby zrobić zdjęcie paragonu"
      >
        <Camera />
      </Button>

      <div className="flex w-full max-w-sm flex-col items-stretch gap-2">
        <Button
          type="button"
          variant="outline"
          size="lg"
          className="w-full"
          onClick={onOpenGallery}
          disabled={disabled}
        >
          <ImagePlus />
          Wybierz z galerii
        </Button>

        {canPasteFromClipboard && (
          <Button
            type="button"
            variant="ghost"
            size="sm"
            // `sm` styling, but keep a 44 px touch target.
            className="min-h-11 w-full"
            onClick={onPasteFromClipboard}
            disabled={disabled}
          >
            <ClipboardPaste />
            Wklej ze schowka
          </Button>
        )}

        {showKeyboardPasteHint && (
          <p className="text-xs text-muted-foreground">
            Na komputerze możesz też wkleić obraz skrótem Ctrl/Cmd+V.
          </p>
        )}
      </div>
    </div>
  );
}
