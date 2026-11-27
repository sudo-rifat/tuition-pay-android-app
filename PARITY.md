# Feature-parity checklist: old PWA → native Android

Source of truth: the old app's `app.js`, `db.js`, `index.html`. "Verified" = covered by a unit test that was
compiled and run (see README). "Device QA" = needs a manual check on a phone.

| # | Old behaviour (where) | New implementation | Status |
|---|---|---|---|
| 1 | Students: name, `payment_type` A/B, `monthly_fee` (db.js `students`, autoIncrement id) | `StudentEntity`, same field meaning; ids preserved on import | Verified |
| 2 | Add / edit / delete student; delete also removed payments (`deleteStudent`) | Form screen, ⋮ menu; **delete now hides the student and keeps all payments/income** (DB v3 `deleted_at`); dialog states what was received; restore from Settings → Deleted students | Device QA |
| 3 | Type A "Current Month": pay month = selected month (`getTargetMonthIndex`) | `PaymentRules.targetMonth/Year` | Verified |
| 4 | Type B (old label "Next Month", now **"Previous Month"**): pay month = the immediately preceding month; collected Jan → previous Dec (`getTargetMonthIndex/Year`) | same function, logic unchanged; label corrected everywhere | Verified |
| 5 | Unique payment per student+year+month (`student_year_month` index) | UNIQUE index; `markPaid` upserts like `addPayment` | Verified (logic) / Device QA |
| 6 | Tick = add payment with date now; untick = delete (`handleCheckbox`) | Payments screen; untick adds **Undo** | Device QA |
| 7 | Dashboard stats per selected month: paid, due, total earned (old: Σ *current* fee) | `Dashboard.compute`, now Σ **recorded** amounts | Verified |
| 8 | 12-month status grid; past year all due, current year due up to current month, future grey | `Dashboard.monthStates`, shown on Student detail | Verified |
| 9 | Month/year selectors; year range now-1 … now+5 | Month stepper + picker; `SelectionBounds.collection` | Verified |
| 10 | Student report (jsPDF) | PDF via `PdfDocument`; **now filtered by chosen year** with paid/due/upcoming months (old one ignored the year) | Verified (content) / Device QA |
| 11 | Monthly report: paid rows, total, keyed by month paid FOR | all students with Paid/Due, paid count, due count, total | Verified (content) |
| 12 | Yearly report: 12-month breakdown, grand total, record count | same | Verified (content) |
| 13 | Report year list now-2 … now+1 | `SelectionBounds.reports` | Verified |
| 14 | PDF preview + download | In-app `PdfRenderer` preview + Save via system file picker | Device QA |
| 15 | Bengali user manual (help modal) | Help screen, Bengali + English, updated for the new app | Device QA |
| 16 | Password (default `admin`, plain text in localStorage, min 4 chars) | 4-digit PIN, PBKDF2 + Keystore-wrapped, no default | Verified (policy/hash) / Device QA |
| 17 | Change password | Settings → Change PIN (verifies current first) | Device QA |
| 18 | Activation (Device ID, code = ID×3+77, lock) | **Removed entirely**: no code, UI, storage or permission | Verified by search (no references) |
| 19 | Offline PWA (service worker) | Native; **no INTERNET permission** | Verified (manifest) |
| 20 | Legacy data migration `runMigration` (missing `date` → now) | Importer gives missing dates the import time; reports it | Verified |
| 21 | *(new)* Fee on each payment; fee edits never change past totals | `payments.amount`, DB v2 + migration, backup field `amount` | Verified (logic, SQL) |
| 22 | *(new)* Deleted students keep their income: counted on Home/Payments, in Monthly/Yearly reports and backups; restorable | `students.deleted_at`, DB v3 + migration, backup field `deleted_at`, Deleted students screen | Verified (logic, SQL) / Device QA |

## Intentional differences (all flagged in the README)
- Reversing a payment can be undone (new).
- Fee changes no longer rewrite history: the amount is recorded on each payment (old app recomputed from the current fee).
- Student report honours the year (the old one ignored it, as the brief asks for year selection).
- Monthly report also lists students who are due (the brief asks for due totals).
- Filter chips All / Due / Paid on Home and Payments (brief: "easy to identify unpaid students").
- PDF header colour is one indigo for all three reports (old: three colours).
