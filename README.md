# Hub Helper

Hub Helper is a private, offline-first Android app for tracking attendance, PTO,
sick time, call-ins, holidays, and personal work notes. It keeps original work
documents on the device beside searchable, OCR-derived text.

The first release focuses on trustworthy attendance tracking and document
reference. Job-bid tracking is outside the project's scope. Natural-language
document Q&A is planned only after page-level citations and source provenance
are reliable.

## Project status

The Android/Compose app now includes persistent attendance and time-balance
ledgers, schedule presets, holidays, notes, a private OCR document library,
offline contract/policy search, reminders, backup export, app lock, and tested
policy calculations. Automatic 90-day credit timing and full printout-row import
remain under review.

- Product and implementation plan: [`PLAN.md`](PLAN.md)
- Technical architecture: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Detailed status and source needs: [`progress.md`](progress.md)

## Build

Requirements: JDK 17 and Android SDK 36.

```bash
./gradlew test assembleDebug
```

Install the debug build on a USB-connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In the app, open **Settings → Debug date** to enter an ISO date or move one day
at a time. The override persists across restarts, is clearly shown on Home, and
can be reset with **Use device date**. The debug menu also includes a point
falloff preview. Date overriding is disabled in release builds.
