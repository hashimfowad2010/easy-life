# Manual QA checklist (on a phone, from a GitHub Release)

Install: download `EasyLife.apk` from the latest Release → open → allow "Install unknown apps" for your browser/Files → Install. Updating keeps your data (same signing key).

Tick each item in **both English and Urdu**, and in **light and dark** mode (Settings → Appearance).

## First run
- [ ] Onboarding: language buttons work; choosing اردو flips the whole UI right-to-left immediately.
- [ ] Modules step: turning a module off hides it from Today and greys it in Modules.
- [ ] City + pilot code saved (Settings → City shows them).
- [ ] Impact opt-in: both "Yes" and "No thanks" finish onboarding. No permission was asked.

## Shell
- [ ] Bottom bar: Today · Modules · [Scan] · Settings; Scan button opens the scan sheet.
- [ ] Modules grid shows live status (e.g. "142 / 200 units", "2 bills due").
- [ ] Back button never returns to a form you already saved.
- [ ] Rotate the phone on every screen: no crash, no lost input on forms.
- [ ] Font size at 200% (Android Settings → Display → Font size): text wraps, buttons still usable.
- [ ] TalkBack: buttons and the progress ring are read out sensibly.

## Universal Scan
- [ ] "Bill, receipt or paper" opens the Google document scanner (or says it's unavailable and offers photos).
- [ ] "Try a sample → Sample LESCO bill": shows "This looks like: Electricity bill" with "Read on your phone"/"Demo data" label.
- [ ] Changing the kind chip changes where Continue goes.
- [ ] A blank/blurred photo shows "Couldn't read any text" and offers typing it in.

## Bijli Guard (Limits) — hero
- [ ] With no meter: "Scan my electricity bill", "Enter last bill by hand", "Try demo data" all work.
- [ ] Demo data: ring, headline ("At this pace you'll reach … Keep to … units a day"), allowance, projected, cross date.
- [ ] Reading-date slip: "If read N days late" rows; changing the expected reading date updates them.
- [ ] Estimated bill: "Stay at 200 or less" vs "Reach N units", difference, 6-month cost; says "Estimated" + tariff date.
- [ ] Protected card: for the demo family (crossed in July) says "Not protected right now" and when it can return.
- [ ] Add reading → Take a photo: camera permission asked only now, with a reason; deny → "Type it instead" works.
- [ ] Meter photo: digits found, low confidence or lower-than-last highlighted; candidate chips fill the field.
- [ ] History: daily bars, monthly bars with the 200 line, months over 200 listed; deleting a reading updates the dashboard.
- [ ] Appliance advisor: add/edit appliances; suggestions like "Reduce AC by 1.5 h a day → saves about 2.4 units".
- [ ] Calibrate with last bill: realistic numbers accepted, silly numbers refused.
- [ ] Other limits: create "Mobile data 30 GB", add entries, see the warning colours.

## Bills
- [ ] Sample LESCO bill → confirm screen shows every field; uncertain fields are highlighted; editing then saving works.
- [ ] Saving an electricity bill also updates Bijli Guard (new cycle + 12-month history).
- [ ] Notification permission is asked after the first bill is saved (Android 13+), not before.
- [ ] Bill list tabs Upcoming / Overdue / Paid; "Pay by … to save Rs …" pill; Mark paid / unpaid.
- [ ] Add a bill by hand with only a due date and amount.

## Notifications (leave the app closed)
- [ ] A bill due in ≤ 5 days gives one reminder (not repeated); nothing between 10 pm and 8 am.
- [ ] Demo meter in DANGER gives one "may cross 200 units" warning.

## Settings, privacy, data
- [ ] Language switch in Settings flips text and direction.
- [ ] Urdu font Nastaliq ↔ Naskh.
- [ ] Smart AI page explains Gemini's free-tier data use honestly; the switch stays off until "I am 18 or older. Turn on" is tapped (and that button is disabled in a build without Firebase).
- [ ] Export all my data → choose a file → a JSON file is written.
- [ ] Delete all my data → confirm → app restarts at onboarding with nothing left.
- [ ] Demo mode on/off adds/removes only demo rows (real bills stay).
- [ ] Tap version 7 times → Developer tools: Parser Lab, SMS Lab (try an OTP: "DISCARD"), error report (copy), AI router (shows why each provider is skipped).

## Offline and low-end
- [ ] Airplane mode: everything above still works (except online-only items, which say so).
- [ ] On a low-end phone the dashboard opens in under 2 s after the first launch.

## Urdu Helper
- [ ] First translation asks to download the Urdu model (about 30 MB, Wi-Fi by default); after that it works in airplane mode.
- [ ] Type or paste English → Translate → Urdu text in simple words; "Read aloud" speaks it (or shows the guide to install an Urdu voice).
- [ ] Speak: microphone permission asked only now, with a reason; deny → typing still works.
- [ ] Scan & Read: a photographed circular → text, translation, dates, amounts and "Things to do"; a date can become a reminder.
- [ ] Share text from WhatsApp or Chrome to Easy Life → opens in Urdu Helper.
- [ ] A prescription or legal paper shows the "check with a doctor / lawyer" banner and never a dose.

## Budget
- [ ] Bank setup asks only for the bank name and optional last 4 digits (never a password, PIN, OTP or full number).
- [ ] SMS permission asked only when you tap "Allow reading SMS", with a reason; deny → statement import / adding by hand still work.
- [ ] SMS Lab: an OTP message shows "DISCARD"; it never appears in Transactions.
- [ ] Transactions: exclude one → monthly total drops; Undo restores it. "Exclude similar…" offers a rule.
- [ ] Money sent to yourself and back is suggested as a self-transfer pair; a refund is paired with its purchase.
- [ ] Paying a bill by bank SMS suggests marking that bill paid.
- [ ] Budgets: set Groceries; Today shows a warning card when it runs low.
- [ ] Biometric lock (Settings): Budget asks for fingerprint/PIN.

## Smart Shopping
- [ ] New list: "2 kg cheeni, 1 dozen anday, Dalda 5 litre, aadha kilo dahi, chips" → 5 items with quantities and units.
- [ ] Mark an item "can skip"; the estimate shows the must-have total and the Groceries budget left.
- [ ] Search on a retailer opens that shop's own search page in the browser; typing the price back adds it to the item.
- [ ] Compare prices (demo data): cheapest per item with "Reported N days ago · N reports", best single shop, best split across 2 shops, savings; the silly Rs 1,600 sugar report is ignored; "not guaranteed cheapest" note shown.
- [ ] Scan a receipt → confirm → saved as a Budget transaction and as prices; sharing prices asks for consent first.
- [ ] Share the list in Urdu and in English (WhatsApp).

## Impact and feedback
- [ ] Settings → Help the project → ILAP impact report: highlights and bars show the demo activity; "Save as CSV" and "Save one-page PDF" write files that open.
- [ ] The PDF is one page and readable in Urdu too.
- [ ] Settings → Give feedback: Send stays disabled until questions 1 and 3 are answered; after sending, the Today card does not come back.
- [ ] Three days after install a "How is Easy Life helping?" card appears on Today; "Later" hides it for 3 days.
- [ ] With "Share anonymous counts" off, nothing is uploaded (check Firestore stays empty).

## Smart AI (Google Gemini, free)
- [ ] Settings → Smart AI: off by default; the page says what is sent (numbers, names and addresses hidden), that Google may use and review free-tier data, and that it is for adults; "I am 18 or older. Turn on" switches it on.
- [ ] With Smart AI on and internet: scan a real bill → the confirm screen shows "Smart AI (Gemini)"; the counter "Today on this phone: N of 15" goes up.
- [ ] Urdu Helper → "Simple words" mode now works (it said "Needs Smart AI" before turning it on).
- [ ] Airplane mode: the same scan still works and shows "Read on your phone".
- [ ] Bank SMS are never sent: Developer tools → AI router lists only the on-device reader for sms_parse (and the router refuses Gemini for it even if Remote Config added it).
- [ ] After 15 Smart AI reads in a day, scans fall back to "Read on your phone" (AI router shows the daily limit).
- [ ] Withdraw consent → Smart AI switch turns off and scans read on the phone again.
- [ ] Developer tools → AI router: each provider shows why it is or isn't used (e.g. "claude_enabled = false").
