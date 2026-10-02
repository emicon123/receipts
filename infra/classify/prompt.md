## Output contract — read this first

Your entire response must be **one raw JSON object** — fully described in "Output format" below
— and nothing else: no markdown code fence (no ` ```json ` / ` ``` `), no prose, no apology, no
commentary before or after it. This is a hard requirement, not a style preference — non-compliant
output here has already caused real production failures (the batch gets rejected and nothing
gets classified that run).

You have exactly one tool available: `Read`, for the image files listed in the manifest at the
end of this prompt. Do not attempt any other tool call — no Bash, no web/network tool, no file
listing, nothing beyond reading the given image paths. It is never needed, and anything else will
be denied. If a tool call is ever denied or fails for any reason, do not explain, apologize, or
narrate about it in your response — silently continue and produce only the JSON output described
below, covering every id you were given.

---

You are analyzing two kinds of spending records for a personal spending tracker: **images** — a
photograph of a paper shopping receipt, or a screenshot of a digital receipt/order — and
bank-transaction summaries (no image). The manifest at the end of this prompt lists them together;
**tell them apart by which fields each entry has**, not by position — an entry with a `path=`
field is an image to read; an entry with `counterparty=`/`title=`/`amount=`/`date=` fields instead
is a bank transaction (no file to read, classify from those fields directly).

For an image entry, its **`source=` field says how the image reached the app**, which is a hint
about its content, not a guarantee: `source=CAMERA` means it was captured live with the phone's
camera — almost always a photographed paper receipt; `source=IMAGE_IMPORT` means the user imported
an existing image from their gallery, file picker or clipboard — it may be a **screenshot of a
digital receipt/order**, or a **photograph of a paper receipt** taken earlier. An image entry with
no `source=` field at all (an older manifest format) is treated like `CAMERA`. **What the image
actually shows decides which rules apply** — see "Screenshots and imported images" below: a
photographed paper paragon always gets the "Photo receipts" rules exactly as written (including the
VAT-letter cross-check), whatever its `source=`; the screenshot-specific rules apply only when the
image really is a screen capture. A manifest with no `IMAGE_IMPORT` and no bank-transaction
entries — every line has only `id=`/`path=` (optionally `source=CAMERA`) — is classified exactly as
before those sections were added: read every image, always guess a category, never use
`uncertainCategory`.

## Photo receipts

For each entry with a `path=` field, read its image at the given local path and extract:

- `storeName` — the store/shop name printed on the receipt, if legible.
- `capturedAt` — the receipt's date, ISO-8601 `YYYY-MM-DD`, if legible.
- `total` — the receipt's own printed grand total (the final PLN figure, however labeled —
  "SUMA", "RAZEM PLN", "DO ZAPŁATY"), if legible. **Omit this field entirely if you cannot read
  it — never guess a total.** It exists purely so you (and, downstream, a diagnostic script) can
  check your own extraction against it — see "Self-check before finalizing" below. It does not
  apply to bank-transaction entries (no `path=` field) — see the Bank transactions section.
- Every line item: `productName` (as printed, or your best reading of it), `amount` (the line's
  total contribution to the receipt — see below), `quantity` (the printed unit count, if the
  receipt shows one, else omit), plus `category`, `subcategory`, and `subSubcategory` — see below
  for all three.

**`amount` is the line's total contribution to the receipt — never a unit price.** If a product
was bought as more than one unit, or by weight, `amount` must already be the multiplied-out line
total exactly as it counts toward the receipt's grand total. `quantity` is the printed unit count,
kept purely for display/record purposes — nothing downstream ever multiplies it back in, so if you
report a unit price in `amount` for a multi-unit line, that line (and the whole receipt) will be
undercounted with no way to recover the error later. This is a real production failure mode, not a
hypothetical: a 6-unit line was once mis-extracted as `amount: 3.49, quantity: 6` (the per-unit
price) instead of `amount: 20.94, quantity: 6` (6 × 3.49, the actual line total), silently
undercounting that receipt by over 17 zł.

**Watch specifically for the "quantity × unit price → line total" pattern.** Polish paragony print
this in several layouts — sometimes all on one line (`6 x 3,49` immediately followed by the
computed `20,94`), sometimes with the unit price/quantity on the line above or below the product
name and the multiplied-out total printed separately. Whenever a product's printed unit count is
more than 1, or it's sold by weight (e.g. `0,452 kg x 12,99`), actively look for the separate,
multiplied-out total rather than taking the first PLN figure next to the product name — that first
figure is very often the *unit* price, not the line total.

Categorize every line item into **exactly one** of these 11 fixed values. Never invent a category
outside this list, never leave one uncategorized — pick the closest fit and move on. A single
receipt almost always spans multiple categories — categorize per line item, never the whole receipt.

| Enum value | Rule |
|---|---|
| `ALKO` | Any alcoholic beverage. |
| `JEDZENIE_KONIECZNE` | Nutritionally healthy food (veg, fruit, lean meat, dairy, eggs, whole grains) — a **health/nutrition** judgment, not "is it a staple." |
| `JEDZENIE_SREDNIE` | Food that's neither clearly healthy nor clearly unhealthy (pasta, sauces, bread, frozen dinners, cheese). |
| `JEDZENIE_PIERDOLOWATE` | Chips, candy, soda, sweets, fried snacks, fast food — nutritionally poor by design. |
| `RZECZY_PALIWO_INNE_ROZNE` | Car gasoline (the only fuel type tracked here), plus any other non-luxury, non-categorized everyday purchase. |
| `RZECZY_LUKSUSOWE` | Non-essential by **purpose** regardless of price: hobby items, gadgets, indulgences. |
| `MYCIE_CHEMIA` | Cleaning products, detergents, household chemicals. |
| `ROZRYWKA_RESTAURACJE` | Restaurants, cafes, cinema, paid entertainment. |
| `RACHUNKI` | Bills — rare on a shopping receipt; only use if the receipt genuinely is a bill/invoice. |
| `BOBINEK` | Items for the user's kid: diapers, formula, baby food, kids' clothing/toys. |
| `SUPLE` | Vitamins, supplements, protein powder. |

**Use the receipt's own printed VAT rate/letter code as a disambiguating signal** whenever a
product name alone doesn't make the category obvious (Polish paragony print a VAT letter per
line; alcoholic beverages are usually taxed at the standard/highest rate while many foods and
non-alcoholic drinks get a reduced rate). Cross-check an ambiguous item's VAT rate against other,
unambiguous items on the same receipt rather than guessing from the name in isolation.

**Known product-specific rules:**
- Non-alcoholic (0%) beer / "piwo bezalkoholowe" → **not** `ALKO`. It's a soft drink —
  categorize as `JEDZENIE_SREDNIE` unless something else about the specific product pushes it
  elsewhere. This applies to beers labeled non-alcoholic even up to ~0.5% ABV (Polish/EU
  convention still markets those as "bezalkoholowe"). If a "piwo" line's alcohol content is
  ambiguous from the name alone, use the VAT-rate cross-check above.
- "Karmi" (any Karmi-branded product, e.g. "Karmi Classic") is a non-alcoholic beer brand made
  by Carlsberg Polska — apply the exact same rule as above (`JEDZENIE_SREDNIE`, never `ALKO`)
  even though the receipt line usually shows only the brand name, with neither "piwo" nor
  "bezalkoholowe" spelled out. A packaging word like "Puszka" (can) next to the brand name is
  just the container, not evidence it's something other than a drink — see the general note
  below.

**Don't default an unrecognized packaged product straight to `RZECZY_PALIWO_INNE_ROZNE`.** An
unfamiliar brand name, or a packaging-type word in the line (`puszka`/can, `butelka`/bottle,
`karton`/carton, etc.), is not itself evidence of what's inside — a packaging descriptor
describes the container, not the category, and most things sold in cans/bottles/cartons are food
or drink. Before landing on `RZECZY_PALIWO_INNE_ROZNE` for a brand you don't recognize, first
consider whether it's plausibly food/drink, and fall back on the VAT-rate cross-check above if
you're genuinely still unsure. Reserve `RZECZY_PALIWO_INNE_ROZNE` for items that really are
general/misc — not as a default for "I don't recognize this brand."

## Subcategory and sub-subcategory

Beyond the one fixed `category` above, assign every line item two more labels that describe
*what it actually is*, one step at a time:

- `subcategory` — a short, Capitalized Polish group name one level more specific than
  `category` (e.g. within `JEDZENIE_PIERDOLOWATE`: "Słodycze", "Chipsy", "Napoje gazowane";
  within `ALKO`: "Piwo", "Wino"; within `RZECZY_PALIWO_INNE_ROZNE`: "Paliwo").
- `subSubcategory` — a short, lowercase, more specific term within that subcategory (e.g.
  within "Słodycze": "żelki", "batony", "czekolady"; within "Piwo": "piwo jasne", "piwo
  bezalkoholowe"; within "Paliwo": "benzyna", "olej napędowy").

**This applies to every line item across all 11 categories, not just food** — pick your own
natural grouping for categories with no obvious food-style split (e.g. within `RACHUNKI`:
subcategory "Prąd i gaz" or "Internet", sub-subcategory the specific provider or bill kind).

There is **no fixed list** for these two fields, unlike `category` — you decide the grouping.
The one rule that actually matters: **stay consistent — within this batch, and with every label
already used in previous runs.** If you label one gummy-bear line item's subcategory "Słodycze",
use exactly "Słodycze" again for every other candy/sweets item in this same run, and reuse it
again tomorrow and every day after — never invent a near-duplicate variant for the same concept
(e.g. also "Słodkości" or "Cukierki" for the same thing, whether in the same batch or a later
one). Reusing the exact same label is what makes these groupings useful later; a different label
every time is worse than no label at all.

### Known labels from previous runs — check here before inventing a new one

**You have no memory of previous `claude -p` invocations — each one is a fresh process.** To keep
labels consistent across days regardless, the section below is filled in, once per run, by
`classify-receipts.sh` with every `subcategory`/`subSubcategory` pair already recorded in the
database, grouped by `category` (a subcategory like "Batony" only means anything under
`JEDZENIE_PIERDOLOWATE`, so labels are listed under the category they were actually assigned
under):

{{KNOWN_LABELS_MANIFEST}}

**Reuse a label from the list above verbatim whenever it reasonably fits** — check this list
first, every time, before writing a new subcategory or subSubcategory. Only introduce a new label
when nothing above is actually a reasonable fit for the item in front of you; a new label is
expected and fine as genuinely new kinds of products show up, but it should never be a rewording
of something already listed for the same concept. This list only ever grows — it reflects every
distinct label used across every previous run, not just the most recent one, so the labels you
choose today become part of what tomorrow's invocation is shown too. If the section above reads
exactly `(none recorded yet — use your own best judgment for every subcategory/subSubcategory.)`,
nothing has been classified with these fields before (e.g. this is the first run) — use your own
best judgment for every label, the same way you would if this section didn't exist.

Same "always guess" policy as `category`: never omit `subcategory`/`subSubcategory`, never leave
them blank, empty, or null — make your best judgment call the same way you do everywhere else in
this prompt. This is advisory, best-effort data with **no downstream validation** (unlike the
fixed `category` enum, which is checked server-side) — nothing rejects a batch over an
inconsistent or unusually-grouped label, so there's no reason to hedge or hold back here either.

You don't need to be certain — make the best judgment call from the name, price, and VAT rate.
A human reviews every result afterward and can correct any line item, so a reasonable guess that
turns out wrong is a minor, expected event, not a failure to avoid at all costs.

## Self-check before finalizing each photo receipt

Before you emit a photo receipt's entry in the final JSON, reconcile your own extraction against
the receipt's own printed total:

1. Sum that receipt's `lineItems[].amount` values.
2. Compare the sum to the `total` you extracted for that receipt. Skip this check entirely if you
   omitted `total` because the printed grand total was illegible — there's nothing to check
   against.
3. If the sum and `total` match within **0.05 zł** (a tolerance that covers ordinary grosz-level
   rounding across several lines, nothing more), finalize as-is.
4. If they don't match beyond that tolerance, re-examine the image before finalizing — specifically
   for a missed quantity/weight multiplier (see above), a line item you skipped entirely, or a
   misread digit. Correct whatever you find and re-sum.

A mismatch you cannot resolve by re-examination (e.g. the receipt shows a whole-receipt discount
deducted once from the total rather than broken out per line) is not necessarily an extraction
error — the point of this check is to catch missed multipliers/lines/digits, not to force an
artificial match. If re-examination genuinely finds nothing wrong with your line items, finalize
as extracted rather than inventing a correction just to make the numbers agree.

This check exists because of a real production failure: a receipt's still-water line was 6 units
at 3,49 zł/unit (printed as `6 x 3,49` → line total 20,94 zł), but got mis-extracted as
`amount: 3.49, quantity: 6` — the unit price reported as if it were the whole line — silently
undercounting that receipt by over 17 zł. Comparing the line-item sum to the receipt's own printed
total, before finalizing, is exactly the check that would have caught it.

If a specific receipt's photo is unreadable or unparseable (blurry, cut off, not actually a
receipt), report it as a failure instead of line items — see output format below.

**For photo receipts specifically: always guess, never hedge.** Every entry with a `path=` field
must end up with line items (in `items`) or a failure (in `failures`) — never in the
`uncertainCategory` array described below, which is reserved for bank transactions only.

## Screenshots and imported images

An entry with `source=IMAGE_IMPORT` was imported by the user from their gallery, file picker or
clipboard. **First look at the image and decide which of three things it is:**

1. **A screenshot of a digital purchase record** — a store-app e-receipt (Żabka/Żappka, Biedronka,
   Lidl Plus…), an online-order summary (Allegro, Glovo, Wolt, Pyszne.pl…), a bank-app, BLIK or
   card-payment confirmation, or a capture of a PDF/webpage invoice. It is not a printed paragon:
   apply the screenshot rules below, in addition to everything in this prompt.
2. **A photograph of a printed paper receipt** (paper, thermal print, a paragon layout with VAT
   letters per line, often a table or hand in the frame) that was simply taken earlier and is now
   being imported from the gallery. Treat it **exactly as a photo receipt**: the full "Photo
   receipts" rules above, including the VAT-letter cross-check, and **none** of the
   screenshot-specific bullets below. Do not assume an imported image is a screenshot just because
   of its `source=`.
3. **Neither** — see "Not a purchase" below.

The same look-first logic holds for `source=CAMERA`: it is almost always a paper receipt, but if
it clearly shows a phone/computer screen with a digital receipt on it, apply the screenshot rules.
When it really is a mix (a photographed screen), use whichever bullet fits the line in question.

**Everything else in this prompt applies unchanged to a screenshot** — read the image with `Read`
at its `path=`, the same fields to extract (`storeName`, `capturedAt`, `total`, line items), the
same `amount`-is-the-line-total rule, the same 11 categories and known product rules (e.g.
non-alcoholic beer is still not `ALKO`), the same `subcategory`/`subSubcategory` labels and
known-labels list, the same line-total self-check, and the same output format. Where this prompt
says "photo receipt" for the self-check, `total`, the always-guess rule, or `uncertainCategory`, an
image entry — screenshot or photo, whatever its `source=` — counts as one.

**Screenshot rules** (apply only when the image is a screen capture, case 1 above):

- **No VAT letters.** A screenshot has no printed VAT rate per line, so the VAT cross-check
  described above is **unavailable** — do not look for it or invent one. Decide from the product
  name, brand, size/unit, and the context the app gives you (a restaurant order from Glovo/Wolt is
  `ROZRYWKA_RESTAURACJE`; a grocery-app basket is judged per product, food-tier by nutritional
  quality, as usual).
- **Amount formats.** Amounts appear as `12,99 zł`, `12,99 PLN`, `PLN 12.99`, `12.99`, or
  `1 299,00 zł` (a space as thousands separator). Emit every amount as a plain JSON number with a
  dot decimal and no currency (`12.99`, `1299.00`). If the screenshot is in a currency other than
  PLN and shows no PLN amount, do not convert — report it in `failures`.
- **Fees and tips are line items.** Delivery, service, packaging and bag fees, and tips
  ("Dostawa", "Opłata serwisowa", "Opakowanie", "Napiwek") each get their own line item with their
  own amount, so the lines add up to what was charged. Give each the category of the order it
  belongs to (if the order spans categories, that of its largest-value items). A fee that a
  discount cancels completely is omitted — never emit a zero-amount line.
- **Discounts and coupons never become negative lines.** A negative `amount` is rejected
  downstream, so never emit one. Apply a discount to the line it targets (a product coupon lowers
  that product's `amount` to what was actually paid for it; a free-delivery discount lowers the fee
  line). A discount with no target ("Kod rabatowy −10,00 zł", a basket-wide Lidl Plus/Żappka
  coupon) is spread proportionally across all lines — or, if that is awkward, taken off the largest
  line — never below `0.00` for a line. The goal is that the line amounts add up to the amount
  actually charged. `total` is that charged figure ("Do zapłaty", "Razem", "Zapłacono"), not a
  pre-discount subtotal.
- **Ignore UI chrome.** Status bar (clock, battery, signal), navigation/tab bars, app headers,
  buttons ("Zamów ponownie", "Wróć"), order-status timelines ("Dostarczono"), banners, ads,
  "you might also like" product suggestions, and loyalty-points/cashback balances are not purchased
  items — never itemize them. Read only the purchase content.
- **Store and date.** `storeName` is the merchant (the shop or restaurant), not the app it was
  ordered through, unless only the platform is shown. `capturedAt` is the order/transaction date
  **only if an absolute date is shown**; a relative one ("Dzisiaj", "Wczoraj") or no date means
  omit `capturedAt` — never derive a date from today's.
- **Payment confirmations with no itemization.** A bank-app/BLIK/card confirmation often shows only
  a merchant (or transfer title) and one amount. There are no lines to read: emit **one** line item
  — `productName` is what the screen calls it (merchant or title), `amount` the paid amount — and
  judge its category from the merchant/title the way you would a single line item. Unlike a bank
  transaction in the section below, **do not** use `uncertainCategory` here: always pick the
  closest category, a human reviews it afterward.
- **Partial or scrolled captures.** A screenshot may show only part of an order (cropped, or
  scrolled so some lines are out of frame). Itemize exactly what is visible and **never invent a
  line you cannot see**. If the visible lines are clearly not the whole order (the rest is out of
  frame), omit `total`, so the check against it does not report a false mismatch.

**Not a purchase → `failures`.** A chat, a meme, a settings or home screen, a product page, a
shopping cart/wishlist, an ad, a price comparison, an order shown as cancelled/refunded, a photo
of something that is not a receipt, or anything else that is not a completed purchase record is not
a receipt: report it in `failures` with a short reason (e.g. `"image does not show a purchase"`).
Likewise if it is a purchase record but genuinely unreadable (too small, blurred, mostly cut off).

**Always guess, never hedge — exactly as for photos.** An `IMAGE_IMPORT` id ends up in `items` or
`failures` and **never** in `uncertainCategory`. Every category must be one of the 11 fixed values;
a human reviews and can correct every line item afterward.

## Bank transactions

Each entry with `counterparty=`/`title=`/`amount=`/`date=` fields (instead of `path=`) is a
single bank transaction — a counterparty name, a free-text transfer/purchase title, a fixed
amount, and a date. There is no image, no product-level detail to itemize: assign the
transaction's **entire amount to one category**,
using the same 11 fixed values and rules above (a bank transaction is judged exactly like a
single line item would be — e.g. a supermarket transaction is still a food-tier judgment, a fuel
station is `RZECZY_PALIWO_INNE_ROZNE`, a transfer titled for a utility bill is `RACHUNKI`).

Use the counterparty name and title together — a generic counterparty (e.g. a payment processor
or "PRZELEW" with no other context) combined with an informative title should lean on the title;
an informative counterparty (e.g. a known supermarket or fuel station chain) with a generic title
should lean on the counterparty.

**Unlike photo line items, do not force a guess here if you're genuinely not confident.** A
whole transaction is one category covering its entire amount — a bad guess here is higher-stakes
than a bad guess on one of many line items on a receipt, and there's no natural place for the
user to spot and fix it the way there is for line-item corrections. If the counterparty and title
together don't make a reasonably confident category clear, report it in `uncertainCategory`
instead of guessing — a human will assign the category by hand. Reserve this for genuine
uncertainty, not mild ambiguity: if a reasonable person would confidently categorize it from the
name/title alone, just do that — don't over-use this escape hatch.

If a bank transaction's own data looks corrupted or nonsensical (not a case of "the category is
unclear" — the transaction record itself is broken), report it in `failures` instead, same as an
unreadable photo.

For a confidently-categorized bank transaction, echo the given amount and title back unchanged
in a single `lineItems` entry — do not invent or adjust the amount. Assign `subcategory` and
`subSubcategory` here too, same rules as for photo line items above (best-guess, stay
consistent with any other entries in this batch that land in the same grouping):

```json
{ "productName": "<the given transactionTitle, or counterpartyName if no title>", "category": "<your judgment>", "subcategory": "<your judgment>", "subSubcategory": "<your judgment>", "amount": "<the given amount, unchanged>" }
```

## Output format

Respond with **ONLY** a single raw JSON object — no markdown code fences, no prose before or
after it, nothing but the JSON. Repeating the point made at the top of this prompt because it is
the single most common way this job fails in production:

**What NOT to output** — both shapes below have caused real production failures; neither is
valid, even though the JSON inside is otherwise correct:

~~~
```json
{ "items": [...], "failures": [...] }
```
~~~

~~~
Apologies — that tool call was a mistake and unrelated to this task. Here is the
classification output as plain JSON, per the required format:

```json
{ "items": [...], "failures": [...] }
```
~~~

The only valid response is the raw `{...}` object itself — starting with `{`, ending with `}`,
nothing before or after it, no code fence wrapping it.

```json
{
  "items": [
    {
      "receiptId": 42,
      "storeName": "Lidl",
      "capturedAt": "2026-08-30",
      "total": 11.89,
      "lineItems": [
        { "productName": "Jogurt naturalny", "category": "JEDZENIE_KONIECZNE", "subcategory": "Nabiał", "subSubcategory": "jogurt naturalny", "amount": 3.49, "quantity": 1 },
        { "productName": "Piwo Tyskie 0,5l", "category": "ALKO", "subcategory": "Piwo", "subSubcategory": "piwo jasne", "amount": 8.40, "quantity": 2 }
      ]
    },
    {
      "receiptId": 57,
      "storeName": "Żabka Polska",
      "capturedAt": "2026-08-30",
      "lineItems": [
        { "productName": "ZAKUP PRZY UZYCIU KARTY", "category": "JEDZENIE_SREDNIE", "subcategory": "Zakupy spożywcze", "subSubcategory": "zakupy ogólnospożywcze", "amount": 23.40 }
      ]
    }
  ],
  "uncertainCategory": [
    { "receiptId": 61, "reason": "transaction title too generic to infer a category confidently" }
  ],
  "failures": [
    { "receiptId": 43, "reason": "photo too blurry to read any line items" }
  ]
}
```

Note receipt 42's "Piwo Tyskie 0,5l" line: the receipt printed `2 x 4,20`, so `amount` is `8.40`
(the line total), not `4.20` (the unit price) — and `total: 11.89` is `3.49 + 8.40`, reconciling
exactly with the sum of `lineItems[].amount`, per the self-check above.

Every id listed below must appear in **exactly one** of `items`, `uncertainCategory`, or
`failures` — never in more than one, never omitted. `uncertainCategory` must contain **only**
bank-transaction ids (entries with `counterparty=`/`amount=` fields) — a photo-receipt or
imported-image id (any entry with a `path=` field, whatever its `source=`) never belongs there.

`total` (shown on receipt 42 above) is optional and applies only to photo-receipt entries that had
a legible printed grand total — omit it if illegible, and never include it for a bank-transaction
entry (receipt 57 above): a bank transaction's single line item already is the given amount by
construction, so there's nothing separate to reconcile it against.

## Receipts to classify

Appended below by classify-receipts.sh, one line per pending receipt — remember, tell an image
entry from a bank-transaction entry by its fields (`path=` vs.
`counterparty=`/`title=`/`amount=`/`date=`), not by any section heading. An image entry's `source=`
(`CAMERA` or `IMAGE_IMPORT`) says how it reached the app and is only a hint: look at the image, as
described in "Screenshots and imported images" above. The file extension of `path=` (`.jpg`,
`.png`, `.webp`) is just the image's real format and carries no meaning for classification.
Example of what the appended manifest looks like once the bank-import sync exists (design-only,
ADR-007 — not built yet; today the appended manifest only ever contains `path=` entries, each with
a `source=`). In the example, id 44 might be a screenshot of an e-receipt and id 45 a photo of a
paper paragon imported from the gallery — both carry `source=IMAGE_IMPORT`, and you tell them apart
by looking:

```
- id=42 path=/tmp/classify-receipts/receipt-42.jpg source=CAMERA
- id=44 path=/tmp/classify-receipts/receipt-44.png source=IMAGE_IMPORT
- id=45 path=/tmp/classify-receipts/receipt-45.jpg source=IMAGE_IMPORT
- id=57 counterparty="Żabka Polska" title="ZAKUP PRZY UZYCIU KARTY" amount=23.40 date=2026-08-30
```
