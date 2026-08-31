import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { App } from "@/App";
import "@/index.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // Uploading/classifying a receipt is not real-time (see CLAUDE.md — classification is a
      // once-daily batch job), so aggressive refetch-on-focus buys nothing here.
      staleTime: 30 * 1000,
      refetchOnWindowFocus: false,
    },
  },
});

const rootElement = document.getElementById("root");
if (!rootElement) {
  throw new Error("Root element #root not found");
}

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      {/* Reverse-proxied under /paragony/ in production (see vite.config.ts's `base`);
          import.meta.env.BASE_URL tracks the same value so dev (base "/") is unaffected. */}
      <BrowserRouter basename={import.meta.env.BASE_URL}>
        <App />
      </BrowserRouter>
    </QueryClientProvider>
  </StrictMode>,
);
