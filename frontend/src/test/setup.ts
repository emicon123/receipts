import { cleanup } from "@testing-library/react";
import { afterEach, beforeEach, vi } from "vitest";

// jsdom implements neither of these; the capture screen needs both. Fresh stubs per test so call
// counts never leak between tests. Tests that care override them (see test/utils.tsx).
beforeEach(() => {
  URL.createObjectURL = vi.fn(() => "blob:mock-preview");
  URL.revokeObjectURL = vi.fn();
  window.matchMedia = vi.fn((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }));
});

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
