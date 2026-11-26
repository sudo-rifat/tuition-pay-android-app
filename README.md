# Student Pay Manager (native Android)

Offline tuition-fee manager for private tutors. Kotlin · Jetpack Compose · Material 3 · MVVM · Room · DataStore ·
Navigation Compose. No network permission, no third-party libraries beyond AndroidX.

## Read this first: what has and has not been verified

**Verified here (compiled and executed, 49 tests passing):** the whole domain layer (payment rules, dashboard maths,
reports and PDF content, validation, PIN policy/hash/lock-out, backup validation), the JSON backup codec, and the
migration path *from the real old `db.js`* (old data → export script → importer).

**Not compiled here:** the Android/Compose/Room/DataStore/PDF code (~4612 lines). This sandbox has no Android SDK
and cannot reach Google's Maven repository, so Gradle could not run. I mitigated with static checks (every import
and `R.string` resolves; 189 strings match 189 uses; format-argument counts match) and by keeping to stable APIs,
but **expect to fix a few compile errors on first build**. Please do the device QA list at the bottom before relying
on it with real fee data.

Likely first-sync issues, all one-line fixes:
- Versions in `gradle/libs.versions.toml` were checked against official pages (AGP 9.5.1, Compose BOM 2026.11.01,
  KSP 2.3.9). Kotlin 2.4.20 is the one pairing I could not confirm against AGP 9.5's bundled Kotlin; if sync
  complains, use the version it names for `kotlin` (Compose compiler plugin uses the same version).
- `gradle/gradle-wrapper.properties` points at Gradle 9.6.0 but `gradle-wrapper.jar` is not included. Open the
  project in Android Studio (it creates it) or run `gradle wrapper`.

## Build
Android Studio (current stable) → Open the folder → sync → Run. JDK 17+. minSdk 26, target/compile 36.
Unit tests: `./gradlew :app:testDebugUnitTest`.

## Decisions you should know about

1. **Type B wording contradicts your brief; I followed the code.** The brief says Type B "applies to the following
   month". The old code (`getTargetMonthIndex`) does the opposite: a Type B payment collected in March is stored for
   **February**, and in January for the previous **December**. You said not to change this logic, so it is ported
   exactly, in one function (`PaymentRules`), with tests. The form and help text describe it plainly. If the code
   was wrong rather than the brief, it is a two-line change; tell me.
2. **Forgot PIN = erase and restore.** A 4-digit PIN cannot be recovered. To meet "never lock out permanently" without
   letting anyone bypass the PIN, "Forgot PIN?" offers *erase all data, set a new PIN, restore from backup*. Wrong
   PINs only cause temporary waits (30 s doubling to 10 min after every 5 tries). The setup screen says this.
3. **PIN storage:** PBKDF2-SHA256 (200k iterations) salted hash, wrapped with a non-exportable Android Keystore key,
   stored in `noBackupFilesDir` (never in Android backup). 4 digits = 10,000 combinations, so the Keystore binding and
   lock-out do the real work. This stops casual access; it is not server-grade security and does not resist a rooted
   device. The app hides itself from Recents/screenshots by default (toggle in Settings).
4. **Amounts use the student's current fee**, exactly like the old dashboard and reports. Changing a fee therefore
   changes past totals. A stored per-payment amount would fix this but cannot be reconstructed for imported history,
   so I did not change it.
5. **Room 2.8.5, not Room 3**, and string-based navigation routes: fewer unknowns without a compiler, and no
   serialization plugin. Minimal DI (no Hilt). Standard Material 3 motion, one "hero" number on Home; I did not use the
   experimental Expressive components (utility app, no compiler to check them).
6. Auto Backup is on for the database/settings; the PIN is deliberately excluded, so a restored phone asks for a new PIN.

## Moving your old data (two ways)
1. **Easiest:** deploy `legacy-export/old_app_with_export/` over your existing old app (same site). It is the old app
   plus one button, *Export data for the new Android app* in Settings (3 small edits; `app.js`/`db.js` untouched; cache
   version bumped so phones pick it up). Tap it, then in the new app: Settings → Restore backup.
2. Paste `legacy-export/export-old-data.js` into the browser console of the old app.
Both read `StudentPayDB` read-only. Restore shows a summary (skipped, duplicate and date-less records) and asks to
confirm before replacing anything; it runs as one transaction. Student ids are preserved.

## Layout
```
domain/   pure Kotlin: PaymentRules, Dashboard, Reports, ReportSpecs, Validation, PinSecurity, Backup   (tested)
data/     Room (entities, DAOs, repository), DataStore settings, PinRepository + KeystoreCrypto, BackupCodec/Service
pdf/      PdfReportWriter (PdfDocument), ReportService, PdfPreviewDocument (PdfRenderer)
ui/       theme, components, lock, home, students, payments, reports, settings, navigation
legacy-export/  export script, real sample output, patched copy of the old app
```

## Device QA checklist (not run: no device available)
First run PIN setup · wrong PIN waits · relaunch asks PIN · Change PIN · Forgot PIN erase · add/edit/delete student ·
tick/untick/Undo · Type A and Type B in January and December · Home numbers change with month/year · due filter ·
student detail grid · three PDFs preview and save (include a Bengali name and 60+ students for multi-page) ·
export then restore (and a restore of the sample in `legacy-export/`) · airplane mode · rotate · dark theme ·
small phone (320dp) and large font scale · TalkBack through lock screen and Payments.
