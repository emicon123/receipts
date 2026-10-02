import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api";
import { IMAGE_FORMAT_ERROR_MESSAGE } from "@/lib/normalizeImageFile";
import { describeUploadError } from "@/lib/uploadError";

describe("describeUploadError", () => {
  it("maps a 422 (unsupported image type) to the format message", () => {
    const error = new ApiError("Unsupported image type: image/heic", 422, []);

    expect(describeUploadError(error)).toBe(IMAGE_FORMAT_ERROR_MESSAGE);
  });

  it("shows the backend's message for other API errors", () => {
    expect(describeUploadError(new ApiError("Brak miejsca", 500, []))).toBe("Brak miejsca");
  });

  it("uses a generic Polish message for anything else", () => {
    expect(describeUploadError(new Error("x"))).toBe("Wysyłanie nie powiodło się. Spróbuj ponownie.");
  });
});
