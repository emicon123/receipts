# Stacked subcategory bars in the Wydatki dashboard ("Szczegóły" view)

User request (2026-09-25): in the Wydatki summary, the single-colour category bars should have a
detail mode where each bar is split into coloured segments, one per subcategory.

Agreed design (confirmed with the user before scoping):
- Toggle on the summary tab: "Podsumowanie" (today's plain bars, unchanged) / "Szczegóły"
  (stacked bars).
- Live data has ~30 distinct subcategory labels (up to 13 in one category) and the same label
  appears under several categories ("Nabiał", "Napoje"), so there is **no global colour per
  subcategory label** and no global legend. Instead, per bar: the **top 3-4 subcategories of that
  category** by amount, as segments in shades of one hue ordered largest-first (darkest = largest),
  plus one neutral grey **"Reszta"** segment for everything else — including line items with a NULL
  subcategory (historical data, ADR-010 no-backfill).
- Tap/hover a segment → tooltip with subcategory name + amount (+ share of the category). Clicking a
  bar/segment still navigates to the existing drill-down route
  (`/dashboard/category/:category?year&month`, ADR-010).
- Bar total must equal today's category total exactly (segments sum to it).

## Architect (first — sequencing gate)

- API: a new lightweight aggregate endpoint, e.g. `GET /api/spending/subcategory-summary?year&month`,
  returning per category the amounts grouped by subcategory (NULL grouped as its own bucket) — do NOT
  make the dashboard fetch `/spending/line-items` for all 11 categories. Decide whether top-N +
  "Reszta" bucketing happens server-side or client-side (recommendation: server returns full
  per-subcategory sums, client does top-N bucketing — N is a presentation choice). Keep
  `/spending/summary` unchanged. Fetched lazily only when "Szczegóły" is selected.
- Normalization: group subcategory labels via the same `lower(trim())` + most-frequent-casing rule
  used by `/receipts/subcategory-labels` (ADR-010 §5), so "Słodycze"/"słodycze " don't split a bar.
- Update `docs/openapi.yaml`, `docs/architecture/05-api-contract.md` (and 02 if relevant), and write
  ADR-013 (check `docs/adr/` for the next free number first — ADR-012 already exists) covering: why
  no global per-label colour, the top-N + Reszta rule, server-vs-client bucketing.

## Backend

- Implement the endpoint per the architect's contract (repository aggregate query, DTOs, controller).
- JUnit + Testcontainers: correct sums per category/subcategory, NULL bucket, casing/whitespace
  normalization, month scoping, and that per-category sums equal `/spending/summary` totals.

## Frontend

- Summary tab toggle "Podsumowanie" / "Szczegóły" (shadcn Tabs or segmented control).
- Stacked horizontal Recharts bars: top-N segments in shades of `--chart-series-1` + grey "Reszta";
  tooltip per segment; click → existing drill-down route. Keep the accessible table fallback
  (extended with subcategory rows or equivalent).
- Loading/error/empty states consistent with existing dashboard hooks; lazy fetch only in "Szczegóły".
- `npm run build` + `npm run lint` clean.

## After implementation

- Backend tests + frontend build/lint, rebuild + redeploy Docker (per CLAUDE.md), smoke-test the new
  endpoint against live data.

## Out of scope

- No changes to classification, prompt, or `/spending/summary`. No commit/push without the user's ask.
