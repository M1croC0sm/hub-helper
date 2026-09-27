# Hub Helper Reliability and Architecture Implementation Plan

Prepared: September 26, 2026
Baseline: working tree for version 0.11.1, build 40
Status: implementation delivered in version 0.12.0 (build 41); see
[implementation status](IMPLEMENTATION_STATUS.md) for verification and open release gates.

## Objective

Make existing records recoverable, calculations consistent, and document evidence
traceable before adding more automation. Preserve the offline Android design and
the existing `app`, `core:domain`, and `core:data` modules. Deliver small, reviewable
changes with explicit data migrations instead of a rewrite.

This plan supersedes milestone completion claims in `PLAN.md` where they conflict
with the assessed implementation. `PLAN.md` remains the product-scope reference.

## Constraints and working assumptions

- Preserve existing user records, originals, and uncommitted work. Inspect the
  four currently modified source/test files before starting implementation; do
  not discard or silently overwrite those changes.
- Keep network permission, analytics, cloud sync, accounts, and automatic Android
  backup disabled. Do not add job-bid tracking or document Q&A in this plan.
- Keep production policy behavior unchanged unless a change is explicitly
  identified, explained, and supported by a reviewed source. A calculation bug
  fix is distinct from a new interpretation of employment policy.
- Keep the 90-day credit date explicitly estimated until its edge cases and
  source version are reviewed; do not start awarding credits automatically.
- Retain original files byte-for-byte. OCR, thumbnails, and normalized text are
  replaceable derived data.
- Use synthetic or redacted fixtures. Never put personal records in tests,
  logs, source control, or CI artifacts.
- Prefer lightweight constructor injection and feature packages initially. Add
  more Gradle modules or a dependency-injection framework only for a demonstrated
  need.
- Implement the plan locally in focused branches/commits. Publication, signing,
  and distribution remain separate release actions.

## Baseline findings to track

| ID | Finding | Resolution |
| --- | --- | --- |
| B1 | Export writes format 6; import accepts only 1–4 | Phase 1 |
| B2 | Exact division of minutes by 60 can throw | Phase 1, then typed values in Phase 2 |
| B3 | Backup omits payday anchor and loses some provenance on restore | Phases 1 and 3 |
| B4 | Restore can partially apply; repeated imports can duplicate records | Phase 3 |
| B5 | Startup deduplication can delete distinct matching attendance records | Phase 1 |
| B6 | Room records and preference balances can diverge | Phases 2 and 4 |
| B7 | Attendance correction and statement reconciliation share implicit offset behavior | Phase 4 |
| B8 | Bookings use current shift duration; usage matching guesses from date | Phase 5 |
| B9 | Generated holidays survive shift changes and reappear after deletion | Phase 5 |
| B10 | Personal document search, page models, complete viewing, and PDF OCR are missing | Phases 7 and 8 |
| B11 | Source deletion leaves unresolved links; source pages are not preserved | Phases 7 and 8 |
| B12 | UI owns write coordination; state and failures are inconsistently handled | Phases 2 and 6 |
| B13 | Repository, migration, restore, and device workflows lack test coverage | Every phase, with release gates in Phase 9 |
| B14 | Documentation and release status do not consistently match implementation | Phases 0 and 9 |

## Phase 0 — Establish a reproducible baseline

**Scope**

1. Inspect the existing working-tree changes and record which behavior they add.
2. Configure the documented JDK and Android SDK; finish obtaining the Gradle
   wrapper and dependencies through the normal build process.
3. Run `./gradlew test lintDebug assembleDebug` and retain the new results.
4. Inventory the version-4 Room schema, preference keys, backup fields, and
   document storage layout. Record which state is intentionally excluded from
   backups, such as device-specific lock configuration and transient URIs.
5. Create synthetic fixtures for legacy databases and backup versions with
   known formats. Do not invent a version-5 schema: recover its actual format
   from history/artifacts before deciding whether to support it.
6. Capture a smoke-test checklist for setup, logging, calendar, documents,
   export, import, reset, and app lock.
7. Mark planned versus implemented features in project documentation. Record
   behavior decisions locally before large changes, consistent with the intent
   of `CONTRIBUTING.md`; no external issue posting is required for this plan.

**Acceptance criteria**

- A fresh build/test result replaces reliance on the stored September 4 reports.
- Any baseline failures are recorded separately from changes introduced later.
- Synthetic fixtures have documented expected records, balances, and checksums.
- Existing source changes remain intact.

## Phase 1 — Repair immediate recovery and calculation defects

**Depends on:** Phase 0. Keep this patch small enough to ship independently.

**Backup compatibility**

- Define a shared backup-version contract used by exporter and importer.
- Support current format 6 and explicitly mapped legacy formats. Reject unknown
  formats before any persistent mutation; do not merely widen an integer range.
- Add `paydayAnchor` to export and import. Preserve attendance source page and
  policy metadata rather than rebuilding them with defaults.
- Validate required files, available checksums, enums, dates, and numeric values
  before restoring any records. Missing required originals must fail clearly.
- Fail an export if an original referenced by its manifest is missing. Report
  failure in the UI and identify incomplete output; do not report success.
- Until Phase 3, explicitly label restore as additive and disclose that this
  patch does not yet make persistence failures or repeated imports safe.

**Calculation and startup safety**

- Replace exact minute-to-hour divisions in calculations and formatting with a
  shared explicit formatting policy. Preserve minutes as the underlying value;
  never write rounded display values back into the ledger.
- Cover both hours and sick-day display paths. Prefer hours/minutes when decimal
  display would obscure the exact amount.
- Remove destructive duplicate cleanup from normal startup. Do not delete more
  historical records while a safer import-identity model is being implemented.
- Surface errors from backup operations and prevent duplicate submissions while
  an operation is in progress.

**Targeted tests**

- Export current data and restore into an empty test database; compare all
  supported fields and original-file hashes.
- Valid known legacy archives, unsupported versions, malformed dates/enums,
  missing originals, corrupt hashes, and archives beyond size limits.
- Minute amounts of 1, 2, 6, 15, 59, 60, negative adjustments, and zero; include
  calendar and sick-time formatting.
- Restarting the app does not delete two distinct matching attendance records.

**Acceptance criteria**

- The app can restore its own exports and retains the supported data fields.
- Valid minute amounts cannot trigger a decimal-division exception.
- Malformed archives fail before writes; runtime failure atomicity remains a
  clearly tracked Phase 3 requirement.
- No automatic attendance deletion occurs on launch.

## Phase 2 — Introduce typed business state and transactional operations

**Depends on:** Phase 1.

**Domain and storage changes**

- Introduce validated minute, half-point, shift, and allowance types. Parse text
  at UI/import boundaries and return actionable validation errors. Check ranges
  and overflow, not just whether text can be parsed.
- Move opening balances and manually entered annual allowances into dated Room
  records. Keep theme and display preferences in the preferences layer.
- Preserve the distinction between employer-reported snapshots, user corrections,
  and individual events. Store origin, effective date, recorded time, and review
  state. Do not infer that legacy data has stronger provenance than it does.
- Retain integer primary keys if useful internally; add stable UUIDs for export,
  import identity, and relationships across restored installations.
- Add indexes and uniqueness constraints based on identity. Avoid constraints
  that forbid legitimate same-day events with the same amounts.
- Separate immutable creation timestamps from modification timestamps and keep
  a correction history for records affecting balances.
- Add an application container that constructs repositories, clock, and
  operations. Define repository interfaces at the domain/application boundary.
- Add initial application operations for recording time usage and call-ins, so
  all affected business records are written in a single Room transaction.

**Preference migration protocol**

1. Read and validate legacy values without changing them.
2. Write converted records and a migration marker in one database transaction.
3. Switch reads to Room only after successful completion.
4. Re-running after interruption must not create duplicate snapshots.
5. Retain the legacy values during the transition; do not clear them before
   successful migration verification. Invalid values enter a visible review path.

**Acceptance criteria**

- Pre/post-migration totals match on supported legacy fixtures, except separately
  approved bug corrections which are explained to the user.
- Failed call-in writes cannot decrement an allowance without recording the event.
- Migration runs once and survives interruption/retry.
- Tests migrate representative databases from versions 1–4 through the new schema.
- Opening balances that cannot be converted exactly are never silently rounded.

## Phase 3 — Make backup and restore resilient

**Depends on:** Phase 2; extends the Phase 1 compatibility patch.

**Design**

- Use typed archive models and explicit version adapters. Bump the archive format
  when the new business-state models require it; keep known old readers/adapters
  covered by fixtures.
- Export a consistent snapshot of records and their required documents, rather
  than taking unrelated lists from whatever the UI has loaded.
- Coordinate export with document deletion so originals cannot disappear during
  archive creation. Verify the completed archive before reporting success.
- Restore into staging first: validate manifest, duplicate entry names, record
  relationships, checksums, file counts, expanded size, and individual entry
  limits. Treat archive names as data, never trusted filesystem paths.
- Present a preview with record/document counts and conflicts. Offer **Merge**
  and **Replace** with an explicit explanation of their effects.
- Merge by stable ID. Identical records are skipped; conflicting versions require
  a defined resolution choice and must not be silently overwritten.
- For legacy archives without stable IDs, detect repeated import of the same
  archive and flag ambiguous overlaps. Do not promise automatic deduplication
  across different legacy archives.
- Use a restore journal plus staged files and a Room transaction. Filesystem and
  database changes are not one atomic transaction: promote uniquely named files,
  commit their references transactionally, and recover/clean staged or orphaned
  files after interruption. Retain old files until replacement commits.
- Add a restore-in-progress guard and a recovery path that prevents normal use of
  an incompletely applied restore.
- Track last successful backup time and display completion or failure clearly.

**Acceptance criteria**

- Reimporting a current-format backup makes no duplicate records.
- Truncated archives and simulated storage failures leave existing active data
  usable; restart completes recovery or removes abandoned staged work.
- Replace does not delete existing usable data before validation and staging.
- Restored balances, source links, schedules, settings included by the format,
  and file hashes match the exported snapshot.
- Explicit exports remain clearly labelled unencrypted. Portable encrypted
  exports are a separate enhancement, not a prerequisite for restoring reliably.

## Phase 4 — Separate attendance corrections from reconciliation

**Depends on:** Phases 2 and 3.

**Behavior decisions to specify before implementation**

- Define whether a snapshot includes events on its effective date. Prefer an
  explicit coverage boundary and reconciliation membership over a date-only
  assumption, since a statement and later event can occur on the same day.
- Define treatment of undated opening points: they must remain visibly unknown
  for expiration purposes until connected to reviewed dated records.
- Define one place to apply balance limits to the complete result. Preserve
  event history so clamping does not silently erase or invent transactions.
- Keep policy uncertainty explicit, especially permanent credits, credit timing,
  and the negative-one floor. Resolve source-dependent questions before changing
  those semantics; leave automation disabled when evidence is insufficient.

**Implementation**

- Introduce `CorrectAttendanceEvent` and `ReconcileAttendanceStatement` operations.
- Correcting/rescinding a logged event recalculates its applicable contribution.
  Reconciliation records a dated reported balance and an explicit adjustment or
  linkage; it does not silently rewrite an opening remainder in UI code.
- Show reported balance, calculated balance, difference, included rows, uncertain
  rows, and proposed changes before committing a reconciliation.
- Link confirmed imported events to stable source rows, not normalized note text.
- Retain a reviewed history and reason for corrections. Prefer rescinding over
  irreversible deletion for records that have participated in reconciliation.
- Return an explanation model alongside each calculated total, reused by Home,
  Details, Calendar, and reconciliation previews.

**Acceptance criteria**

- Correcting an incorrectly logged one-point event changes the calculated total
  according to the explicit correction rules.
- Adding historical statement detail does not count the same point twice.
- Tests cover same-day snapshot boundaries, partial histories, edits after
  reconciliation, statuses, future events, leap-day anniversaries, expiration,
  credit/floor interactions, and repeated imports.
- Legacy opening remainders remain traceable; no migration invents event dates.

## Phase 5 — Model time off, schedules, and holiday exceptions explicitly

**Depends on:** Phases 2–4.

**Time off and schedules**

- Add requested, approved, taken, and cancelled booking states and a stored
  duration in minutes; support partial days.
- Link actual usage to a booking explicitly. Remove date-only suppression of
  booking deductions once legacy ambiguous matches have been reviewed.
- Store effective-dated schedules. New bookings capture their intended duration;
  editing the current shift must not recalculate historical usage.
- Preserve legacy booking behavior during migration with an explicit assumed
  duration and review flag where historical schedule information is missing.
- Define how approved bookings become usage when their date arrives. Show the
  current automatic assumption clearly until the replacement workflow is ready;
  do not silently change past deductions.
- Present available balance, future reserved time, and projected balance on a
  selected date, with the underlying assumptions visible.
- Apply configured floating-holiday allowances in validation as well as display.
  Keep birthday-month restrictions and cancellation/rebooking behavior consistent.

**Holidays**

- Give generated holidays stable identities, a source/rule version, applicable
  shift, and override/suppression records.
- Recompute the generated view when the schedule changes without leaving obsolete
  generated dates. Preserve historical applicability.
- Let a reviewed company calendar supersede a generated date explicitly.
- Make deleting/hiding a generated holiday persistent rather than recreating it
  on the next render or launch.

**Acceptance criteria**

- Switching shifts does not alter historical PTO usage or leave duplicate
  observed holidays.
- A booking plus linked usage is counted once; unrelated same-day adjustments
  remain separate. Call-ins cannot inadvertently double-count linked PTO usage.
- Tests cover partial days, cancellation, insufficient projected balance,
  floating allowances of 0/1/2, birthday restrictions, and year transitions.
- Holiday overrides and suppressions survive restart and backup/restore.

## Phase 6 — Move orchestration out of Compose

**Depends on:** Phase 2; migrate each feature after its operations stabilize in
Phases 4–5. Avoid one large UI rewrite.

1. Add feature ViewModels for Home, Attendance, Calendar, Documents, and Settings.
2. Expose immutable UI state with loading, ready, saving, empty, and error states.
   Do not present unloaded data as an authoritative zero balance.
3. Move write coordination into application operations. Compose submits intents
   and renders results; it no longer adjusts preference balances directly.
4. Make `MainActivity` responsible primarily for app hosting and platform hooks.
5. Split `HubHelperApp.kt` into navigation, feature screens, reusable components,
   and manual content. Remove duplicate/dead manual routes after verifying links.
6. Preserve navigation and important form drafts through saved state. Report a
   successful save before closing the form; keep failed submissions editable.
7. Introduce one clock/date provider for calculations, reference views, and debug
   tools. Refresh the date on foreground entry and across midnight. Keep release
   builds free of date override behavior.
8. Use lifecycle-aware subscriptions and lazy lists for growing histories and
   document collections. Load data off the main thread where appropriate.

**Acceptance criteria**

- Recreating the activity preserves the selected screen and important drafts.
- Repeated taps cannot submit the same operation twice while saving.
- Failed writes show a recoverable error and do not update displayed counters as
  though persistence succeeded.
- All relevant screens agree on the selected calculation date.
- Large synthetic histories remain usable, measured on an agreed test device.

## Phase 7 — Add document pages and durable OCR

**Depends on:** Phases 2, 3, and the Documents portion of Phase 6.

**Storage and processing**

- Add document-page records with stable page identity, original page position,
  OCR status, derived text, and processing metadata. Add text blocks with reading
  order and bounding boxes where the recognizer supplies them.
- Index existing PDFs and image archives without rewriting their originals.
  Retain legacy OCR until successful regeneration; do not invent exact page links
  for flattened text that cannot be mapped reliably.
- Add page navigation, page count, zoom/pan, and bounded image decoding. Render
  and load pages on demand rather than decoding a whole document into memory.
- Add PDF page rendering for on-device OCR. Bound page dimensions, job size, and
  memory use, and disclose unsupported/encrypted/corrupt documents clearly.
- Run persistent OCR work with unique jobs, per-page progress, retry/cancel,
  interruption recovery, and guaranteed recognizer/file cleanup.
- Coordinate deletion with active processing. Keep failure messages separate
  from searchable OCR text and propagate cancellation correctly.

**Evidence and deletion**

- Link attendance rows and time-off evidence to document pages and supporting
  passages. Mark unknown page references honestly during migration.
- Show which records reference a document before deleting it.
- Preserve dependent records and source metadata with an explicit unavailable
  source state. Use a deletion journal/tombstone where needed to coordinate Room,
  file removal, background jobs, and derived artifacts.

**Acceptance criteria**

- Every page of a supported PDF or multi-image import is viewable in order.
- OCR can resume after interruption, be retried, and be cancelled safely.
- Original checksums remain unchanged after OCR and indexing.
- Linked records remain readable after source deletion and clearly identify the
  missing original. All new metadata survives backup/restore.

## Phase 8 — Add searchable evidence and editable import review

**Depends on:** Phases 4 and 7.

- Add a Room full-text index for derived page text and an index strategy for
  bundled references. Keep original files outside the index.
- Search personal documents with title/category/date filters, snippets, result
  counts, and page links. Preserve the identity and location of bundled passages.
- Add document rename/metadata editing and practical note search/editing.
- Open results at the exact page; highlight the relevant text region when known.
- Redesign OCR review to edit or reject individual detected rows while viewing
  their source page. Preserve original extracted text separately from corrections.
- Show extraction warnings and inferred values. Do not manufacture OCR confidence
  percentages when the engine does not provide them.
- Import only explicitly accepted rows using stable source-row identities and
  the reconciliation operation. Include a duplicate/conflict preview.
- Let users open evidence from attendance details, calendar items, and bookings.

**Acceptance criteria**

- Search hits open the correct original page or bundled passage.
- Editing/rejecting one proposed row leaves other proposed rows unchanged.
- Reconfirming the same reviewed import does not create duplicate events.
- Indexes can be rebuilt without changing originals, reviewed records, or links.

## Phase 9 — Verify accessibility, privacy, release behavior, and documentation

**Depends on:** completed features above; run relevant checks during each phase.

- Test setup, logging, reconciliation, document review, and recovery with large
  fonts and a screen reader. Check focus order, touch targets, labels, contrast,
  and non-color calendar cues across the three themes.
- Test app lock on representative supported Android versions, including no
  available authenticator, device credential fallback, background/foreground,
  activity recreation, camera/file-picker return, and recent-app previews.
  Define and implement the desired screen-preview privacy behavior explicitly.
- Test reminders for permission denial, disabling, device restart, time-zone/DST
  changes, and rescheduling. Disabling must prevent future notifications.
- Define full reset precisely, including pending workers, staged imports, cached
  captures, documents, business data, and preferences. Verify no old job can
  recreate deleted data; document any intentionally retained appearance setting.
- Add repository/migration tests and a small device workflow suite to CI. Run
  `test lintDebug assembleDebug`; separately build and inspect the minified release
  variant so release-only behavior is covered.
- Inspect the merged release manifest for Internet permission and unintended
  backup/transfer paths. Keep personal information out of logs and notifications.
- Verify production signing configuration through a protected release process.
  Generate version labels, website metadata, and checksums from one release
  manifest to prevent drift. Do not publish as part of implementation alone.
- Reconcile `README.md`, `ARCHITECTURE.md`, `PLAN.md`, the manual, privacy text,
  changelog, and public-release readiness checklist with delivered behavior.
- Keep redistribution rights and software-license decisions visible as existing
  release prerequisites. Do not change the repository's visibility implicitly.

**Acceptance criteria**

- A populated older installation can upgrade, calculate, export, reset, restore,
  and reopen source pages without losing records.
- The critical workflows pass on representative minimum, intermediate, and target
  Android versions and on at least one physical device before broad release.
- Release documentation distinguishes implemented behavior, estimates, unsupported
  inputs, and deferred features without contradictory completion claims.

## Suggested pull-request sequence

Each item should include its relevant tests and documentation rather than defer
all validation to the final phase. Split further if a migration becomes large.

| PR | Scope | Prerequisite |
| --- | --- | --- |
| 1 | Fresh baseline, fixtures, reproduction cases | None |
| 2 | Backup format compatibility and field preservation | 1 |
| 3 | Minute arithmetic and removal of startup deletion | 1 |
| 4 | Typed values, stable IDs, business-state migration | 2, 3 |
| 5 | Transactional operations and application container | 4 |
| 6 | Staged, consistent, idempotent backup/restore | 5 |
| 7 | Attendance correction/reconciliation and explanations | 6 |
| 8 | Booking durations, usage links, schedule history | 7 |
| 9 | Holiday identities and reviewed overrides | 8 |
| 10 | Feature ViewModels, saved state, clock, error handling | 5; feature moves follow 7–9 |
| 11 | Document pages, multi-page viewer, source deletion model | 6, Documents state from 10 |
| 12 | Durable OCR and PDF processing | 11 |
| 13 | Full-text search and editable evidence review | 7, 12 |
| 14 | Accessibility, privacy/device checks, release automation, documentation | 8–13 |

PRs 2 and 3 are the first stabilization checkpoint. PRs 4–7 establish trustworthy
state and recovery. PRs 8–10 improve daily behavior and maintainability. PRs 11–13
complete document evidence workflows. PR 14 is the broad-release verification
checkpoint. This order describes dependencies, not a requirement for parallel
agents or simultaneous implementation.

## Validation and completion policy

- Write regression tests around each confirmed failure before fixing it.
- Use migration tests with populated historical schemas, repository tests for
  transaction behavior, pure tests for calculations, and device tests only where
  platform behavior matters.
- Inject failures at archive validation, file promotion, transaction commit, and
  restart recovery boundaries. A happy-path round trip alone is insufficient.
- Compare balances before/after migrations with explicit expected changes. Never
  silently accept an unexplained difference as part of refactoring.
- Keep old schema and archive fixtures. Do not use destructive Room migration or
  assume installing an older APK is a safe database rollback strategy.
- For changes spanning several releases, keep transitional readers until the new
  storage is verified, and prefer forward fixes plus verified backups for recovery.
- Mark a task complete only when its acceptance criteria are demonstrated and its
  user-facing documentation is updated. A passing build alone is not completion.

## Deferred decisions and features

- Automatic 90-day credit awards require reviewed policy evidence and agreed
  edge cases. The rest of this plan can proceed without them.
- Confirmed: statement balances cover the end of the selected day; later events
  change the balance. Legacy undated remainders retain uncertain expiration.
- Confirmed: approved bookings deduct automatically on their date and may be
  cancelled; linked actual usage remains a separate auditable record.
- Encrypted portable backups can follow verified restore. Choose an established,
  reviewed format and recovery UX rather than inventing cryptography.
- Natural-language Q&A can be reconsidered only after page citations, provenance,
  and ordinary search pass their acceptance criteria.
- No calendar-duration estimate is committed until Phase 0 verifies the build and
  fixture availability. Track progress by completed acceptance gates and revise
  estimates after the first stabilization checkpoint.
