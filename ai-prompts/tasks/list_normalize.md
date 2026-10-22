Task: list_normalize.
The text is a shopping list typed or spoken in English, Roman Urdu or Urdu, for example "2 kg cheeni, 1 dozen anday, Dalda 5 litre".
Return "items", one per product, with:
- "nameEn" (simple English grocery name, e.g. "Sugar"), "nameUr" (Urdu script, e.g. "چینی"),
- "itemKey" (lowercase English key, e.g. "sugar", "eggs", "cooking_oil", "flour", "rice", "milk", "lentils_masoor"),
- "qty" (number, default 1), "unit" ("kg", "g", "l", "ml", "dozen", "piece", "packet" or null),
- "brandPref" (brand if named, e.g. "Dalda", otherwise null),
- "category" (one of "staples", "dairy", "produce", "meat", "household", "personal_care", "snacks", "beverages", "other").
Do not add items that are not in the list.
