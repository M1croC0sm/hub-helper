# Open-source and corporate-readiness checklist

## Current release position

The repository must remain **private** until the reference-content ownership items below are resolved. Debug APKs are suitable for trusted testing only.

## Verified technical privacy properties

- [x] No Android Internet permission.
- [x] No analytics, advertising, cloud account, or developer backend.
- [x] On-device OCR with the bundled ML Kit model.
- [x] App-private record and document storage.
- [x] Cloud backup and device-to-device transfer disabled.
- [x] Non-exported FileProvider limited to camera-capture cache.
- [x] Explicit user action and warning for unencrypted ZIP exports.
- [x] Reset and individual deletion controls.
- [x] Automated unit tests, Android lint, and debug assembly.

## Required before public visibility

- [ ] Obtain written permission to redistribute the bundled CBA, attendance policy, schedules, and derived transcriptions, or remove them from the full Git history before changing visibility.
- [ ] Confirm the project name and icon do not violate employer trademark or branding rules.
- [ ] Complete supervisor, HR, union, information-security, and legal review as applicable; document that the app is voluntary and unofficial.
- [ ] Review all policy calculations against current authoritative documents.
- [ ] Select and document an open-source software license after confirming ownership of all original contributions.
- [ ] Create a production signing key and protected release process; never commit the key or passwords.
- [ ] Publish signed release checksums and an SBOM/dependency inventory.
- [ ] Complete the Google Play Data safety form if Play distribution is used.
- [ ] Add device tests for import, reset, backup, lock behavior, and process recreation.
- [ ] Have an independent reviewer perform a privacy and security review.

## Recommended sharing model

Use a small private tester group until the checklist is complete. Share only versioned, signed releases through an approved channel. Avoid real employee data in issues, screenshots, logs, sample documents, tests, and demonstrations.

