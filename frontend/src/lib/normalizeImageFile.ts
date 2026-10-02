/**
 * Client-side image normalisation for every capture entry point (camera, gallery picker,
 * clipboard button, `paste` event, drop) — docs/architecture/04-classification-flow.md
 * § Capture Entry Points, ADR-014 decision 5.
 *
 * The backend accepts only JPEG, PNG and WebP and deliberately does no conversion, so anything
 * else a phone can hand over (HEIC/HEIF from some Android galleries or the iOS Files app, GIF,
 * BMP, AVIF, an empty type…) is decoded here and re-encoded as JPEG. The three accepted types pass
 * through untouched (original bytes, no recompression).
 */

/** Matches the backend's multipart limit and nginx's `client_max_body_size 20m`. */
export const MAX_IMAGE_BYTES = 20 * 1024 * 1024;
/** Longer side after re-encoding; keeps the canvas inside mobile browsers' memory limits. */
export const MAX_IMAGE_DIMENSION = 4096;
export const JPEG_QUALITY = 0.92;

const PASS_THROUGH_TYPES: ReadonlySet<string> = new Set(["image/jpeg", "image/png", "image/webp"]);

export const IMAGE_FORMAT_ERROR_MESSAGE =
  "Nie udało się odczytać tego formatu obrazu (np. HEIC). Wybierz zdjęcie JPEG/PNG lub zrób zrzut ekranu.";
export const IMAGE_TOO_LARGE_MESSAGE = "Plik jest za duży (maks. 20 MB).";
export const NO_CLIPBOARD_IMAGE_MESSAGE = "W schowku nie ma obrazu.";
export const CLIPBOARD_DENIED_MESSAGE =
  "Brak dostępu do schowka. Wybierz obraz z galerii lub wklej go skrótem Ctrl/Cmd+V.";
export const CLIPBOARD_UNAVAILABLE_MESSAGE =
  "Nie udało się odczytać schowka. Wybierz obraz z galerii.";

export type ImageImportErrorCode =
  | "unreadable-format"
  | "too-large"
  | "no-clipboard-image"
  | "clipboard-denied"
  | "clipboard-unavailable";

const MESSAGES: Record<ImageImportErrorCode, string> = {
  "unreadable-format": IMAGE_FORMAT_ERROR_MESSAGE,
  "too-large": IMAGE_TOO_LARGE_MESSAGE,
  "no-clipboard-image": NO_CLIPBOARD_IMAGE_MESSAGE,
  "clipboard-denied": CLIPBOARD_DENIED_MESSAGE,
  "clipboard-unavailable": CLIPBOARD_UNAVAILABLE_MESSAGE,
};

/** Typed failure of the import path; `message` is the Polish text to show the user as-is. */
export class ImageImportError extends Error {
  readonly code: ImageImportErrorCode;

  constructor(code: ImageImportErrorCode) {
    super(MESSAGES[code]);
    this.name = "ImageImportError";
    this.code = code;
  }
}

/** Polish message for anything thrown while importing an image. */
export function describeImportError(error: unknown): string {
  return error instanceof ImageImportError ? error.message : IMAGE_FORMAT_ERROR_MESSAGE;
}

/** "IMG_0042.HEIC" -> "IMG_0042.jpg"; a missing/blank name becomes "image.jpg". */
function jpegFileName(originalName: string): string {
  const base = originalName.replace(/\.[^./\\]+$/, "").trim();
  return `${base || "image"}.jpg`;
}

function assertWithinSizeLimit(file: Blob): void {
  if (file.size > MAX_IMAGE_BYTES) throw new ImageImportError("too-large");
}

function canvasToJpegBlob(canvas: HTMLCanvasElement): Promise<Blob> {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new ImageImportError("unreadable-format"))),
      "image/jpeg",
      JPEG_QUALITY,
    );
  });
}

async function reencodeAsJpeg(file: File): Promise<File> {
  if (typeof createImageBitmap !== "function") throw new ImageImportError("unreadable-format");

  let bitmap: ImageBitmap;
  try {
    // No options: current browsers apply the EXIF orientation by default.
    bitmap = await createImageBitmap(file);
  } catch {
    throw new ImageImportError("unreadable-format");
  }

  try {
    const scale = Math.min(1, MAX_IMAGE_DIMENSION / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));

    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const context = canvas.getContext("2d");
    if (!context) throw new ImageImportError("unreadable-format");

    // JPEG has no alpha channel — paint white first so transparent PNG/GIF/WebP-like sources
    // do not turn black.
    context.fillStyle = "#ffffff";
    context.fillRect(0, 0, width, height);
    context.drawImage(bitmap, 0, 0, width, height);

    const blob = await canvasToJpegBlob(canvas);
    return new File([blob], jpegFileName(file.name), {
      type: "image/jpeg",
      lastModified: file.lastModified,
    });
  } catch (error) {
    throw error instanceof ImageImportError ? error : new ImageImportError("unreadable-format");
  } finally {
    bitmap.close();
  }
}

/**
 * Returns a file the backend will accept: JPEG/PNG/WebP unchanged (same object), anything else
 * re-encoded as JPEG. Rejects with an {@link ImageImportError} when the image cannot be decoded
 * (`unreadable-format`) or is larger than 20 MB after this step (`too-large`).
 */
export async function normalizeImageFile(file: File): Promise<File> {
  const normalized = PASS_THROUGH_TYPES.has(file.type) ? file : await reencodeAsJpeg(file);
  assertWithinSizeLimit(normalized);
  return normalized;
}
