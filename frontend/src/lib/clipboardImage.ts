import { format } from "date-fns";
import { ImageImportError } from "@/lib/normalizeImageFile";

/**
 * Clipboard entry points of the capture screen (04-classification-flow.md § Capture Entry Points).
 * Both helpers return an *un-normalised* `File` named `import-<yyyyMMdd-HHmmss>.<ext>` carrying
 * the blob's real MIME type — a pasted blob has no filename and the server only reads the part's
 * content type. Normalisation to a backend-accepted type is `normalizeImageFile`'s job.
 */

const EXTENSION_BY_TYPE: Readonly<Record<string, string>> = {
  "image/png": "png",
  "image/jpeg": "jpg",
  "image/webp": "webp",
};

function extensionFor(mimeType: string): string {
  const known = EXTENSION_BY_TYPE[mimeType];
  if (known) return known;
  const subtype = mimeType.split("/")[1]?.replace(/[^a-z0-9]/gi, "");
  return subtype || "png";
}

/** "import-20261002-143005.png" */
export function pastedImageName(mimeType: string, now: Date = new Date()): string {
  return `import-${format(now, "yyyyMMdd-HHmmss")}.${extensionFor(mimeType)}`;
}

function toImportFile(blob: Blob): File {
  return new File([blob], pastedImageName(blob.type), { type: blob.type });
}

/**
 * The async Clipboard API is absent over plain HTTP (the app may be served that way on the
 * tailnet), in some browsers, and in insecure contexts. Gate the "Wklej ze schowka" button on this.
 */
export function isClipboardReadSupported(): boolean {
  return typeof navigator !== "undefined" && typeof navigator.clipboard?.read === "function";
}

/**
 * Whether a mouse/trackpad (fine pointer) is the primary input, i.e. a desktop where the
 * Ctrl/Cmd+V hint is meaningful; on a phone the hint would only be noise.
 */
export function hasFinePointer(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    ? window.matchMedia("(pointer: fine)").matches
    : false;
}

/**
 * First image in a `paste` event's `clipboardData` (`files` first, then `items`), or `null` for a
 * text-only paste so the caller can leave the event alone.
 */
export function imageFromClipboardData(data: DataTransfer | null): File | null {
  if (!data) return null;

  const file = Array.from(data.files).find((candidate) => candidate.type.startsWith("image/"));
  if (file) return toImportFile(file);

  for (const item of Array.from(data.items)) {
    if (item.kind !== "file" || !item.type.startsWith("image/")) continue;
    const blob = item.getAsFile();
    if (blob) return toImportFile(blob);
  }
  return null;
}

function isPermissionError(error: unknown): boolean {
  return (
    error instanceof DOMException &&
    (error.name === "NotAllowedError" || error.name === "SecurityError")
  );
}

/**
 * Reads the first `image/*` item through `navigator.clipboard.read()`. Rejects with an
 * {@link ImageImportError}: `no-clipboard-image` (nothing image-like on the clipboard),
 * `clipboard-denied` (permission refused, e.g. the iOS "Paste" prompt dismissed) or
 * `clipboard-unavailable` (API missing or any other failure).
 */
export async function readClipboardImage(): Promise<File> {
  if (!isClipboardReadSupported()) throw new ImageImportError("clipboard-unavailable");

  try {
    const items = await navigator.clipboard.read();
    for (const item of items) {
      const imageType = item.types.find((type) => type.startsWith("image/"));
      if (imageType) return toImportFile(await item.getType(imageType));
    }
  } catch (error) {
    throw new ImageImportError(isPermissionError(error) ? "clipboard-denied" : "clipboard-unavailable");
  }
  throw new ImageImportError("no-clipboard-image");
}
