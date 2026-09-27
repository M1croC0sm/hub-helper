# Hub Helper Architecture

## Scope and boundaries

Hub Helper is a single-user, offline Android application. There is no backend,
account, analytics service, or Internet permission. The minimum Android version
is API 26. Keep the existing three Gradle modules:

- `core:domain`: Android-independent models, exact minute values, half-points,
  policy calculations, and parsers.
- `core:data`: Room entities/DAOs, repositories, migrations, and coordination of
  operations involving database references and original files.
- `app`: Compose features, ViewModels, application operations, platform OCR,
  reminders, lock, setup conversion, and backup adapters.

Home, Settings, navigation, and manual content are separate files. `LedgerViewModel`
combines loaded repository streams into one presentation state for Home,
Attendance, and Calendar. `DocumentsViewModel` owns asynchronous page search.
`AppOperations` runs user writes in a retained ViewModel scope, prevents concurrent
submissions, and presents operation failures. Compose retains simple drafts and
navigation using saved state. `AppContainer` supplies repository dependencies.

The retained shared-ledger ViewModel is intentional: these three screens need the
same consistent inputs. Separate ViewModels for each small screen are unnecessary
until their state lifecycles diverge. Repositories still expose concrete types;
a larger interface/module extraction can follow demonstrated substitution needs.

## Database and business state

Room schema 5 migrates explicitly from schemas 1–4. It adds stable record identities,
booking duration/status/usage links, generated-holiday metadata, business-state
and audit records, restore receipts, document tombstones, page text and FTS.
Committed schema JSON is the migration contract.

`SetupStore` migrates the existing preferences into Room once, retaining their
original values. The setup record and its audit history are committed together.
Preferences remain the store for interface/device settings. Call-in transactions
change their annual counter and event together. Backup restoration includes setup
within its database transaction.

Legacy setup strings are preserved rather than silently rounded during migration;
new minute inputs are validated exactly at the boundary. `Minutes` represents
exact whole minutes; decimal-hour rendering uses an explicit rounding policy and
is never persisted as a replacement transaction. Half-points remain integers.

## Attendance and reconciliation

Only confirmed events affect the confirmed total. Individual charges expire on
their 12-month anniversary. Recorded credits and the existing negative-one floor
remain supported; estimated 90-day dates do not award credits automatically.

A reviewed statement records a reported balance through the selected end-of-day
boundary. The reconciliation calculates an opening remainder relative to the
confirmed dated history at that boundary. Later events and expiration change the
result. Unknown opening history has no invented expiration dates. Snapshot date,
setup values, and reconciliation audit are stored together.

Correcting/rescinding an event changes its contribution. Adding historical detail
through the explicitly labelled past-evidence tool preserves the reported balance
transactionally. Normal startup never deletes apparently duplicate events.

Imported rows use a stable document/row identity. A repeated accepted row is
skipped; a changed previously accepted row must be corrected explicitly. Page
references are retained only where the parser can identify them unambiguously.
Policy uncertainty is visible; no new employment-policy interpretation is inferred.

## Time off and holidays

New bookings retain duration, stable ID, and requested/approved/taken/cancelled
state. Approved bookings deduct automatically on their date. Cancelled/requested
bookings do not deduct. Explicit links between bookings and actual usage/call-ins
avoid double counting. Legacy date-only matching remains confined to migrated
bookings marked with an assumed duration.

Changing a shift preserves saved booking durations and records an explicit effective
date plus a schedule-change audit. Default imported booking durations consult this
history. Legacy schedules without a known start date remain labelled as assumed;
unknown past shifts are never reconstructed silently.

Generated holidays have stable identities and suppression state. Reviewed additions
can supersede generated entries. Existing legacy holiday dates remain authoritative
rather than being destructively reclassified without source information.

## Documents and OCR

Original files remain unchanged in private storage and retain SHA-256 checksums.
PDFs and image collections are viewed by page using bounded bitmap sizes. Mixed
collections are flattened in original order for viewing/OCR.

Unique WorkManager jobs process pages, retain completed OCR results, and support
retry/cancellation. Pages store text, status, and recognized line/bounding-box
metadata. Room FTS searches page text; hits open the original page. Bundled policy
reference search remains a separate local-text search experience.

Reviewed import changes are separate from original OCR. Users can reject rows,
edit dates/point amounts/types, and open the source page before accepting them.
Deleting an original retains a tombstone so dependent records can disclose the
unavailable source. Page text/index entries are removed with that deletion.

## Backup and recovery

Current exports use format 7, containing schema-versioned rows, stable identities,
setup/audit state, page metadata, and originals. The export uses a database snapshot
and a private staging ZIP. Files are checksum-checked and the archive is read back
before copying to the chosen destination. Exports are unencrypted.

Restore extracts into random staging filenames with entry/count/size limits. It
validates before applying changes, supports Merge or Replace, and detects conflicting
stable records rather than silently overwriting them. Merge skips identical records.

Filesystem changes and Room transactions are not jointly atomic. New originals use
unique paths journaled before copying. Database changes commit together. Startup
recovery removes unreferenced promoted files while retaining committed originals.
Existing records/files survive a failed replacement transaction.

Formats 1–4 and 6 have a legacy import adapter and archive receipts preventing repeat
import of the same legacy manifest. Undocumented format 5 is rejected. Cross-archive
legacy deduplication cannot be guaranteed because those formats omit stable IDs.

## Privacy and verification

The merged release manifest removes Internet permission and excludes Android cloud
backup and device transfer. App lock requires an available supported authenticator;
locked-mode windows suppress screenshots/recent-app previews. Notifications remain
generic. Reset cancels work and removes business data, originals, and import/capture
staging; appearance preferences are intentionally retained.

Tests include domain boundaries, parsers, exact-minute formatting, booking lifecycle,
backup validation, Room/FTS behavior, schema 1–4 migration, restore round trips,
interruption recovery, and statement end-of-day reconciliation. Robolectric runs
storage tests on APIs 26 and 28; emulator/device checks supplement these tests.
CI builds debug and minified release variants and retains verification artifacts.
Production signing, physical-device accessibility/biometric checks, and content
redistribution review remain distribution gates, not claims made by a successful build.
