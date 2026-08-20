# Hub Helper Delivery Plan

## MVP boundary

The MVP is useful without OCR automation or a language model. It will let one
person record attendance events, see an explainable confirmed balance and next
expiration, maintain PTO/sick balance snapshots, receive an optional private
weekly reminder, and browse/search imported authoritative documents.

MVP includes:

- progressive local setup and an unofficial-reference disclosure;
- attendance event entry, edit, status, history, totals, and source links;
- reviewed 12-month point expiration with visible calculation explanations;
- PTO and sick-time snapshots, simple positive-number usage entry, hire-date
  tenure, and reviewed January 1 resets;
- original PDF/image import, metadata, page browsing, and local text search;
- optional weekly check-in and generic notifications;
- app/biometric lock, backup disabled, export, and deletion controls; and
- an offline user manual available from Settings; and
- accessibility, empty/error states, and automated tests for calculations.

PTO and sick opening balances are manually entered from the user's pay stub;
pay-stub login or automated pay-stub import is not required. Normal time use is
entered as a positive number and subtracted internally. Exceptional additions
or corrections belong in Settings.

On first launch, setup asks for current PTO, sick, and attendance-point balances
and offers an attendance-points-sheet image/PDF attachment. Every item can be
deferred, and setup can be reopened from Settings. Until reviewed OCR parsing is
implemented, the printed point total is entered manually and the sheet remains
supporting evidence.

MVP does not include:

- job-bid tracking;
- cloud sync, accounts, coworker data sharing, or remote analytics;
- automatic policy interpretation;
- natural-language Q&A/local LLM;
- automatic attendance-statement extraction that changes balances without
  review; or
- automatic resolution of conflicting contract/policy language.

## Milestones

### 0. Foundation — complete

- Record product scope, privacy constraints, source discrepancies, and UX areas.
- Add the Compose Android shell and pure Kotlin domain module.
- Encode and test only the reviewed 12-month anniversary rule.
- Add a debug-only persistent date override and point-falloff preview for device
  testing without changing the device clock.

Exit evidence: `test` and `assembleDebug` pass; app opens to four primary areas.

### 1. Local attendance ledger — implemented

- Add Room entities, migrations, repositories, and test fixtures.
- Build onboarding and attendance event create/edit screens.
- Show confirmed/pending totals, history, next expiration, and explanations.
- Require explicit confirmation before imported information affects totals.

Exit evidence: events survive restart; boundary tests and repository tests pass;
every calculated value can identify its inputs and policy version.

### 2. Time-off and weekly review — implemented

- Add PTO/sick opening snapshots and manual usage history.
- Add the exact hire date, display length of service, calculate the reviewed
  Article 14 vacation tier, and offer a January 1 reset confirmation. Reset the
  annual sick day to eight hours for both shifts, per the confirmed operational
  rule recorded in `SUPPLEMENTAL_POLICY_RULES.md`.
- Replace signed daily adjustments with “Use PTO” and “Use sick time” actions;
  keep additions and corrections under Settings.
- Replace manual plant-holiday entry with a reviewed annual-calendar scan and
  retain the source image/PDF.
- Add editable first- and second-shift presets from `WORK_SCHEDULES.md`, plus
  per-date overtime activation and schedule overrides.
- Add optional weekly review and privacy-safe notifications.
- Add calendar/history views without requiring daily bookkeeping.

Exit evidence: balances reconcile from snapshots and events; reminders can be
skipped/disabled and expose no attendance detail on the lock screen.

### 3. Authoritative document library — in progress

- Import PDFs and images into app-private storage without altering originals.
- Capture version/effective-date metadata and stable page identifiers.
- Add on-device OCR, Room FTS, browse/search, and Original/Text views.
- Link attendance and time-off records to source pages.

Exit evidence: OCR can be regenerated without changing the original; search
opens the cited page; checksum and deletion behavior are tested.

### 4. Privacy, recovery, and MVP release — in progress

- Add biometric/app lock, explicit export/import, and safe deletion flows.
- Complete the built-in user manual with setup, attendance, time-off, document,
  privacy, backup, and troubleshooting guidance.
- Complete accessibility, performance, device, migration, and restore testing.
- Review legal/unofficial wording and produce a signed release build.

Exit evidence: release checklist passes on supported Android versions and no
network permission or unintended backup path exists.

### 5. Post-MVP intelligence

- Evaluate an on-device model against device size, speed, and quality targets.
- Add retrieval with page citations and conflict-aware answers.
- Add reviewed statement-row extraction with total reconciliation.

This phase proceeds only after authoritative page sources are available and the
non-AI search/reference experience is dependable.

## Open product decisions

These do not block milestone 1:

- final visual direction and app icon;
- whether encrypted exports should support transfer between devices;
- which document categories besides attendance, PTO, pay, benefits, and policy
  should be first-class; and
- whether local Q&A is valuable enough to justify its storage/battery cost.

## Inputs requested from the user

Before milestone 3 or policy-credit automation:

1. Original or page-by-page attendance policy images, including any page that
   shows its effective date/version.
2. A redacted sample attendance/point statement with dates, descriptions,
   values, and printed total, if available.
3. The cleanest original contract PDF or complete page scans with page numbers.

Useful later: a redacted PTO balance example, current holiday calendar, and any
written updates or side letters that modify the supplied policy or CBA.

One supplemental rule is already recorded from the user: reaching seven points
twice within one year reportedly results in dismissal. Exact written wording or
an authoritative clarification is still needed before automatic evaluation.
