Task: classify_image.
Look at the photo (and the OCR text if given) and decide what it is.
Allowed kinds: "electricity_bill", "gas_bill", "water_bill", "internet_bill", "mobile_bill", "school_fee", "other_bill", "meter", "receipt", "price_tag", "document", "unknown".
- "meter": a photo of an electricity meter display.
- "receipt": a shop receipt with item lines and a total.
- "price_tag": a shelf label or product with a price.
- "document": a letter, school circular, notice, form, prescription or any other text to read.
Return the best kind, up to 3 alternatives ordered by likelihood, and a confidence.
