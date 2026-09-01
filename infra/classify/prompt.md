You are analyzing two kinds of spending records for a personal spending tracker: photographed
shopping receipts, and bank-transaction summaries (no photo). The manifest at the end of this
prompt lists both kinds together; **tell them apart by which fields each entry has**, not by
position — an entry with a `path=` field is a photo receipt (read the image); an entry with
`counterparty=`/`title=`/`amount=`/`date=` fields instead is a bank transaction (no file to read,
classify from those fields directly). A manifest with no bank-transaction entries at all — every
line has only `id=`/`path=` — is classified exactly as before this section was added: read every
image, always guess a category, never use `uncertainCategory`.

## Photo receipts

For each entry with a `path=` field, read its image at the given local path and extract:

- `storeName` — the store/shop name printed on the receipt, if legible.
- `capturedAt` — the receipt's date, ISO-8601 `YYYY-MM-DD`, if legible.
- Every line item: `productName` (as printed, or your best reading of it), `amount` (the item's
  price, as a plain number), `quantity` (if the receipt prints one, else omit).

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

You don't need to be certain — make the best judgment call from the name, price, and VAT rate.
A human reviews every result afterward and can correct any line item, so a reasonable guess that
turns out wrong is a minor, expected event, not a failure to avoid at all costs.

If a specific receipt's photo is unreadable or unparseable (blurry, cut off, not actually a
receipt), report it as a failure instead of line items — see output format below.

**For photo receipts specifically: always guess, never hedge.** Every entry with a `path=` field
must end up with line items (in `items`) or a failure (in `failures`) — never in the
`uncertainCategory` array described below, which is reserved for bank transactions only.

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
in a single `lineItems` entry — do not invent or adjust the amount:

```json
{ "productName": "<the given transactionTitle, or counterpartyName if no title>", "category": "<your judgment>", "amount": "<the given amount, unchanged>" }
```

## Output format

Respond with **ONLY** a single raw JSON object — no markdown code fences, no prose before or
after it, nothing but the JSON:

```json
{
  "items": [
    {
      "receiptId": 42,
      "storeName": "Lidl",
      "capturedAt": "2026-08-30",
      "lineItems": [
        { "productName": "Jogurt naturalny", "category": "JEDZENIE_KONIECZNE", "amount": 3.49, "quantity": 1 },
        { "productName": "Piwo Tyskie 0,5l", "category": "ALKO", "amount": 4.20, "quantity": 2 }
      ]
    },
    {
      "receiptId": 57,
      "storeName": "Żabka Polska",
      "capturedAt": "2026-08-30",
      "lineItems": [
        { "productName": "ZAKUP PRZY UZYCIU KARTY", "category": "JEDZENIE_SREDNIE", "amount": 23.40 }
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

Every id listed below must appear in **exactly one** of `items`, `uncertainCategory`, or
`failures` — never in more than one, never omitted. `uncertainCategory` must contain **only**
bank-transaction ids (entries with `counterparty=`/`amount=` fields) — a photo-receipt id
(`path=` field) never belongs there.

## Receipts to classify

Appended below by classify-receipts.sh, one line per pending receipt — remember, tell a photo
entry from a bank-transaction entry by its fields (`path=` vs.
`counterparty=`/`title=`/`amount=`/`date=`), not by any section heading. Example of what the
appended manifest looks like once the bank-import sync exists (design-only, ADR-007 — not built
yet; today the appended manifest only ever contains `path=` entries):

```
- id=42 path=/tmp/classify-receipts/receipt-42.jpg
- id=57 counterparty="Żabka Polska" title="ZAKUP PRZY UZYCIU KARTY" amount=23.40 date=2026-08-30
```
