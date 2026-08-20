# Hub Helper Architecture

## Architecture decisions

Hub Helper is a single-user, offline-first Android application. Version 1 has no
server component, user account, analytics SDK, advertising SDK, or Internet
permission. The minimum supported Android version is Android 8 (API 26).

The code is organized around three boundaries:

1. **UI:** Jetpack Compose screens and state holders. UI code presents results
   and explanations but does not calculate policy outcomes.
2. **Domain:** Pure Kotlin models and deterministic policy calculators. Points
   are stored as integer half-points rather than floating-point numbers. This
   layer has no Android dependencies and is covered by unit tests.
3. **Data:** Room will store structured records and full-text-search data.
   Original PDFs and page images will live in app-private files. Database rows
   will store stable references to those files and their derived OCR text.

The Gradle modules are `app`, `core:domain`, and `core:data`. Room database code
is isolated from the UI and policy engine in `core:data`.

## Data ownership and provenance

Original scans and imported PDFs are evidence and must never be rewritten by
OCR or normalization. Derived OCR text can be regenerated. Any reviewed rule or
personal record derived from a document can link to:

- document identifier and immutable original file;
- page number or page-image identifier;
- document effective date and policy version, when known;
- exact supporting text span, when available; and
- review state and date.

Rules learned from an employee report, verbal notice, or later practice are
stored separately from transcribed documents with explicit provenance and a
verification state. They are never inserted into the text of a printed source.

Contract/policy data and personal records use separate tables and repositories.
Deleting a personal event must not delete its source document. Deleting an
original document requires an explicit warning and leaves affected records
marked as having a missing source.

## Planned local data model

- `Document`: title, category, dates, version, original-file metadata, checksum
- `DocumentPage`: stable page number, original image/PDF position, OCR status
- `TextBlock`: page, reading order, text, optional bounding box, FTS content
- `ReviewedRule`: typed rule payload, source passage, review/version metadata
- `AttendanceEvent`: date, type, half-points, status, source, notes
- `BalanceSnapshot`: PTO/sick balance at a date and optional source
- `TimeOffEvent`: type, amount, status, date range, optional source
- `ReminderPreference`: weekly check-in schedule and privacy settings

The version-1 Room schema is committed under `core/data/schemas`. Future schema
changes require explicit migrations and migration tests.

## Attendance calculation boundary

Only confirmed events affect the confirmed total. Pending, excused, disputed,
and rescinded events remain visible but do not silently affect it. Each result
must be explainable from stored events and a versioned rule.

The reviewed attendance transcription states that individual points expire on
their 12-month anniversary. The 90-day provision is a separate attendance
credit. Annual expiration is implemented in the domain scaffold. The credit
engine is deferred until the original policy's effective date/version and edge
cases are reviewed, including how credits interact with half-points, corrected
events, and a negative-one balance.

## Search and document Q&A

Room FTS provides the first searchable document experience. Results always open
the original page. OCR confidence or unreadable passages should be disclosed.

On-device natural-language Q&A is a later enhancement. It may retrieve and
summarize passages, but it must cite pages, show conflicting passages, and never
perform balances or deadline calculations. The deterministic domain layer owns
those calculations.

## Privacy and resilience

- Android backup is disabled by default.
- App data uses platform app-private storage; device-backed encryption is the
  baseline. Biometric/app lock is planned before handling production data.
- Notifications contain generic wording on the lock screen.
- Export is explicit, user-initiated, and warns that exported files leave the
  app's protection boundary.
- Destructive operations require confirmation and are tested for referential
  integrity.

## Built-in user manual

The app includes an offline manual under Settings. Manual content ships with and
is versioned alongside the app, requires no network connection, and covers
setup, calculations, document provenance, privacy, backup/export, debug-build
testing, and troubleshooting. Relevant empty and error states should link to
the applicable manual section where practical.

## Testing strategy

- Domain unit tests cover anniversary boundaries, half-points, statuses, and
  later all reviewed attendance/PTO rules.
- Repository tests cover Room migrations, search, and source links.
- Instrumented tests cover document import, process recreation, and privacy.
- A small set of redacted source fixtures will test statement extraction and
  reconciliation without including personal production data.
