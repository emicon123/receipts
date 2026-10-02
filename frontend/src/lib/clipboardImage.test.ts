import { describe, expect, it, vi } from "vitest";
import {
  hasFinePointer,
  imageFromClipboardData,
  isClipboardReadSupported,
  pastedImageName,
  readClipboardImage,
} from "@/lib/clipboardImage";
import { ImageImportError } from "@/lib/normalizeImageFile";
import { clipboardItem, setClipboard, setFinePointer } from "@/test/utils";

function pngBlob(): Blob {
  return new Blob(["png-bytes"], { type: "image/png" });
}

/** The parts of a `paste` event's DataTransfer the helper reads. */
function clipboardData(
  files: File[],
  items: Array<{ kind: string; type: string; getAsFile: () => File | null }> = [],
): DataTransfer {
  return { files, items } as unknown as DataTransfer;
}

describe("pastedImageName", () => {
  const now = new Date(2026, 9, 2, 14, 30, 5);

  it.each([
    ["image/png", "import-20261002-143005.png"],
    ["image/jpeg", "import-20261002-143005.jpg"],
    ["image/webp", "import-20261002-143005.webp"],
    ["image/heic", "import-20261002-143005.heic"],
    ["", "import-20261002-143005.png"],
  ])("names a %s blob %s", (type, expected) => {
    expect(pastedImageName(type, now)).toBe(expected);
  });
});

describe("imageFromClipboardData", () => {
  it("returns null without clipboard data", () => {
    expect(imageFromClipboardData(null)).toBeNull();
  });

  it("returns null for a text-only paste", () => {
    const text = { kind: "string", type: "text/plain", getAsFile: () => null };
    expect(imageFromClipboardData(clipboardData([], [text]))).toBeNull();
  });

  it("takes the first image file, renames it and keeps its real MIME type", () => {
    const doc = new File(["x"], "notes.pdf", { type: "application/pdf" });
    const shot = new File(["x"], "image.png", { type: "image/png" });

    const result = imageFromClipboardData(clipboardData([doc, shot]));

    expect(result?.type).toBe("image/png");
    expect(result?.name).toMatch(/^import-\d{8}-\d{6}\.png$/);
  });

  it("falls back to image items when `files` is empty", () => {
    const blob = new File(["x"], "blob", { type: "image/webp" });
    const items = [
      { kind: "string", type: "text/plain", getAsFile: () => null },
      { kind: "file", type: "image/webp", getAsFile: () => blob },
    ];

    const result = imageFromClipboardData(clipboardData([], items));

    expect(result?.type).toBe("image/webp");
    expect(result?.name).toMatch(/\.webp$/);
  });
});

describe("isClipboardReadSupported", () => {
  it("is false when navigator.clipboard is missing (plain HTTP)", () => {
    setClipboard(undefined);
    expect(isClipboardReadSupported()).toBe(false);
  });

  it("is false when the clipboard has no read() (e.g. writeText only)", () => {
    setClipboard({ writeText: vi.fn() });
    expect(isClipboardReadSupported()).toBe(false);
  });

  it("is true when read() exists", () => {
    setClipboard({ read: vi.fn() });
    expect(isClipboardReadSupported()).toBe(true);
  });
});

describe("hasFinePointer", () => {
  it("reflects the (pointer: fine) media query", () => {
    setFinePointer(true);
    expect(hasFinePointer()).toBe(true);
    setFinePointer(false);
    expect(hasFinePointer()).toBe(false);
  });

  it("is false when matchMedia does not exist", () => {
    // @ts-expect-error simulating an environment without matchMedia
    window.matchMedia = undefined;
    expect(hasFinePointer()).toBe(false);
  });
});

describe("readClipboardImage", () => {
  it("returns the first image/* blob as a named import file", async () => {
    setClipboard({
      read: vi.fn().mockResolvedValue([
        clipboardItem({ "text/plain": new Blob(["hi"], { type: "text/plain" }) }),
        clipboardItem({ "text/html": new Blob(["<b>"]), "image/png": pngBlob() }),
      ]),
    });

    const file = await readClipboardImage();

    expect(file.type).toBe("image/png");
    expect(file.name).toMatch(/^import-\d{8}-\d{6}\.png$/);
  });

  it("rejects with no-clipboard-image when nothing on the clipboard is an image", async () => {
    setClipboard({
      read: vi
        .fn()
        .mockResolvedValue([clipboardItem({ "text/plain": new Blob(["hi"], { type: "text/plain" }) })]),
    });

    await expect(readClipboardImage()).rejects.toMatchObject({ code: "no-clipboard-image" });
  });

  it("rejects with no-clipboard-image for an empty clipboard", async () => {
    setClipboard({ read: vi.fn().mockResolvedValue([]) });

    await expect(readClipboardImage()).rejects.toMatchObject({ code: "no-clipboard-image" });
  });

  it("rejects with clipboard-denied when permission is refused", async () => {
    setClipboard({ read: vi.fn().mockRejectedValue(new DOMException("denied", "NotAllowedError")) });

    await expect(readClipboardImage()).rejects.toMatchObject({ code: "clipboard-denied" });
  });

  it("rejects with clipboard-unavailable on any other failure", async () => {
    setClipboard({ read: vi.fn().mockRejectedValue(new Error("boom")) });

    await expect(readClipboardImage()).rejects.toMatchObject({ code: "clipboard-unavailable" });
  });

  it("rejects with a typed error (not a TypeError) when the API does not exist", async () => {
    setClipboard(undefined);

    await expect(readClipboardImage()).rejects.toBeInstanceOf(ImageImportError);
    await expect(readClipboardImage()).rejects.toMatchObject({ code: "clipboard-unavailable" });
  });
});
