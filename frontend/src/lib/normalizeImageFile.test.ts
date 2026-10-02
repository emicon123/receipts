import { describe, expect, it, vi } from "vitest";
import { mockImageDecoding } from "@/test/utils";
import {
  CLIPBOARD_DENIED_MESSAGE,
  IMAGE_FORMAT_ERROR_MESSAGE,
  IMAGE_TOO_LARGE_MESSAGE,
  ImageImportError,
  JPEG_QUALITY,
  MAX_IMAGE_BYTES,
  MAX_IMAGE_DIMENSION,
  NO_CLIPBOARD_IMAGE_MESSAGE,
  describeImportError,
  normalizeImageFile,
} from "@/lib/normalizeImageFile";

function imageFile(name: string, type: string, content = "bytes"): File {
  return new File([content], name, { type });
}

function withSize(file: File, size: number): File {
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("normalizeImageFile", () => {
  describe.each(["image/jpeg", "image/png", "image/webp"])("pass-through of %s", (type) => {
    it("returns the very same file without decoding or recompressing it", async () => {
      const decoding = mockImageDecoding(100, 100);
      const file = imageFile("shot", type);

      const result = await normalizeImageFile(file);

      expect(result).toBe(file);
      expect(globalThis.createImageBitmap).not.toHaveBeenCalled();
      expect(decoding.toBlob).not.toHaveBeenCalled();
    });
  });

  it("re-encodes a HEIC photo as JPEG over a white background and renames it", async () => {
    const decoding = mockImageDecoding(3000, 2000);
    const heic = imageFile("IMG_0042.HEIC", "image/heic");

    const result = await normalizeImageFile(heic);

    expect(globalThis.createImageBitmap).toHaveBeenCalledWith(heic);
    expect(result).not.toBe(heic);
    expect(result.type).toBe("image/jpeg");
    expect(result.name).toBe("IMG_0042.jpg");

    // Under the 4096 px cap: size kept as is.
    expect(decoding.canvases[0]?.width).toBe(3000);
    expect(decoding.canvases[0]?.height).toBe(2000);
    expect(decoding.toBlob.mock.calls[0]?.[1]).toBe("image/jpeg");
    expect(decoding.toBlob.mock.calls[0]?.[2]).toBe(JPEG_QUALITY);

    // White fill happens before the image is painted, so transparency never turns black.
    expect(decoding.context.fillStyle).toBe("#ffffff");
    const fillOrder = decoding.context.fillRect.mock.invocationCallOrder[0] ?? Infinity;
    const drawOrder = decoding.context.drawImage.mock.invocationCallOrder[0] ?? -Infinity;
    expect(fillOrder).toBeLessThan(drawOrder);
    expect(decoding.context.drawImage).toHaveBeenCalledWith(decoding.bitmap, 0, 0, 3000, 2000);
    expect(decoding.bitmap.close).toHaveBeenCalledTimes(1);
  });

  it("scales a large landscape image so its longer side is 4096 px, keeping the aspect ratio", async () => {
    const decoding = mockImageDecoding(8192, 6144);

    await normalizeImageFile(imageFile("big.avif", "image/avif"));

    expect(decoding.canvases[0]?.width).toBe(MAX_IMAGE_DIMENSION);
    expect(decoding.canvases[0]?.height).toBe(3072);
    expect(decoding.context.drawImage).toHaveBeenCalledWith(decoding.bitmap, 0, 0, 4096, 3072);
  });

  it("scales a tall screenshot by its height", async () => {
    const decoding = mockImageDecoding(1170, 8000);

    await normalizeImageFile(imageFile("long.bmp", "image/bmp"));

    expect(decoding.canvases[0]?.height).toBe(MAX_IMAGE_DIMENSION);
    expect(decoding.canvases[0]?.width).toBe(Math.round((1170 * 4096) / 8000));
  });

  it("decodes a file with an empty type instead of passing it through", async () => {
    mockImageDecoding(10, 10);

    const result = await normalizeImageFile(imageFile("clip", ""));

    expect(globalThis.createImageBitmap).toHaveBeenCalled();
    expect(result.type).toBe("image/jpeg");
    expect(result.name).toBe("clip.jpg");
  });

  it("names a nameless file image.jpg", async () => {
    mockImageDecoding(10, 10);

    const result = await normalizeImageFile(imageFile("", "image/gif"));

    expect(result.name).toBe("image.jpg");
  });

  describe("decode failure", () => {
    it("throws a typed unreadable-format error with the Polish message when decoding fails", async () => {
      vi.stubGlobal("createImageBitmap", vi.fn().mockRejectedValue(new DOMException("bad", "InvalidStateError")));

      const failure = await normalizeImageFile(imageFile("a.heic", "image/heic")).catch(
        (error: unknown) => error,
      );

      expect(failure).toBeInstanceOf(ImageImportError);
      expect((failure as ImageImportError).code).toBe("unreadable-format");
      expect((failure as ImageImportError).message).toBe(IMAGE_FORMAT_ERROR_MESSAGE);
      expect(IMAGE_FORMAT_ERROR_MESSAGE).toBe(
        "Nie udało się odczytać tego formatu obrazu (np. HEIC). Wybierz zdjęcie JPEG/PNG lub zrób zrzut ekranu.",
      );
    });

    it("fails the same way when createImageBitmap does not exist", async () => {
      vi.stubGlobal("createImageBitmap", undefined);

      await expect(normalizeImageFile(imageFile("a.heic", "image/heic"))).rejects.toMatchObject({
        code: "unreadable-format",
      });
    });

    it("fails when no 2D canvas context is available, and still releases the bitmap", async () => {
      const decoding = mockImageDecoding(10, 10);
      vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(null);

      await expect(normalizeImageFile(imageFile("a.heic", "image/heic"))).rejects.toMatchObject({
        code: "unreadable-format",
      });
      expect(decoding.bitmap.close).toHaveBeenCalledTimes(1);
    });

    it("fails when the canvas cannot encode a JPEG (toBlob yields null)", async () => {
      const decoding = mockImageDecoding(10, 10);
      decoding.toBlob.mockImplementation((callback: BlobCallback) => callback(null));

      await expect(normalizeImageFile(imageFile("a.heic", "image/heic"))).rejects.toMatchObject({
        code: "unreadable-format",
      });
      expect(decoding.bitmap.close).toHaveBeenCalledTimes(1);
    });
  });

  describe("20 MB limit", () => {
    it("accepts a passed-through file of exactly 20 MB", async () => {
      const file = withSize(imageFile("a.png", "image/png"), MAX_IMAGE_BYTES);

      await expect(normalizeImageFile(file)).resolves.toBe(file);
    });

    it("rejects a passed-through file over 20 MB", async () => {
      const file = withSize(imageFile("a.png", "image/png"), MAX_IMAGE_BYTES + 1);

      const failure = await normalizeImageFile(file).catch((error: unknown) => error);

      expect(failure).toBeInstanceOf(ImageImportError);
      expect((failure as ImageImportError).code).toBe("too-large");
      expect((failure as ImageImportError).message).toBe("Plik jest za duży (maks. 20 MB).");
    });

    it("rejects a converted file that is still over 20 MB", async () => {
      const decoding = mockImageDecoding(10, 10);
      decoding.toBlob.mockImplementation((callback: BlobCallback, type?: string) =>
        callback(new Blob([new Uint8Array(MAX_IMAGE_BYTES + 1)], { type })),
      );

      await expect(normalizeImageFile(imageFile("a.heic", "image/heic"))).rejects.toMatchObject({
        code: "too-large",
      });
    });

    it("accepts a big HEIC whose JPEG re-encode fits (the check is after normalisation)", async () => {
      mockImageDecoding(10, 10);
      const bigHeic = withSize(imageFile("a.heic", "image/heic"), MAX_IMAGE_BYTES * 2);

      const result = await normalizeImageFile(bigHeic);

      expect(result.type).toBe("image/jpeg");
    });
  });
});

describe("describeImportError", () => {
  it("uses the typed error's own Polish message", () => {
    expect(describeImportError(new ImageImportError("too-large"))).toBe(IMAGE_TOO_LARGE_MESSAGE);
    expect(describeImportError(new ImageImportError("no-clipboard-image"))).toBe(
      NO_CLIPBOARD_IMAGE_MESSAGE,
    );
    expect(describeImportError(new ImageImportError("clipboard-denied"))).toBe(
      CLIPBOARD_DENIED_MESSAGE,
    );
  });

  it("falls back to the format message for any unexpected error", () => {
    expect(describeImportError(new Error("boom"))).toBe(IMAGE_FORMAT_ERROR_MESSAGE);
    expect(describeImportError("nope")).toBe(IMAGE_FORMAT_ERROR_MESSAGE);
  });
});
