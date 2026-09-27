# Version 0.12.0 implementation status

Prepared September 26, 2026. Version 0.12.0, build 41.

## Delivered behavior

- End-of-day attendance statement reconciliation, editable/rejectable source
  rows, possible-overlap warnings, stable import identities, dated reconciliation
  audit, and correction/rescission without silent opening-balance compensation.
  Undated legacy balances retain explicitly uncertain expiration.
- Exact minute arithmetic, saved booking durations, requested/approved/taken/
  cancelled states, partial-day bookings, effective-dated schedule history,
  and explicit actual-usage/call-in links. Approved bookings deduct automatically
  on their date. Cancellation removes the booking deduction; independently
  recorded actual usage remains until corrected separately.
- Room-backed setup/business state and transactional allowance changes. Existing
  setup preferences are retained during migration. Schema 5 has explicit upgrade
  paths from schemas 1–4. Startup no longer deletes matching attendance records.
- Format-7 snapshot exports and staged Merge/Replace restores, stable identities,
  checksum validation, archive bounds, atomic restore journals, recovery cleanup,
  and last-export status. Known legacy formats 1–4 and 6 remain importable;
  undocumented format 5 is rejected. Legacy archive receipts prevent importing
  the same manifest twice; different legacy archives can still overlap.
- Document tombstones preserve source identities. Per-page PDF/image viewing,
  zoom/pan, durable OCR jobs, retry/cancel, page text/geometry storage, FTS search,
  category filtering, title edits, editable attendance review, and evidence links.
- Split navigation/Home/Settings/manual code, retained operation state and errors,
  lifecycle-aware ledger subscriptions, consistent app dates, and saved navigation
  and simple input drafts. Generated holidays support persistent suppression and
  reviewed overrides. App lock checks authenticator availability and protects
  window previews; disabled reminders are checked again before notification.
- CI builds debug and minified release APKs, runs tests/lint, and uploads reports
  and a generated artifact checksum/version manifest. Reset cancels work and
  removes business data, originals and staging; appearance preferences remain.

## Architecture choices

The implementation retains three Gradle modules. Home, Attendance and Calendar
share a ledger ViewModel because they require the same inputs; Documents owns its
search state. Repositories use concrete constructor dependencies instead of adding
an interface for every class. Dated setup snapshots and audit payloads use Room
business-state rows rather than a separate table for every setup field. These are
smaller adaptations of the proposed architecture, documented in ARCHITECTURE.md.

## Verification

- `./gradlew --no-daemon test lintDebug assembleDebug assembleRelease` passes.
- 84 test executions pass: 57 domain tests and 27 app tests, including the storage
  suite on APIs 26 and 28. Coverage includes schemas 1–4, FTS maintenance,
  transactional call-in failure/repeated deletion, backup round trips/repeated
  merge, missing-original rejection, journal recovery, and statement boundaries.
- Lint has no errors. Remaining warnings/hints are not presented as a clean lint
  baseline; see the generated reports under each module's `build/reports`.
- An isolated Android API 36 emulator is used for installation, setup and basic
  navigation/logging smoke checks. The final APK completes setup, saves a synthetic
  work note, and displays it in Documents; the emulator crash buffer is empty.
  This is not a physical-device certification.
- The merged release manifest is inspected for Internet permission and backup
  settings. APK versions and SHA-256 values are generated from Gradle metadata by
  `python3 scripts/release_manifest.py`.

## Remaining acceptance and distribution gates

The original plan remains the fuller acceptance checklist; building the app does
not prove every proposed gate. Remaining validation includes a populated real-world
upgrade fixture, more injected restore failures, large-history performance,
TalkBack/large-font/theme checks, biometric/credential lifecycle behavior, and
reminders across reboot/time-zone changes on representative physical devices.

Some planned refinements remain: date filtering and visual OCR-region highlights,
full lazy-list conversion for large collections, preservation of complex review
and camera drafts across process death, and a unified search across bundled and
personal documents. OCR jobs persist; an interrupted initial-setup preview may
need reopening. Legacy unknown schedule/provenance remains uncertain by design.

The debug APK is installable with the development key. The release APK is built
and minified but remains unsigned until a production signing key is configured.
The initial implementation did not publish distribution artifacts. The subsequent
website update establishes 0.12.0 as the signing baseline; see RELEASE.md for the
one-time legacy reinstall and the verified publishing workflow. Existing content-redistribution and software-license review
requirements remain in effect. Automatic 90-day credit awards, encrypted portable
backups and document Q&A remain deferred as specified in the plan.
