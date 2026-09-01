import path from "node:path";
import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

// https://vite.dev/config/
export default defineConfig(({ command }) => ({
  // Reverse-proxied under /paragony/ by investing-app's shared nginx (see
  // infra/nginx/README or investing-app/infra/nginx/nginx.conf's /paragony/ location) —
  // only for the production build. `npm run dev` stays at "/" so the dev-server proxy
  // below (keyed on "/api") and local testing are unaffected.
  base: command === "build" ? "/paragony/" : "/",
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["favicon.svg", "apple-touch-icon.png"],
      manifest: {
        name: "Paragony",
        short_name: "Paragony",
        description: "Zrób zdjęcie paragonów i śledź wydatki według kategorii.",
        theme_color: "#0f172a",
        background_color: "#0f172a",
        display: "standalone",
        start_url: "/paragony/",
        scope: "/paragony/",
        icons: [
          { src: "pwa-192x192.png", sizes: "192x192", type: "image/png" },
          { src: "pwa-512x512.png", sizes: "512x512", type: "image/png" },
          {
            src: "pwa-512x512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        // App-shell caching only — receipt upload/classification always needs the
        // network, so there is no offline upload queue (out of scope per CLAUDE.md).
        globPatterns: ["**/*.{js,css,html,svg,png,ico,webmanifest}"],
      },
    }),
  ],
  resolve: {
    tsconfigPaths: true,
    alias: {
      "@": path.resolve(import.meta.dirname, "./src"),
    },
  },
  server: {
    forwardConsole: true,
    proxy: {
      // Mirrors the Nginx /api/* proxy used in production so `npm run dev` can talk
      // to a locally running backend without CORS configuration.
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
  devtools: true,
  build: { minify: "oxc" },
}));
