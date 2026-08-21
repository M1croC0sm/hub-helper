# Hub Helper publication-readiness research

Last reviewed: August 20, 2026

## Purpose and status

This is a living research and planning document for a possible future public
release of Hub Helper. It is not a declaration that the application is ready
for production, Google Play, employer approval, or open-source publication.

The application is still under active development. Items in this document can
change as functionality, dependencies, policies, and Google Play requirements
change. Recheck the linked primary sources before an actual release.

## Current overall assessment

Hub Helper has a strong privacy-oriented foundation and a working Android
application, but it is not yet ready for public publication. The largest
remaining areas are:

1. Reconcile the built application's network permissions and ML Kit telemetry
   with the privacy claims.
2. Resolve redistribution and trademark questions involving workplace and
   union documents, names, and branding.
3. Select the final product name, package ID, software license, and ownership
   model.
4. Establish production signing, automated release testing, and a repeatable
   release process.
5. Complete Google Play listing, policy, testing, and review requirements.

## Existing strengths

- The application targets Android API 36 and supports API 26 and newer.
- User records and imported originals are stored in app-private storage.
- Android cloud backup and device-to-device transfer are disabled.
- Cleartext network traffic is disabled.
- The FileProvider is not exported and grants temporary URI permissions.
- There are no accounts, advertisements, developer-hosted services, or social
  features.
- Users can remove individual records and reset local application data.
- Biometric/device-credential access is delegated to Android rather than
  storing biometric information in the application.
- OCR input and output are processed on-device by the bundled ML Kit model.
- The repository contains privacy, security, architecture, notice, and
  open-source-readiness documentation.
- Unit tests, Android lint, debug assembly, and the optimized release bundle
  currently complete successfully.
- The application uses shared domain calculations instead of separately
  reproducing attendance logic in the UI.

## Important current findings

### 1. Privacy and built permissions do not currently match

The source manifest does not directly request Internet access. However, the
merged debug and release manifests currently contain:

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.WAKE_LOCK`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.USE_BIOMETRIC`
- `android.permission.USE_FINGERPRINT`
- `android.permission.POST_NOTIFICATIONS`

Manifest-merger inspection shows:

- ML Kit's Google Data Transport dependency contributes `INTERNET`.
- WorkManager contributes network-state, wake-lock, boot, and foreground-service
  permissions.
- AndroidX Biometric contributes biometric and legacy fingerprint permissions.

Google states that ML Kit performs image/text processing on-device and does not
send the OCR input or result to its servers. Google also states that ML Kit may
contact its servers and sends device information, application information,
per-installation identifiers, performance metrics, API configuration, event
types, and diagnostic information for usage analytics and diagnostics.

This conflicts with the current privacy statements that the application has no
Internet permission, analytics, or transmission. Before publication, choose
and verify one of these positions:

#### Option A: retain ML Kit network metrics

- Keep the required permissions and SDK behavior.
- Update the privacy policy, in-app disclosures, Data Safety form, and store
  listing so they accurately describe ML Kit's collection.
- State clearly that workplace images, recognized text, attendance records,
  notes, and documents are processed locally and are not sent to Google by the
  OCR API.
- Verify the exact disclosures against the version of ML Kit shipped in the
  release.

#### Option B: enforce a no-network application

- Remove the merged network permissions or replace the dependency as needed.
- Confirm the bundled OCR model still initializes and works on every supported
  API level without a network connection.
- Inspect the final release artifact rather than relying on the source manifest.
- Perform network capture/testing to confirm the process cannot transmit data.
- Recheck every dependency update because manifest permissions can return.

Do not claim both "no Internet permission" and active ML Kit metrics collection.

Official references:

- [ML Kit terms and privacy](https://developers.google.com/ml-kit/terms)
- [ML Kit Android data-disclosure guidance](https://developers.google.com/ml-kit/android-data-disclosure)
- [Bundled and unbundled text-recognition options](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)

### 2. Bundled reference-document rights are unresolved

The app and repository contain workplace reference material, including a CBA,
attendance policy, schedules, and derived text. Before making the repository or
application public:

- Obtain written authorization to redistribute each document and transcription,
  or remove them from the distributed application.
- If the repository becomes public, remove unauthorized content from the full
  Git history, not only the latest commit.
- Confirm whether excerpts, derived summaries, holiday rules, schedules, and
  calculated rules are covered by any permission received.
- Retain evidence of the permission and any required attribution or restrictions.

### 3. Name, branding, and affiliation need a final decision

The project currently uses both `Hub Helper` and `Hubb Helper`. Choose one final
name before creating a store listing or signing a public release.

Also confirm:

- The name and icon do not infringe or imitate employer or union marks.
- References to Hubbell, Killark, IBEW, and IAM are factual and necessary.
- The store listing, screenshots, and application prominently avoid implying
  sponsorship, authorization, or official-record status.
- Any employer, union, legal, information-security, or supervisor review is
  documented when applicable.

### 4. The application ID should be finalized before publication

The current application ID is `app.hubhelper`. A Play-published package ID is
the permanent identity used for installs and updates. Before the first Play
upload, consider a reverse-domain identifier based on a domain or organization
that the publisher controls. Do not use an employer or union namespace without
authorization.

### 5. The release bundle builds but is not production-ready

`bundleRelease` currently succeeds and produces an optimized AAB. Current gaps:

- The AAB is unsigned because there is no production upload-key configuration.
- R8 reports Kotlin metadata compatibility warnings during minification.
- The minimized build has not yet received the full UI, OCR, database,
  backup/restore, reminder, and app-lock test suite.
- Debug APKs are development artifacts and must not be presented as public
  production releases.

Before publication:

- Align the Kotlin, Android Gradle Plugin, and R8 versions and eliminate the
  metadata warnings.
- Create a dedicated upload key with a validity period that satisfies Android's
  requirements.
- Keep the key and passwords out of Git and application source.
- Store encrypted recovery copies in at least two controlled locations.
- Enroll in Play App Signing and retain the upload certificate and account
  recovery information.
- Test the exact signed, shrunk artifact delivered by Google Play.

Official references:

- [Prepare an Android app for release](https://developer.android.com/studio/publish/preparing)
- [Sign an Android application](https://developer.android.com/studio/publish/app-signing)
- [Upload an Android App Bundle](https://developer.android.com/studio/publish/upload-bundle)

## Professional development readiness checklist

### Product and policy accuracy

- [ ] Define the supported use cases and explicitly list non-goals.
- [ ] Document every attendance, falloff, PTO, sick, call-in, holiday, and
      schedule rule with its authoritative source and effective date.
- [ ] Have another qualified person independently verify the calculations.
- [ ] Test boundary dates, leap years, year transitions, policy changes,
      duplicate entries, rescinded entries, and corrupted/incomplete imports.
- [ ] Make estimates visibly different from confirmed values.
- [ ] Preserve provenance so a user can understand why each total is shown.
- [ ] Keep the unofficial/non-authoritative disclaimer visible and accurate.
- [ ] Define how policy changes will be versioned and communicated.

### Privacy and security

- [ ] Create a complete data-flow inventory for user input, documents, OCR,
      exports, notifications, logs, dependencies, and operating-system services.
- [ ] Record a decision for ML Kit telemetry and network access.
- [ ] Verify final APK/AAB permissions after every dependency change.
- [ ] Complete a lightweight threat model covering storage, exports, malicious
      documents, FileProvider paths, process logs, screenshots, device compromise,
      and unauthorized physical access.
- [ ] Review all exported Android components and intent handling.
- [ ] Test that app lock covers every path that can reveal private records.
- [ ] Confirm no sensitive content appears in notifications, logs, crash reports,
      recent-app previews, test fixtures, or screenshots.
- [ ] Fuzz or constrain imported ZIPs, document types, file names, file sizes,
      page counts, and OCR input dimensions.
- [ ] Document that exported backups leave the app's protected storage and are
      not encrypted by Hub Helper.
- [ ] Arrange an independent privacy/security review before production.
- [ ] Maintain a private vulnerability-reporting channel and response procedure.

Android references:

- [Android security checklist](https://developer.android.com/privacy-and-security/security-tips)
- [Android privacy checklist](https://developer.android.com/privacy-and-security/about)
- [Android security-risk guidance](https://developer.android.com/privacy-and-security/risks)

### Automated quality assurance

- [ ] Run unit tests, Android lint, release lint, and `bundleRelease` in CI.
- [ ] Add Compose UI tests for setup, navigation, logging, editing, deletion,
      reset, theme changes, and app restart.
- [ ] Add database migration tests for every published schema version.
- [ ] Add process-death and saved-state tests.
- [ ] Test backup export and restore, including invalid and older backups.
- [ ] Test multi-page attendance documents as one logical document.
- [ ] Test OCR with synthetic clear, blurred, rotated, large, and malformed images.
- [ ] Test notifications after reboot, time changes, timezone changes, permission
      denial, and app hibernation.
- [ ] Test app lock with biometrics unavailable, canceled, failed, and changed.
- [ ] Add screenshot regression tests for all three themes and light/dark modes.
- [ ] Test small, normal, foldable, tablet, portrait, landscape, split-screen,
      and resized windows.
- [ ] Test font scales from 100% through 200% and increased display size.
- [ ] Test API 26, representative intermediate versions, and the latest API.
- [ ] Run accessibility checks for labels, touch targets, contrast, focus order,
      status meaning, and clipped content.
- [ ] Run selected tests on hosted physical devices before a public release.

Official references:

- [Testing different Android screen sizes](https://developer.android.com/training/testing/different-screens/tools)
- [Android accessibility testing](https://developer.android.com/guide/topics/ui/accessibility/testing)
- [Firebase Test Lab](https://firebase.google.com/docs/test-lab)

### Release engineering

- [ ] Use a unique, monotonically increasing version code.
- [ ] Maintain human-readable release notes and a changelog.
- [ ] Produce a signed AAB from a clean Git commit.
- [ ] Tag each production source revision.
- [ ] Record artifact hashes and build-tool versions.
- [ ] Generate an SBOM or equivalent dependency/license inventory.
- [ ] Enable automated dependency, vulnerability, and secret scanning.
- [ ] Ensure signing credentials and Play service credentials are never committed.
- [ ] Define release approval, rollback, hotfix, and support procedures.
- [ ] Test installation and upgrade from every previously published version.
- [ ] Retain release artifacts and mappings needed to interpret production crashes.
- [ ] Monitor Play pre-launch reports, Android Vitals, reviews, and support mail.

### User experience and support

- [ ] Complete the user manual and verify it against the release build.
- [ ] Provide accessible privacy, support, accuracy, and export warnings in-app.
- [ ] Make setup understandable without assistance.
- [ ] Provide useful empty, loading, failure, permission-denied, and recovery states.
- [ ] Test with colleagues using synthetic or their own private local data.
- [ ] Record beta feedback, defects, resolutions, and unresolved limitations.
- [ ] Publish a support contact and expected response policy.
- [ ] Decide how long old Android versions and old app versions are supported.

## Open-source readiness

Google Play publication does not require an application to be open source. If
the repository is eventually made public:

- [ ] Resolve document, transcription, artwork, brand, and contribution ownership.
- [ ] Remove unauthorized material and secrets from all Git history.
- [ ] Select an explicit software license for original code.
- [ ] Preserve all third-party notices and font licenses.
- [ ] Add contribution guidelines and a contributor license/developer-certificate
      policy if outside contributions will be accepted.
- [ ] Add issue and pull-request templates that prohibit real workplace records.
- [ ] Keep vulnerability reports out of public issues.
- [ ] Add CI status, supported versions, build instructions, release verification,
      and architecture documentation.
- [ ] Publish source corresponding to each public binary release.
- [ ] Clearly separate redistributable sample/reference content from private or
      user-imported content.

## Google Play publication process

### 1. Choose the legal publisher and account type

Google offers Personal and Organization developer accounts.

- Use a Personal account if this remains an independent personal project.
- Use an Organization account only when a real business, nonprofit, union, or
  other authorized organization owns and controls the publication.
- An Organization account requires a D-U-N-S number and website.
- Do not create an employer/union account or publish under its identity without
  written authorization.

Full distribution currently requires a one-time USD $25 registration fee and
identity verification. Account and contact requirements may expose specified
developer information publicly, so use a dedicated support email and consider
the privacy implications before registering.

References:

- [Choose a developer account type](https://support.google.com/googleplay/android-developer/answer/13634885)
- [Developer account setup and verification](https://support.google.com/android-developer-console/answer/16604405)
- [Developer contact-information requirements](https://support.google.com/googleplay/android-developer/answer/10840893)

### 2. Establish the publisher's public presence

Prepare:

- A stable developer name.
- A verified account and support email.
- A website under the publisher's control.
- A public, stable privacy-policy webpage.
- Support and private security-reporting instructions.
- Accurate legal identity and contact information for Google.

The public privacy policy must match the exact production binary and its SDKs,
not only the application's intended architecture.

### 3. Finalize application identity

Before the first upload:

- Choose `Hub Helper` or `Hubb Helper` consistently.
- Finalize the application/package ID.
- Select the launcher icon and developer name.
- Choose the default language, application category, free/paid status, and
  initial countries.
- Consider beginning with United States availability if workplace policies and
  references are specific to a US facility.

### 4. Prepare the store listing

Current listing requirements include:

- App title: up to 30 characters.
- Short description: up to 80 characters.
- Full description: up to 4,000 characters.
- Play icon: 512 x 512, 32-bit PNG with alpha, no more than 1,024 KB.
- Feature graphic: 1,024 x 500, JPEG or 24-bit PNG without alpha.
- At least two screenshots; four high-resolution phone screenshots are strongly
  preferable for promotional eligibility.
- Support email and privacy-policy URL.

The listing should:

- Explain attendance, PTO, sick time, call-ins, holidays, and private documents
  without overstating accuracy.
- State that it is unofficial and not endorsed by an employer or union.
- State that it is not an authoritative employment, payroll, leave,
  disciplinary, or legal record.
- Avoid confidential records and personally identifying data in screenshots.
- Avoid unapproved logos, documents, policy excerpts, or workplace photographs.

References:

- [Create and set up a Play application](https://support.google.com/googleplay/android-developer/answer/9859152)
- [Play preview-asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151)
- [Store-listing best practices](https://support.google.com/googleplay/android-developer/answer/13393723)

### 5. Complete Play Console App Content

Expect to complete at least:

- Privacy policy.
- Data Safety form.
- Ads declaration.
- Target-audience declaration.
- Content-rating questionnaire.
- App-access/reviewer instructions.
- Permission or special-category declarations if Google requests them.

Likely positioning, subject to final functionality:

- No advertising.
- Adult workforce/productivity audience rather than children.
- No account creation or developer-hosted backend.
- Sensitive employment information stored locally by the user.
- Explicit export initiated by the user.
- ML Kit diagnostics/usage collection disclosed if retained.

Because optional app lock can restrict screens, provide review instructions that
allow Google to exercise all functionality. Do not provide real credentials or
employee records.

References:

- [Prepare an app for review](https://support.google.com/googleplay/android-developer/answer/9859455)
- [Google Play Data Safety](https://support.google.com/googleplay/android-developer/answer/10787469)
- [Content-rating requirements](https://support.google.com/googleplay/android-developer/answer/9859655)
- [Target audience and content](https://support.google.com/googleplay/android-developer/answer/9867159)

### 6. Build and sign the Play artifact

1. Generate and protect an upload key.
2. Configure release signing without placing secrets in Git.
3. Resolve all release-only warnings and failures.
4. Build the release AAB from a clean, reviewed commit.
5. Test the shrunk release build.
6. Enroll in Play App Signing.
7. Upload the signed `.aab`; do not upload a development debug APK as production.
8. Inspect Play's generated device APKs and supported-device list.

As of August 31, 2026, new phone/tablet apps and updates must target Android 16,
API 36, or newer. Hub Helper already targets API 36, but the policy must be
rechecked at release time.

References:

- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Prepare and roll out a release](https://support.google.com/googleplay/android-developer/answer/9859348)

### 7. Use Play testing tracks

Recommended progression:

1. Internal test with trusted testers.
2. Closed test with colleagues using non-confidential or personal local data.
3. Address pre-launch report, crashes, accessibility, feedback, and policy issues.
4. Apply for production access when eligible.
5. Publish to a deliberately limited initial audience/country set.

For Personal developer accounts created after November 13, 2023, Google
currently requires a closed test with at least 12 testers opted in continuously
for at least 14 days before applying for production access. Google asks about
the test, feedback, and production readiness and may require additional testing.

References:

- [New Personal-account testing requirements](https://support.google.com/googleplay/android-developer/answer/14151465)
- [Configure internal, closed, and open tests](https://support.google.com/googleplay/android-developer/answer/9845334)
- [Play pre-launch report](https://support.google.com/googleplay/android-developer/answer/9842757)

### 8. Review, publish, and operate

- Leave at least a week of schedule buffer for review; reviews can take longer.
- Resolve all Play Console errors and seriously assess warnings.
- Verify store listing, privacy policy, declarations, countries, pricing, and
  release notes before submission.
- Monitor Android Vitals, reviews, support messages, policy notifications, and
  dependency advisories after launch.
- For later updates, use staged rollouts and halt them if new failures appear.
- Increase version code for every uploaded update.
- Revisit Data Safety whenever dependencies or data behavior change.
- Keep target API compliance current.

References:

- [Control review and publication](https://support.google.com/googleplay/android-developer/answer/9859654)
- [Staged update rollouts](https://support.google.com/googleplay/android-developer/answer/6346149)
- [Android Vitals](https://support.google.com/googleplay/android-developer/answer/9844486)

## Suggested development-phase roadmap

These phases are intentionally separate from feature development. They can be
completed when the application's behavior is stable enough to justify release
work.

### Phase 1: decisions and rights

- Decide the final name and publisher.
- Decide the final package ID.
- Obtain or reject document redistribution permission.
- Complete trademark/affiliation review.
- Choose the ML Kit privacy/network position.

### Phase 2: technical hardening

- Correct privacy behavior and documentation.
- Add instrumentation, migration, responsive-layout, accessibility, and release
  artifact tests.
- Resolve R8 warnings.
- Review dependencies and produce an SBOM.
- Complete an independent privacy/security review.

### Phase 3: release infrastructure

- Select the software license and open-source plan.
- Create CI release gates.
- Create and protect the upload key.
- Produce and test signed release AABs.
- Establish changelog, support, vulnerability, and rollback processes.

### Phase 4: Play preparation

- Create the verified developer account.
- Publish the website and privacy policy.
- Create store text and graphic assets.
- Complete App Content and Data Safety declarations.
- Begin internal testing.

### Phase 5: controlled validation and launch

- Run the required closed test.
- Document feedback and fixes.
- Clear the Play pre-launch report and policy review.
- Apply for production access.
- Launch in the intended region and monitor closely.

## Repeatable audit commands

Run these against the final source revision and retain the output with release
records. Paths may vary by Android SDK installation.

```bash
./gradlew clean test lintDebug bundleRelease
```

Inspect permissions in a built APK rather than relying only on the source
manifest:

```bash
apkanalyzer manifest permissions path/to/release.apk
```

Inspect the merged release manifest and merger sources:

```bash
rg -n "uses-permission" app/build/intermediates/merged_manifest/release
sed -n '1,160p' \
  app/build/intermediates/manifest_merge_blame_file/release/\
processReleaseMainManifest/manifest-merger-blame-release-report.txt
```

Confirm that an AAB has the intended signature before upload:

```bash
jarsigner -verify -verbose -certs path/to/app-release.aab
```

Never treat a successful build alone as publication approval. The exact signed
artifact must also pass policy, privacy, security, compatibility, upgrade, and
human review.

## Decision log

Record major release decisions here as they are made.

| Date | Decision | Rationale/evidence | Owner |
|---|---|---|---|
| Pending | Final product name | Choose Hub Helper or Hubb Helper | Pending |
| Pending | Final package ID | Must be settled before first Play upload | Pending |
| Pending | Publisher/account type | Personal or authorized organization | Pending |
| Pending | Reference-document distribution | Written permission or removal | Pending |
| Pending | ML Kit/network position | Accurate disclosure or enforced no-network build | Pending |
| Pending | Software license | Depends on ownership and open-source decision | Pending |

