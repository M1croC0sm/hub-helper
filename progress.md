# Hub Helper Progress

Last updated: August 16, 2026

## Current Status

The project has a buildable Android app with Room persistence, editable
attendance events, PTO/sick adjustments, schedule presets, holidays, work
notes, debug-date testing, private document import, bundled on-device image OCR,
offline contract/policy search, weekly reminders, backup export, and optional
app lock. Domain tests cover falloffs, credits entered from a verified source,
printout parsing, and reported schedules.

Automatic 90-day credit generation remains intentionally disabled because the
printed policy and sample history do not yet establish whether the credit is
applied exactly on day 90 or on the following processing day. Credits can be
entered explicitly and remain auditable.

See `PLAN.md` for the MVP and milestones and `ARCHITECTURE.md` for technical
decisions.

## Purpose

Hub Helper is a private, offline Android app that combines employment documents
with practical tools for tracking attendance, PTO, sick time, call-ins,
vacations, holidays, and personal work notes.

The app may eventually be shared with coworkers, so it should be professional,
approachable, and clearly labeled as an unofficial employee reference that is
not affiliated with or endorsed by Hubbell, the IBEW, or the IAM.

## Current Scope

- Preserve original employment documents on the device.
- Create an on-device full-text search index from derived OCR text.
- Let users browse the original document with page numbers and versions intact.
- Answer natural-language contract questions using an on-device language model,
  retrieved passages, and citations. 
- Extract reviewed rules and link them to their original source passages.
- Use deterministic application logic for balances, deadlines, attendance
  points, calendars, and alerts.
- Keep personal records separate from contract and policy documents.
- Track PTO, paid sick time, call-ins, vacations, holidays, attendance events,
  and personal work notes.
- Scan, categorize, search, and retain general work-related documents locally.
- Preserve every original scan or imported PDF beside its OCR-derived text.

Job-bid tracking is explicitly outside the current scope.

Visual aesthetics will be discussed later.

PTO and sick balances will initially use manual input because the authoritative
values are shown on a login-protected pay-stub site. Initial first- and
second-shift defaults are recorded in `WORK_SCHEDULES.md`; overtime is activated
per date rather than assumed.

## Product Layers

1. Original documents are the authoritative evidence.
2. Searchable text and reviewed structured rules support retrieval and
   calculations.
3. A local language model retrieves and explains relevant passages but does not
   replace the source or perform deterministic calculations.

## Proposed UX

The app should not require daily bookkeeping. Its primary interaction model is
event-based logging plus an optional one-minute weekly check-in.

### Main Areas

1. Home
   - Current attendance points
   - Next point falloff
   - PTO and sick-time summary
   - Upcoming deadline or reminder
   - Prominent "Log something" action
2. Attendance & Time
   - Log an absence, call-in, vacation, sick day, or holiday
   - View a point-expiration timeline
   - Review PTO history and balances
   - View calendar entries and calculation explanations
3. Contract
   - Browse original pages
   - Search locally
   - Ask questions
   - Review structured rules and their source passages
4. Documents
   - Scan or import work-related documents
   - Categorize documents as Attendance, PTO, Pay, Benefits, Policies, or Other
   - Search OCR-derived text
   - View Original, Text, and possibly Split views
   - Link personal records to supporting document pages

The "Log something" action should initially offer:

- Missed work
- Called in
- Used time off
- Add note
- Scan document

Setup should be progressive and mostly optional. Initial information may
include hire date, preferred reminder schedule, current time-off balances, and
an attendance-statement scan. Additional details should be requested only when
a feature needs them.

### Weekly Check-In

At a user-selected day and time, send a privacy-safe notification such as:

> Anything from this workweek to log?

The review can ask whether the user missed work, called in, used time off, or
wants to add a note. Every question can be skipped, and "My week is up to date"
should finish the review immediately. The reminder must be optional and should
not expose attendance details on the lock screen.

## Attendance Point Falloff

The contract does not define point falloff; the separate supplied attendance
policy does. Its transcription says individual points drop 12 months after the
date issued. This anniversary rule is implemented and tested in the domain
scaffold. The policy separately awards an attendance credit after 90 days with
no new points; 90 days is not the point-expiration period.

Before automating attendance credits, the original policy must confirm:

- The policy's effective date and version
- How a credit interacts with a half-point balance
- Whether corrected, disputed, or rescinded events reset the 90-day clock
- The exact clock behavior after an immediately consumed credit
- How excused, corrected, disputed, or rescinded events are handled

### Supplemental Seven-Point Rule

The user reports an additional rule not printed in the available attendance
policy: reaching seven points twice within one year results in dismissal. It is
tracked separately in `SUPPLEMENTAL_POLICY_RULES.md`. The app may surface a
clearly labeled warning. A rolling 12-month period is the user's provisional
interpretation, pending confirmation. Automatic evaluation remains blocked
until the period, second qualifying occurrence, correction handling, and
effective date are defined by a written source or authoritative clarification.

An attendance event is expected to store:

- Occurrence date
- Event type
- Point value
- Status: pending, confirmed, excused, disputed, or rescinded
- Calculated falloff date when supported by policy
- Attendance-policy version
- Supporting document and page
- Optional personal notes

If a statement contains only a current total without dated occurrences, the app
may save it as an opening balance, but individual falloff dates must remain
unknown.

## Document Scanning

Scanning and OCR must occur on the device. Each saved document should retain:

- Untouched original page images or imported PDF
- Derived OCR text
- Title and category
- Document date and effective date when known
- Scan/import date
- Optional notes
- Links to records created from the document

Re-running OCR may replace derived text but must never modify or discard the
original. Extracted attendance rows must be presented for user confirmation and
checked against any total printed on the source before affecting calculations.

The app should support device-backed protection, disable cloud backup by
default, offer an app/biometric lock, and allow users to remove original scans
when they explicitly choose to do so.

## Contract Review Notes

The repository contains a cleaned CBA transcription suitable for early search
and rule analysis. It still is not an authoritative page source: dependable
original page images/PDF pages are needed for citations. Covers, page numbers,
blank pages, effective dates, and inserts should be preserved.

### Overtime Language Requiring Clarification

The current text contains overlapping notification and cancellation language:

- Article 13 Section 3 says volunteers are sought three days before weekend
  overtime, mandatory overtime is announced two days before by 2:00 p.m., and
  cancellation occurs by noon on the preceding day.
- Article 13 Section 7 says mandatory weekend overtime is posted by 2:00 p.m.
  Thursday.
- Article 15 says production employees generally receive two hours' notice,
  Saturday overtime is announced by 2:00 p.m. Thursday, and Saturday overtime
  is cancelled by 3:00 p.m. Friday. Maintenance and tool-room employees may be
  required to work overtime without notice.

The clearest discrepancy is the noon versus 3:00 p.m. Friday cancellation
deadline. The differences may reflect shifts, departments, circumstances, or
contract drafting. Hub Helper should show all applicable passages, flag possible
overlap, and recommend clarification instead of silently choosing one rule.

## Documents Needed Next

Priority inputs:

1. Original attendance-policy images, especially any page showing the effective
   date/version. A cleaned transcription is already present.
2. A redacted sample attendance or point statement showing dates, descriptions,
   point values, and total if possible.
3. The cleanest available contract or handbook, as an original PDF or careful
   page-by-page scan.

Useful later but not blockers:

- Current plant holiday calendar
- Written call-in instructions or posted call-in policy
- Sample PTO balance statement or redacted pay stub
- PTO rules that exist outside the contract
- Nonstandard shift calendars
- Updated policy notices issued after the contract
- Benefits summaries if benefits questions are included
- A redacted grievance form if grievance-deadline reminders remain in scope

## Next Step

Confirm the exact 90-day credit boundary and test the holiday-calendar import
when the calendar arrives. Full OCR row import must continue to require user
review and reconciliation before creating events.
