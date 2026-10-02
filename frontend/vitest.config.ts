import path from "node:path";
import { defineConfig } from "vitest/config";

// Kept separate from vite.config.ts on purpose: the app config carries the PWA plugin, Tailwind
// and a production-only `base`, none of which the unit/component tests need.
export default defineConfig({
  resolve: {
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
  },
});
