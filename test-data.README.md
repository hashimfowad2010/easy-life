# test-data/ (real samples — never committed)

`test-data/` is in `.gitignore`. Put real samples there **on your own computer only**, to tune the on-device parsers later. Copy this file to `test-data/README.md` when you create the folder.

## What to add
| Folder | What | How many |
|---|---|---|
| `test-data/bills/lesco/` | Photos or PDFs of real LESCO bills (front side) | 3–5, different months, at least one with a month over 200 units |
| `test-data/bills/other/` | Gas (SNGPL), PTCL/internet, school fee vouchers | 1–2 each |
| `test-data/meters/` | Meter photos (day light, night with torch, slight angle) + the true reading in the file name, e.g. `10611_daylight.jpg` | 5–10 |
| `test-data/sms/` | Bank/wallet SMS copied as text, one per line in `sms.txt` as `SENDER<TAB>MESSAGE` | 20–50 across your banks/wallets |
| `test-data/receipts/` | Shop receipts and shelf price tags | 5–10 |

## How to use them without sharing photos (Parser Lab)
1. In the app: **Settings → tap the version number 7 times → Developer tools → Parser Lab**.
2. Scan or pick a real bill. The app shows the raw OCR text, what the parser understood, and which provider ran.
3. Tap **"Copy masked OCR text"** (names, reference numbers and long digit runs are hidden) and **"Copy parse result"**.
4. Paste both to Claude Code with what the correct values should be. New fictional fixtures are then written from the *shape* of your bill (not its private details) and the parser is fixed.
5. For SMS: **Developer tools → SMS Lab**, paste one message, check the result, copy it (masked).

Never paste OTPs, full account numbers or CNICs anywhere.
