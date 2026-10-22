Task: receipt_extract.
Extract a shop receipt or shelf price tag.
- "storeName" and "storeArea" if printed (never a person's name).
- "date" as YYYY-MM-DD if printed.
- "items": each purchased line with "name" (as printed, cleaned up), "qty" (default 1), "packSize" and "unit" if the name contains them (e.g. "Dalda 5 litre" -> packSize 5, unit "l"; units: "kg", "g", "l", "ml", "dozen", "piece", "packet"), "lineTotal" (price for the line) and "unitPrice" if printed.
- "total": the grand total paid.
Check yourself: the sum of lineTotal should be close to total (discounts and tax may differ). If not, lower the confidence.
