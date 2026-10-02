import { ApiError } from "@/lib/api";
import { IMAGE_FORMAT_ERROR_MESSAGE } from "@/lib/normalizeImageFile";

/**
 * Polish message for a failed image upload. A `422` from either upload endpoint means the server
 * rejected the image type (UnsupportedImageTypeException) — the client normalises to JPEG/PNG/WebP
 * first, so this is a last-resort guard that maps to the same text as a client-side decode failure.
 */
export function describeUploadError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.status === 422 ? IMAGE_FORMAT_ERROR_MESSAGE : error.message;
  }
  return "Wysyłanie nie powiodło się. Spróbuj ponownie.";
}
