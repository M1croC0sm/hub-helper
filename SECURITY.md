# Security Policy

## Supported version

Security fixes are provided for the latest released version. Development and debug APKs are test artifacts and are not production-signed releases.

## Reporting a vulnerability

Report suspected vulnerabilities privately to neil.wolf@gmail.com with the subject `Hub Helper security report`. Include the affected version, reproduction steps, impact, and any suggested mitigation. Do not include real employee records, workplace documents, credentials, or exploit data belonging to another person.

Please allow a reasonable period for investigation and remediation before public disclosure. Routine bugs may use GitHub Issues once the repository is public; vulnerabilities should not be filed publicly.

## Security boundaries

- The app is designed without Android Internet permission or a hosted backend.
- Sensitive records and originals use app-private storage.
- Android cloud backup and device transfer are explicitly excluded.
- User-created ZIP exports leave the app security boundary and are unencrypted.
- Device compromise, insecure exported files, and inaccurate source documents are outside the guarantees the app can provide.
