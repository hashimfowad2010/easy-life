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
- [ ] Smart AI page explains honestly that it's not switched on yet; switch is disabled.
- [ ] Export all my data → choose a file → a JSON file is written.
- [ ] Delete all my data → confirm → app restarts at onboarding with nothing left.
- [ ] Demo mode on/off adds/removes only demo rows (real bills stay).
- [ ] Tap version 7 times → Developer tools: Parser Lab, SMS Lab (try an OTP: "DISCARD"), error report (copy), AI router (shows why each provider is skipped).

## Offline and low-end
- [ ] Airplane mode: everything above still works (except online-only items, which say so).
- [ ] On a low-end phone the dashboard opens in under 2 s after the first launch.
