# Changelog

All notable user-facing changes to Hub Helper are recorded here.

## 0.9.8 — 2026-08-29

### Changed

- Reference documents now render with structured headings, typography, and spacing closer to a readable PDF while retaining all source text.
- Added contract-based annual plant-holiday generation, including observed Saturday/Sunday dates and the second-shift Thursday rule for Friday holidays. Floating birthday and roving holidays remain personal and are not generated.
- Annual PTO, sick-day, and call-in calculations continue to reset from the contract rules at the start of each calendar year; scanned calendars remain available for company-specific additions.
- Document previews can now be dragged while zoomed.

## 0.9.7 — 2026-08-29

### Changed

- Redesigned the Reference screen with compact searchable cards and a next-holiday summary while preserving full holiday and document access.
- Document previews now open in a larger portrait-aware viewer with pinch zoom and OCR text.
- Second-shift reminders default to Thursday at 12:37 when no reminder schedule has been saved.

## 0.9.6 — 2026-08-29

### Changed

- Simplified document categories to attendance, holiday calendar, PTO exception form, pay, and other.
- Removed document search and added a direct document viewer with zoomable original previews and OCR text.
- Corrected calendar attendance wording to describe points accrued while retaining the red point marker.
- Android Back now returns from app sections to Home instead of leaving the app.
- Reminder setup now handles notification permission correctly and provides a test notification action.

## 0.9.5 — 2026-08-29

### Fixed

- Existing normalized duplicate attendance rows are cleaned up once at startup, while distinct events on the same date remain separate.

## 0.9.4 — 2026-08-29

### Fixed

- Attendance rows are sorted by their dates before missing point changes are inferred, so pages uploaded out of order still produce chronological records and the final running total.

## 0.9.3 — 2026-08-29

### Fixed

- Multi-page attendance imports now continue past the first page’s signature footer and use the final page’s running total.
- Added regression coverage for signature blocks at the bottom of page one.

## 0.9.2 — 2026-08-29

### Changed

- Attendance-sheet confirmation reconciles the imported running total with the app’s current attendance balance, preventing historical rows from inflating the total.
- Setup now asks for the available floating vacation-day allowance and preserves it in backups.
- Missing attendance-row point amounts continue to be inferred from consecutive running totals.
- Attendance scans now show a plain-language confirmation list of dates, point changes, and running totals before saving.
- Setup asks users to confirm the same OCR result immediately after its attendance pages are read.

## 0.9.1 — 2026-08-29

### Changed

- Confirmed attendance-sheet rows are now permanent dated attendance records and appear in Calendar, history, and calculations.
- Repeated scans skip matching dated events using a normalized row identity while preserving distinct same-day details.
- Attendance OCR parsing requires the attendance-table header and row evidence, ignoring unrelated dates and footer text.
- Import confirmation reports how many rows were saved versus already present.
- Updated the manual and import wording to describe confirmation and durable records.

## 0.9.0 — 2026-08-29

### Added

- A Calendar destination replacing the former Records screen.
- A twelve-month year overview with stable attendance-point intensity bands and
  printed monthly accrued-point totals.
- A conventional month/day calendar with a permanently visible two-row legend.
- Dated markers for point falloffs, confirmed and estimated 90-day credits,
  accrued points, call-ins, sick time, PTO, floating holidays, corrections, and
  plant holidays.
- Day details with entry explanations, attendance editing, removal controls, and
  a LOG action that preselects the chosen date.
- Calendar filters and direct Home shortcuts for PTO, sick time, call-ins,
  holidays, and recent activity.
- Accessibility descriptions that communicate event meaning without relying on
  color alone.

### Changed

- Attendance Details is now a focused drill-down instead of part of a combined
  records ledger.
- Estimated credits use an outlined marker while confirmed credits use a solid
  marker.
- Sick-time markers use a theme-aware neutral color so they remain visible in
  both light and dark modes.
- The user manual now documents Calendar navigation and symbols.

### Removed

- The duplicated Records navigation destination and combined records ledger.

## 0.8.0 — 2026-08-20

- Added Industrial Instrument, Clear & Easy, and Soft & Friendly themes with
  system, light, and dark appearance modes.
- Redesigned Home, Attendance Details, LOG, and attendance history surfaces.
- Bundled offline fonts and their open-source licenses.
