import { Suspense, lazy } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { CaptureRoute } from "@/routes/CaptureRoute";

// Capture is the PWA's default/home route and the one screen that must load fast on a phone
// (see the frontend agent's mobile-first mandate) — it's imported eagerly above. Everything
// else, especially the Recharts-heavy dashboard, is code-split so the first paint on `/`
// doesn't pay for chart-library weight it doesn't need.
const ReceiptsListRoute = lazy(() =>
  import("@/routes/ReceiptsListRoute").then((m) => ({ default: m.ReceiptsListRoute })),
);
const ReceiptDetailRoute = lazy(() =>
  import("@/routes/ReceiptDetailRoute").then((m) => ({ default: m.ReceiptDetailRoute })),
);
const ManualEntryRoute = lazy(() =>
  import("@/routes/ManualEntryRoute").then((m) => ({ default: m.ManualEntryRoute })),
);
const DashboardRoute = lazy(() =>
  import("@/routes/DashboardRoute").then((m) => ({ default: m.DashboardRoute })),
);

function RouteFallback() {
  return <p className="p-4 text-center text-sm text-muted-foreground">Loading…</p>;
}

export function App() {
  return (
    <Suspense fallback={<RouteFallback />}>
      <Routes>
        <Route path="/" element={<CaptureRoute />} />
        <Route path="/receipts" element={<ReceiptsListRoute />} />
        <Route path="/receipts/manual" element={<ManualEntryRoute />} />
        <Route path="/receipts/:id" element={<ReceiptDetailRoute />} />
        <Route path="/dashboard" element={<DashboardRoute />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}
