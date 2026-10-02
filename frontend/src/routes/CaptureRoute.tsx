import { useCallback, useEffect, useRef, useState } from "react";
import type { ChangeEvent, DragEvent } from "react";
import { useNavigate } from "react-router-dom";
import { CaptureActions } from "@/components/capture/CaptureActions";
import { CapturePreview } from "@/components/capture/CapturePreview";
import { AppShell } from "@/components/layout/AppShell";
import { useUploadReceipt } from "@/hooks/useUploadReceipt";
import {
  hasFinePointer,
  imageFromClipboardData,
  isClipboardReadSupported,
  readClipboardImage,
} from "@/lib/clipboardImage";
import { describeImportError, normalizeImageFile } from "@/lib/normalizeImageFile";
import type { ImageReceiptSource } from "@/lib/types";
import { describeUploadError } from "@/lib/uploadError";

/** A normalised image waiting for the user's Powtórz / Zatwierdź decision, plus where it came from. */
interface Draft {
  file: File;
  source: ImageReceiptSource;
  previewUrl: string;
}

/**
 * The only place an image enters the app. Camera, gallery picker, clipboard button, `paste` event
 * and drop all converge on one normalisation step, one preview/confirm step and one upload
 * mutation; only the upload endpoint differs by the draft's source (CAMERA vs IMAGE_IMPORT) —
 * docs/architecture/04-classification-flow.md § Capture Entry Points.
 */
export function CaptureRoute() {
  const [draft, setDraft] = useState<Draft | null>(null);
  const [importError, setImportError] = useState<string | null>(null);
  const [isPreparing, setIsPreparing] = useState(false);
  const cameraInputRef = useRef<HTMLInputElement>(null);
  const galleryInputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const upload = useUploadReceipt();
  const { reset: resetUpload } = upload;
  // Environment capabilities don't change during a visit — evaluate once.
  const [canPasteFromClipboard] = useState(isClipboardReadSupported);
  const [showKeyboardPasteHint] = useState(hasFinePointer);

  const hasDraft = draft !== null;
  const previewUrl = draft?.previewUrl;

  // Revoke the object URL whenever we drop it, so preview blobs don't leak.
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  const startDraft = useCallback(
    async (image: File | Promise<File>, source: ImageReceiptSource) => {
      setImportError(null);
      resetUpload();
      setIsPreparing(true);
      try {
        const file = await normalizeImageFile(await image);
        setDraft({ file, source, previewUrl: URL.createObjectURL(file) });
      } catch (error) {
        setImportError(describeImportError(error));
      } finally {
        setIsPreparing(false);
      }
    },
    [resetUpload],
  );

  // Ctrl/Cmd+V (and long-press -> Paste on mobile, where it fires) — the only clipboard path that
  // works without the async Clipboard API, e.g. over plain HTTP. Only while no draft is showing.
  useEffect(() => {
    if (hasDraft || isPreparing) return;
    function handlePaste(event: ClipboardEvent) {
      const image = imageFromClipboardData(event.clipboardData);
      if (!image) return; // text-only paste: leave it alone
      event.preventDefault();
      void startDraft(image, "IMAGE_IMPORT");
    }
    document.addEventListener("paste", handlePaste);
    return () => document.removeEventListener("paste", handlePaste);
  }, [hasDraft, isPreparing, startDraft]);

  function pickFromInput(event: ChangeEvent<HTMLInputElement>, source: ImageReceiptSource) {
    const selected = event.target.files?.[0];
    // Allow re-selecting the exact same file next time (retake -> same photo).
    event.target.value = "";
    if (selected) void startDraft(selected, source);
  }

  function handleDragOver(event: DragEvent<HTMLDivElement>) {
    // Without this the browser would navigate away to the dropped image file.
    if (event.dataTransfer.types.includes("Files")) event.preventDefault();
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    if (!event.dataTransfer.types.includes("Files")) return;
    event.preventDefault();
    const dropped = event.dataTransfer.files[0];
    if (dropped && !hasDraft && !isPreparing) void startDraft(dropped, "IMAGE_IMPORT");
  }

  function handleRetake() {
    setDraft(null);
    setImportError(null);
    upload.reset();
  }

  function handleAccept() {
    if (!draft) return;
    upload.mutate(
      {
        image: draft.file,
        source: draft.source,
        // An import omits it (server defaults to now; the classifier reads the real date).
        capturedAt: draft.source === "CAMERA" ? new Date() : undefined,
      },
      {
        onSuccess: () => {
          setDraft(null);
          navigate("/receipts");
        },
      },
    );
  }

  const errorMessage = importError ?? (upload.isError ? describeUploadError(upload.error) : null);

  return (
    <AppShell title="Dodaj paragon">
      <div
        className="flex min-h-full flex-col gap-4"
        onDragOver={handleDragOver}
        onDrop={handleDrop}
      >
        {errorMessage && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {errorMessage}
          </p>
        )}

        {isPreparing && (
          <p role="status" className="rounded-lg bg-muted px-3 py-2 text-sm text-muted-foreground">
            Przygotowuję obraz…
          </p>
        )}

        {draft ? (
          <CapturePreview
            previewUrl={draft.previewUrl}
            isUploading={upload.isPending}
            onRetake={handleRetake}
            onAccept={handleAccept}
          />
        ) : (
          <CaptureActions
            disabled={isPreparing}
            canPasteFromClipboard={canPasteFromClipboard}
            showKeyboardPasteHint={showKeyboardPasteHint}
            onOpenCamera={() => cameraInputRef.current?.click()}
            onOpenGallery={() => galleryInputRef.current?.click()}
            onPasteFromClipboard={() => void startDraft(readClipboardImage(), "IMAGE_IMPORT")}
          />
        )}

        {/* Camera: opens the camera directly. */}
        <input
          ref={cameraInputRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={(event) => pickFromInput(event, "CAMERA")}
          className="sr-only"
          aria-hidden="true"
          tabIndex={-1}
        />
        {/* Gallery: deliberately NO `capture` (that would open the camera instead of the photo
            library / file chooser) and NO `multiple` (one image = one receipt entry). `image/*`
            rather than a png/jpeg/webp list so Android shows HEIC/HEIF files too; whatever the
            backend can't take is converted by normalizeImageFile. */}
        <input
          ref={galleryInputRef}
          type="file"
          accept="image/*"
          onChange={(event) => pickFromInput(event, "IMAGE_IMPORT")}
          className="sr-only"
          aria-hidden="true"
          tabIndex={-1}
        />
      </div>
    </AppShell>
  );
}
