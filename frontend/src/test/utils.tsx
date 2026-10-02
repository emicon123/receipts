import { QueryClient } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { vi } from "vitest";
import { TestProviders } from "@/test/TestProviders";

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

export function renderWithProviders(ui: ReactElement, route = "/") {
  const client = createTestQueryClient();
  return {
    client,
    ...render(
      <TestProviders client={client} route={route}>
        {ui}
      </TestProviders>,
    ),
  };
}

/** Pretend the primary pointer is (or is not) a mouse/trackpad, i.e. a desktop. */
export function setFinePointer(isFine: boolean): void {
  window.matchMedia = vi.fn((query: string) => ({
    matches: isFine && query === "(pointer: fine)",
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
}

/** Install (or, with `undefined`, remove) `navigator.clipboard` — jsdom has none, like plain HTTP. */
export function setClipboard(clipboard: Partial<Clipboard> | undefined): void {
  Object.defineProperty(navigator, "clipboard", {
    value: clipboard,
    configurable: true,
    writable: true,
  });
}

/** A `ClipboardItem`-like object holding one blob per MIME type. */
export function clipboardItem(types: Record<string, Blob>): ClipboardItem {
  return {
    types: Object.keys(types),
    getType: (type: string) => {
      const blob = types[type];
      return blob ? Promise.resolve(blob) : Promise.reject(new Error(`no ${type}`));
    },
    presentationStyle: "unspecified",
  } as ClipboardItem;
}

export interface ImageDecodingMock {
  bitmap: { width: number; height: number; close: ReturnType<typeof vi.fn> };
  context: {
    fillStyle: string;
    fillRect: ReturnType<typeof vi.fn>;
    drawImage: ReturnType<typeof vi.fn>;
  };
  canvases: HTMLCanvasElement[];
  toBlob: ReturnType<typeof vi.fn>;
}

/**
 * jsdom has no `createImageBitmap` and no canvas backend, so the JPEG re-encode path of
 * `normalizeImageFile` runs against these stand-ins. `toBlob` returns a small fake JPEG.
 */
export function mockImageDecoding(width: number, height: number): ImageDecodingMock {
  const bitmap = { width, height, close: vi.fn() };
  vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue(bitmap));

  const context = { fillStyle: "", fillRect: vi.fn(), drawImage: vi.fn() };
  const canvases: HTMLCanvasElement[] = [];
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockImplementation(function (
    this: HTMLCanvasElement,
  ) {
    canvases.push(this);
    return context as unknown as CanvasRenderingContext2D;
  } as never);

  const toBlob = vi.fn((callback: BlobCallback, type?: string) => {
    callback(new Blob(["jpeg-bytes"], { type }));
  });
  vi.spyOn(HTMLCanvasElement.prototype, "toBlob").mockImplementation(toBlob as never);

  return { bitmap, context, canvases, toBlob };
}
