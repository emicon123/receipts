import { fireEvent, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiClient } from "@/lib/api";
import {
  CLIPBOARD_DENIED_MESSAGE,
  IMAGE_FORMAT_ERROR_MESSAGE,
  IMAGE_TOO_LARGE_MESSAGE,
  MAX_IMAGE_BYTES,
  NO_CLIPBOARD_IMAGE_MESSAGE,
} from "@/lib/normalizeImageFile";
import { receiptsKeys } from "@/lib/queryKeys";
import { CaptureRoute } from "@/routes/CaptureRoute";
import { pngFile, receiptSummary } from "@/test/fixtures";
import {
  clipboardItem,
  mockImageDecoding,
  renderWithProviders,
  setClipboard,
  setFinePointer,
} from "@/test/utils";

const PREVIEW_ALT = "Podgląd obrazu paragonu";

let post: ReturnType<typeof mockPost>;

function mockPost() {
  return vi.spyOn(apiClient, "post").mockResolvedValue({
    data: { data: receiptSummary({ source: "IMAGE_IMPORT" }) },
  } as never);
}

beforeEach(() => {
  post = mockPost();
});

function renderCapture() {
  const view = renderWithProviders(
    <Routes>
      <Route path="/" element={<CaptureRoute />} />
      <Route path="/receipts" element={<p>Lista paragonów</p>} />
    </Routes>,
  );
  const invalidate = vi.spyOn(view.client, "invalidateQueries");
  return { ...view, invalidate };
}

function fileInputs() {
  const inputs = Array.from(document.querySelectorAll<HTMLInputElement>('input[type="file"]'));
  const camera = inputs.find((input) => input.hasAttribute("capture"));
  const gallery = inputs.find((input) => !input.hasAttribute("capture"));
  if (!camera || !gallery) throw new Error("expected a camera and a gallery file input");
  return { camera, gallery };
}

function button(name: string) {
  return screen.getByRole("button", { name });
}

async function findPreview() {
  return screen.findByRole("img", { name: PREVIEW_ALT });
}

function sentForm(): FormData {
  return post.mock.calls[0]?.[1] as FormData;
}

function sentFile(): File {
  return sentForm().get("image") as File;
}

function textOnlyPaste() {
  return {
    clipboardData: {
      files: [],
      items: [{ kind: "string", type: "text/plain", getAsFile: () => null }],
    },
  };
}

describe("CaptureRoute — layout", () => {
  it("shows the Dodaj paragon title, the camera button and the prominent gallery button", () => {
    renderCapture();

    expect(screen.getByRole("heading", { level: 1, name: "Dodaj paragon" })).toBeTruthy();
    expect(button("Otwórz aparat, aby zrobić zdjęcie paragonu")).toBeTruthy();
    expect(button("Wybierz z galerii")).toBeTruthy();
  });

  it("has a camera input with capture and a gallery input WITHOUT capture or multiple", () => {
    renderCapture();
    const { camera, gallery } = fileInputs();

    expect(camera.getAttribute("accept")).toBe("image/*");
    expect(camera.getAttribute("capture")).toBe("environment");
    expect(camera.multiple).toBe(false);

    expect(gallery.getAttribute("accept")).toBe("image/*");
    expect(gallery.hasAttribute("capture")).toBe(false);
    expect(gallery.multiple).toBe(false);
  });

  it("opens the matching file input from each button", async () => {
    const user = userEvent.setup();
    renderCapture();
    const { camera, gallery } = fileInputs();
    const cameraClick = vi.spyOn(camera, "click");
    const galleryClick = vi.spyOn(gallery, "click");

    await user.click(button("Wybierz z galerii"));
    expect(galleryClick).toHaveBeenCalledTimes(1);
    expect(cameraClick).not.toHaveBeenCalled();

    await user.click(button("Otwórz aparat, aby zrobić zdjęcie paragonu"));
    expect(cameraClick).toHaveBeenCalledTimes(1);
  });

  it("shows the Ctrl/Cmd+V hint only on fine-pointer (desktop) devices", () => {
    setFinePointer(false);
    const phone = renderCapture();
    expect(screen.queryByText(/Ctrl\/Cmd\+V/)).toBeNull();
    phone.unmount();

    setFinePointer(true);
    renderCapture();
    expect(
      screen.getByText("Na komputerze możesz też wkleić obraz skrótem Ctrl/Cmd+V."),
    ).toBeTruthy();
  });
});

describe("CaptureRoute — gallery pick flow", () => {
  it("previews a picked PNG (passed through unchanged), then uploads it as an IMAGE_IMPORT", async () => {
    const user = userEvent.setup();
    const { invalidate } = renderCapture();
    const { gallery } = fileInputs();
    const screenshot = pngFile("Screenshot_2026.png");

    await user.upload(gallery, screenshot);

    expect(await findPreview()).toBeTruthy();
    // Original bytes, no recompression: the very same File object backs the preview.
    expect(URL.createObjectURL).toHaveBeenCalledWith(screenshot);
    // The picker is reset so the same file can be chosen again.
    expect(gallery.files?.length).toBe(0);
    // Nothing is uploaded before the confirmation step.
    expect(post).not.toHaveBeenCalled();

    await user.click(button("Zatwierdź"));

    await screen.findByText("Lista paragonów");
    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0]?.[0]).toBe("/receipts/image-import");
    expect(sentFile()).toBe(screenshot);
    expect(sentForm().has("capturedAt")).toBe(false);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: receiptsKeys.all });
  });

  it("converts a HEIC gallery photo to JPEG before showing the preview and uploading it", async () => {
    const user = userEvent.setup();
    mockImageDecoding(4032, 3024);
    renderCapture();
    const { gallery } = fileInputs();

    await user.upload(gallery, new File(["heic"], "IMG_0001.HEIC", { type: "image/heic" }));

    expect(await findPreview()).toBeTruthy();
    expect(URL.createObjectURL).toHaveBeenCalledWith(
      expect.objectContaining({ name: "IMG_0001.jpg", type: "image/jpeg" }),
    );

    await user.click(button("Zatwierdź"));
    await screen.findByText("Lista paragonów");

    expect(post.mock.calls[0]?.[0]).toBe("/receipts/image-import");
    expect(sentFile().name).toBe("IMG_0001.jpg");
    expect(sentFile().type).toBe("image/jpeg");
  });

  it("shows the Polish format error and creates no draft when the image cannot be decoded", async () => {
    const user = userEvent.setup();
    vi.stubGlobal("createImageBitmap", vi.fn().mockRejectedValue(new Error("cannot decode")));
    renderCapture();
    const { gallery } = fileInputs();

    await user.upload(gallery, new File(["heic"], "IMG_0001.heic", { type: "image/heic" }));

    expect((await screen.findByRole("alert")).textContent).toBe(IMAGE_FORMAT_ERROR_MESSAGE);
    expect(screen.queryByRole("img", { name: PREVIEW_ALT })).toBeNull();
    expect(button("Wybierz z galerii")).toBeTruthy(); // never a dead end
    expect(post).not.toHaveBeenCalled();
  });

  it("rejects a file over 20 MB client-side", async () => {
    const user = userEvent.setup();
    renderCapture();
    const { gallery } = fileInputs();
    const huge = pngFile("huge.png");
    Object.defineProperty(huge, "size", { value: MAX_IMAGE_BYTES + 1 });

    await user.upload(gallery, huge);

    expect((await screen.findByRole("alert")).textContent).toBe(IMAGE_TOO_LARGE_MESSAGE);
    expect(screen.queryByRole("img", { name: PREVIEW_ALT })).toBeNull();
    expect(post).not.toHaveBeenCalled();
  });

  it("decodes a picked file with an empty type instead of passing it through", async () => {
    const user = userEvent.setup({ applyAccept: false });
    mockImageDecoding(100, 100);
    renderCapture();
    const { gallery } = fileInputs();

    await user.upload(gallery, new File(["x"], "mystery", { type: "" }));

    await findPreview();
    expect(URL.createObjectURL).toHaveBeenCalledWith(
      expect.objectContaining({ name: "mystery.jpg", type: "image/jpeg" }),
    );
  });

  it("lets the user retake: discards the draft, frees the preview and shows the actions again", async () => {
    const user = userEvent.setup();
    renderCapture();
    const { gallery } = fileInputs();
    await user.upload(gallery, pngFile());
    await findPreview();

    await user.click(button("Powtórz"));

    expect(screen.queryByRole("img", { name: PREVIEW_ALT })).toBeNull();
    expect(button("Wybierz z galerii")).toBeTruthy();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith("blob:mock-preview");
    expect(post).not.toHaveBeenCalled();
  });

  it("blocks the buttons and shows a status while a non-JPEG image is being converted", async () => {
    const user = userEvent.setup();
    mockImageDecoding(100, 100);
    let finishDecoding: (bitmap: unknown) => void = () => {};
    vi.stubGlobal(
      "createImageBitmap",
      vi.fn(
        () =>
          new Promise((resolve) => {
            finishDecoding = resolve;
          }),
      ),
    );
    renderCapture();
    const { gallery } = fileInputs();

    await user.upload(gallery, new File(["h"], "a.heic", { type: "image/heic" }));

    expect((await screen.findByRole("status")).textContent).toBe("Przygotowuję obraz…");
    expect((button("Wybierz z galerii") as HTMLButtonElement).disabled).toBe(true);

    finishDecoding({ width: 10, height: 10, close: vi.fn() });
    await findPreview();
    expect(screen.queryByRole("status")).toBeNull();
  });
});

describe("CaptureRoute — camera path", () => {
  it("still uploads a camera photo to POST /receipts with capturedAt", async () => {
    const user = userEvent.setup();
    const { invalidate } = renderCapture();
    const { camera } = fileInputs();
    const photo = new File(["jpeg"], "IMG_9999.jpg", { type: "image/jpeg" });

    await user.upload(camera, photo);
    await findPreview();
    await user.click(button("Zatwierdź"));

    await screen.findByText("Lista paragonów");
    expect(post.mock.calls[0]?.[0]).toBe("/receipts");
    expect(sentFile()).toBe(photo);
    expect(Number.isNaN(Date.parse(String(sentForm().get("capturedAt"))))).toBe(false);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: receiptsKeys.all });
  });
});

describe("CaptureRoute — paste event (Ctrl/Cmd+V)", () => {
  it("turns a pasted screenshot into a named IMAGE_IMPORT draft and swallows the event", async () => {
    const user = userEvent.setup();
    setClipboard(undefined); // plain-HTTP situation: no async Clipboard API at all
    renderCapture();

    const notPrevented = fireEvent.paste(document.body, {
      clipboardData: { files: [pngFile("image.png")], items: [] },
    });

    expect(notPrevented).toBe(false);
    await findPreview();

    await user.click(button("Zatwierdź"));
    await screen.findByText("Lista paragonów");
    expect(post.mock.calls[0]?.[0]).toBe("/receipts/image-import");
    expect(sentFile().type).toBe("image/png");
    expect(sentFile().name).toMatch(/^import-\d{8}-\d{6}\.png$/);
  });

  it("falls back to clipboardData.items when files is empty", async () => {
    renderCapture();
    const blob = pngFile("blob");

    fireEvent.paste(document.body, {
      clipboardData: {
        files: [],
        items: [{ kind: "file", type: "image/png", getAsFile: () => blob }],
      },
    });

    await findPreview();
  });

  it("ignores a text-only paste", () => {
    renderCapture();

    const notPrevented = fireEvent.paste(document.body, textOnlyPaste());

    expect(notPrevented).toBe(true);
    expect(screen.queryByRole("img", { name: PREVIEW_ALT })).toBeNull();
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("converts a pasted non-JPEG/PNG/WebP image like any other path", async () => {
    mockImageDecoding(300, 200);
    renderCapture();

    fireEvent.paste(document.body, {
      clipboardData: {
        files: [new File(["g"], "anim.gif", { type: "image/gif" })],
        items: [],
      },
    });

    await findPreview();
    expect(URL.createObjectURL).toHaveBeenCalledWith(
      expect.objectContaining({ type: "image/jpeg" }),
    );
  });

  it("stops listening once a draft is showing, and listens again after Powtórz", async () => {
    const user = userEvent.setup();
    renderCapture();
    const { gallery } = fileInputs();
    await user.upload(gallery, pngFile());
    await findPreview();

    const whileDraft = fireEvent.paste(document.body, {
      clipboardData: { files: [pngFile("other.png")], items: [] },
    });
    expect(whileDraft).toBe(true); // not handled, not prevented

    await user.click(button("Powtórz"));
    const afterRetake = fireEvent.paste(document.body, {
      clipboardData: { files: [pngFile("again.png")], items: [] },
    });
    expect(afterRetake).toBe(false);
    await findPreview();
  });

  it("stops listening after the screen unmounts", () => {
    const { unmount } = renderCapture();
    unmount();

    const notPrevented = fireEvent.paste(document.body, {
      clipboardData: { files: [pngFile()], items: [] },
    });

    expect(notPrevented).toBe(true);
  });
});

describe("CaptureRoute — Wklej ze schowka button", () => {
  it("is hidden when the async Clipboard API is unavailable (plain HTTP) — gallery still works", async () => {
    const user = userEvent.setup();
    setClipboard(undefined);
    renderCapture();

    expect(screen.queryByRole("button", { name: "Wklej ze schowka" })).toBeNull();
    expect(button("Wybierz z galerii")).toBeTruthy();
    expect(button("Otwórz aparat, aby zrobić zdjęcie paragonu")).toBeTruthy();

    await user.upload(fileInputs().gallery, pngFile());
    await findPreview();
  });

  it("is hidden when clipboard exists but has no read()", () => {
    setClipboard({ writeText: vi.fn() });
    renderCapture();

    expect(screen.queryByRole("button", { name: "Wklej ze schowka" })).toBeNull();
  });

  it("reads an image from the clipboard, previews it and uploads it as an IMAGE_IMPORT", async () => {
    const user = userEvent.setup();
    const read = vi.fn().mockResolvedValue([
      clipboardItem({
        "text/plain": new Blob(["hi"], { type: "text/plain" }),
        "image/png": new Blob(["png"], { type: "image/png" }),
      }),
    ]);
    setClipboard({ read });
    renderCapture();

    await user.click(button("Wklej ze schowka"));

    await findPreview();
    expect(read).toHaveBeenCalledTimes(1);

    await user.click(button("Zatwierdź"));
    await screen.findByText("Lista paragonów");
    expect(post.mock.calls[0]?.[0]).toBe("/receipts/image-import");
    expect(sentFile().type).toBe("image/png");
    expect(sentFile().name).toMatch(/^import-\d{8}-\d{6}\.png$/);
  });

  it("tells the user when the clipboard holds no image, without a dead end", async () => {
    const user = userEvent.setup();
    setClipboard({
      read: vi
        .fn()
        .mockResolvedValue([clipboardItem({ "text/plain": new Blob(["hi"], { type: "text/plain" }) })]),
    });
    renderCapture();

    await user.click(button("Wklej ze schowka"));

    expect((await screen.findByRole("alert")).textContent).toBe(NO_CLIPBOARD_IMAGE_MESSAGE);
    expect(NO_CLIPBOARD_IMAGE_MESSAGE).toBe("W schowku nie ma obrazu.");
    expect(screen.queryByRole("img", { name: PREVIEW_ALT })).toBeNull();
    expect(button("Wybierz z galerii")).toBeTruthy();
    expect(button("Wklej ze schowka")).toBeTruthy();
  });

  it("shows a hint to use the gallery when clipboard permission is denied", async () => {
    const user = userEvent.setup();
    setClipboard({ read: vi.fn().mockRejectedValue(new DOMException("denied", "NotAllowedError")) });
    renderCapture();

    await user.click(button("Wklej ze schowka"));

    expect((await screen.findByRole("alert")).textContent).toBe(CLIPBOARD_DENIED_MESSAGE);
    expect(screen.queryByRole("img", { name: PREVIEW_ALT })).toBeNull();
  });

  it("clears the previous message on the next successful import", async () => {
    const user = userEvent.setup();
    setClipboard({ read: vi.fn().mockResolvedValue([]) });
    renderCapture();
    await user.click(button("Wklej ze schowka"));
    await screen.findByRole("alert");

    await user.upload(fileInputs().gallery, pngFile());

    await findPreview();
    expect(screen.queryByRole("alert")).toBeNull();
  });
});

describe("CaptureRoute — drop (desktop, optional)", () => {
  it("accepts a dropped image file as an IMAGE_IMPORT draft", async () => {
    renderCapture();

    const notPrevented = fireEvent.drop(button("Wybierz z galerii"), {
      dataTransfer: { types: ["Files"], files: [pngFile("dropped.png")] },
    });

    expect(notPrevented).toBe(false);
    await findPreview();
  });

  it("prevents the browser from navigating to a file dragged over the screen, but not text drags", () => {
    renderCapture();
    const target = button("Wybierz z galerii");

    expect(fireEvent.dragOver(target, { dataTransfer: { types: ["Files"] } })).toBe(false);
    expect(fireEvent.dragOver(target, { dataTransfer: { types: ["text/plain"] } })).toBe(true);
  });
});

describe("CaptureRoute — upload errors", () => {
  async function pickAndAccept() {
    const user = userEvent.setup();
    renderCapture();
    await user.upload(fileInputs().gallery, pngFile());
    await findPreview();
    await user.click(button("Zatwierdź"));
  }

  it("maps a 422 from the server to the format message and stays on the preview", async () => {
    post.mockRejectedValue(new ApiError("Unsupported image type: image/heic", 422, []));

    await pickAndAccept();

    expect((await screen.findByRole("alert")).textContent).toBe(IMAGE_FORMAT_ERROR_MESSAGE);
    expect(screen.getByRole("img", { name: PREVIEW_ALT })).toBeTruthy();
    await waitFor(() => expect((button("Zatwierdź") as HTMLButtonElement).disabled).toBe(false));
    expect(screen.queryByText("Lista paragonów")).toBeNull();
  });

  it("shows the backend's message for other API errors", async () => {
    post.mockRejectedValue(new ApiError("Serwer niedostępny", 503, []));

    await pickAndAccept();

    expect((await screen.findByRole("alert")).textContent).toBe("Serwer niedostępny");
  });

  it("shows a generic Polish message for a non-API failure and lets the user retry", async () => {
    post.mockRejectedValueOnce(new Error("network down"));
    const user = userEvent.setup();
    renderCapture();
    await user.upload(fileInputs().gallery, pngFile());
    await findPreview();
    await user.click(button("Zatwierdź"));

    expect((await screen.findByRole("alert")).textContent).toBe(
      "Wysyłanie nie powiodło się. Spróbuj ponownie.",
    );

    await user.click(button("Zatwierdź"));
    await screen.findByText("Lista paragonów");
    expect(post).toHaveBeenCalledTimes(2);
  });
});
