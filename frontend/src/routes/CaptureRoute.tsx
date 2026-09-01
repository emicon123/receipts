import { Camera } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { CapturePreview } from "@/components/capture/CapturePreview";
import { AppShell } from "@/components/layout/AppShell";
import { Button } from "@/components/ui/button";
import { useUploadReceipt } from "@/hooks/useUploadReceipt";
import { ApiError } from "@/lib/api";

export function CaptureRoute() {
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const upload = useUploadReceipt();

  // Revoke the object URL whenever we drop it, so preview blobs don't leak.
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  function handleFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const selected = event.target.files?.[0];
    if (!selected) return;
    setFile(selected);
    setPreviewUrl(URL.createObjectURL(selected));
    upload.reset();
    // Allow re-selecting the exact same file next time (retake -> same photo).
    event.target.value = "";
  }

  function handleRetake() {
    if (previewUrl) URL.revokeObjectURL(previewUrl);
    setFile(null);
    setPreviewUrl(null);
    upload.reset();
  }

  function handleAccept() {
    if (!file) return;
    upload.mutate(
      { image: file, capturedAt: new Date() },
      {
        onSuccess: () => {
          if (previewUrl) URL.revokeObjectURL(previewUrl);
          setFile(null);
          setPreviewUrl(null);
          navigate("/receipts");
        },
      },
    );
  }

  return (
    <AppShell title="Zrób zdjęcie paragonu">
      <div className="flex min-h-full flex-col gap-4">
        {upload.isError && (
          <p role="alert" className="rounded-lg bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {upload.error instanceof ApiError
              ? upload.error.message
              : "Wysyłanie nie powiodło się. Spróbuj ponownie."}
          </p>
        )}

        {previewUrl ? (
          <CapturePreview
            previewUrl={previewUrl}
            isUploading={upload.isPending}
            onRetake={handleRetake}
            onAccept={handleAccept}
          />
        ) : (
          <div className="flex flex-1 flex-col items-center justify-center gap-6 text-center">
            <div>
              <p className="text-lg font-semibold">Zrób zdjęcie paragonu</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Dotknij przycisku, zrób wyraźne zdjęcie całego paragonu i potwierdź, że wygląda
                dobrze, zanim zostanie wysłane.
              </p>
            </div>
            <Button
              type="button"
              size="icon"
              className="size-28 rounded-full [&_svg]:size-11"
              onClick={() => inputRef.current?.click()}
              aria-label="Otwórz aparat, aby zrobić zdjęcie paragonu"
            >
              <Camera />
            </Button>
          </div>
        )}

        <input
          ref={inputRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={handleFileChange}
          className="sr-only"
          aria-hidden="true"
          tabIndex={-1}
        />
      </div>
    </AppShell>
  );
}
