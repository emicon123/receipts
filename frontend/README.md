# Receipts — Frontend

Mobile-first PWA client for the receipts app: photograph a shopping receipt, review the daily
classifier's per-line-item categorization, and see monthly spend per category. See the repo
root `CLAUDE.md` and `docs/` for the full product/architecture context — this is the single
client, there is no separate desktop/admin build.

## Stack

React 19, TypeScript (strict), Vite 6, Tailwind v4, shadcn/ui-style primitives, TanStack Query
v5, Recharts, react-router-dom, vite-plugin-pwa.

## Develop

```bash
npm install
npm run dev
```

The dev server proxies `/api/*` to `http://localhost:8080` (a locally running backend) — see
`vite.config.ts`. In production, Nginx proxies `/api/*` to the backend container instead; the
frontend always calls relative `/api/...` paths and never hardcodes a host.

## Build

```bash
npm run build   # tsc -b && vite build — output in dist/
npm run preview # serve the production build locally
```

## Structure

```
src/
├── routes/       # capture, receipts list/detail, manual entry, dashboard
├── components/
│   ├── ui/           # shadcn/ui-style primitives (button, input, select, ...)
│   ├── layout/        # AppShell, BottomNav
│   ├── capture/, receipts/, dashboard/
├── hooks/        # TanStack Query hooks (one per query/mutation)
└── lib/
    ├── api.ts        # the only API client — Axios, matches docs/openapi.yaml exactly
    ├── types.ts       # types mirroring the OpenAPI schemas
    ├── queryKeys.ts   # TanStack Query key factories
    └── utils.ts       # cn() + formatting helpers
```
