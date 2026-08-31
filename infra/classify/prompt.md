You are analyzing photographed shopping receipts for a personal spending tracker. For each
receipt listed at the end of this prompt, read its image at the given local path and extract:

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
    }
  ],
  "failures": [
    { "receiptId": 43, "reason": "photo too blurry to read any line items" }
  ]
}
```

Every receipt id listed below must appear in exactly one of `items` or `failures` — never both,
never omitted.

## Receipts to classify
