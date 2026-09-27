# Hub Helper

Hub Helper is a private, offline-first Android app for tracking attendance, PTO,
sick time, call-ins, holidays, and personal work notes. It keeps original work
documents on the device beside searchable, OCR-derived text.

> **Unofficial project:** Hub Helper is not affiliated with or endorsed by
> Hubbell, Killark, the IBEW, or the IAM. It is not an authoritative employment
> record and should not be the sole basis for employment decisions.

The first release focuses on trustworthy attendance tracking and document
reference. Job-bid tracking is outside the project's scope. Natural-language
document Q&A is planned only after page-level citations and source provenance
are reliable.

## Project status

Version 0.12.0 adds transactional setup/call-in state, reviewed end-of-day attendance
reconciliation, durable booking durations and cancellation, linked actual usage,
PTO projections, multi-page PDF/image viewing, resumable OCR, page search and source
links, and validated format-7 backup Merge/Replace with recovery journals.

Current backups preserve setup, stable record identity, originals, and page/audit
metadata. Legacy formats 1–4 and 6 can be imported; undocumented format 5 is rejected.
Exports are unencrypted. Automatic 90-day credit awards remain deferred.

- Implementation sequence and acceptance gates: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)
- Current technical design: [ARCHITECTURE.md](ARCHITECTURE.md)
- Release and verification notes: [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)
- Publishing updates: [RELEASE.md](RELEASE.md)
- Privacy: [PRIVACY.md](PRIVACY.md)
- Distribution prerequisites: [OPEN_SOURCE_READINESS.md](OPEN_SOURCE_READINESS.md)

## Privacy and distribution status

The release app has no Android Internet permission. Records and imported
documents remain in app-private storage, Android backup and device transfer are
disabled, and OCR runs on-device. Explicit ZIP exports are unencrypted after
they leave the app.

This repository is intentionally private while redistribution rights for its
bundled workplace reference material are reviewed. Debug APKs are test builds;
corporate or broad distribution requires the release checklist above and a
protected production-signing process.

## Build

Requirements: JDK 17 and Android SDK 36.

```bash
./gradlew test lintDebug assembleDebug assembleRelease
```

Install the debug build on a USB-connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In the app, open **Settings → Debug date** to enter an ISO date or move one day
at a time. The override persists across restarts, is clearly shown on Home, and
can be reset with **Use device date**. The debug menu also includes a point
falloff preview. Date overriding is disabled in release builds.

Generate a manifest containing APK versions and SHA-256 checksums with:

```bash
python3 scripts/release_manifest.py
```

Debug APKs are installable test builds. The release APK is unsigned unless a protected
production signing process is supplied. The download website is updated only when an
actual release artifact is published; a local build does not change its download link.
