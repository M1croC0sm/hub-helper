# Publishing an update

The permanent download address is https://m1croc0sm.github.io/hub-helper/.
Users can also open it from Settings → Share Hub Helper → Open download page.
Android installs the downloaded APK as an update, preserving data, when the
application ID and signing key match and the build number increases.

## Signing baseline

Version 0.12.0 (build 41) starts the authoritative signing baseline. The old
0.11.1 signing key was lost. Users of 0.11.1 or older must export their backup,
uninstall once, install the new baseline, and import their backup. The website
explains this exception. The September 26 direct-send 0.12.0 APK already uses
the new baseline.

The retained key is `.signing/hub-helper.jks` on the maintainer's computer. It is
Git-ignored, mode 0600, and must never be committed or uploaded with a release.
It is a retained Android development key (alias `androiddebugkey`, conventional
password `android`), not a newly generated production key. Preserve a secure
backup outside this computer: losing it would require another reinstall.
The expected certificate fingerprint is recorded in `docs/release.json`.

CI debug APKs use the runner's own signing key and are **not distribution updates**.
The publisher rejects them. Existing builds remain test-channel releases.

## Routine release

1. Increment both `versionCode` and `versionName` in `app/build.gradle.kts`.
   Every published update needs a higher build number and a unique version/tag.
2. Run the checks and build:

   ```bash
   ./gradlew --no-daemon test lintDebug assembleDebug assembleRelease
   ```

3. Sign the minified release APK with the retained baseline key (adjust the SDK
   path for the current machine). This also lets future updates disable debug
   behavior without changing the application's identity:

   ```bash
   /home/mrwolf/Work/Android/Sdk/build-tools/36.0.0/apksigner sign \
     --ks .signing/hub-helper.jks --ks-key-alias androiddebugkey \
     --ks-pass pass:android --key-pass pass:android \
     --out build/hub-helper-signed.apk \
     app/build/outputs/apk/release/app-release-unsigned.apk
   ```

4. Install over the preceding website version on a test device and check retained
   records. Write release notes in a file, commit the source, and push it.
5. Preview release metadata without uploading:

   ```bash
   python3 scripts/publish_release.py build/hub-helper-signed.apk \
     --build-tools /home/mrwolf/Work/Android/Sdk/build-tools/36.0.0
   ```

6. Publish with the authenticated GitHub CLI:

   ```bash
   python3 scripts/publish_release.py build/hub-helper-signed.apk \
     --build-tools /home/mrwolf/Work/Android/Sdk/build-tools/36.0.0 \
     --notes /path/to/release-notes.md --publish
   ```

The publisher verifies the APK signature and identity, checks the build number,
creates a draft release, uploads `Hub.Helper.apk`, `release.json`, and
`SHA256SUMS`, and only then publishes the test release. It dispatches the Pages
workflow. It never replaces an existing release asset or commits a signing key.
A failed upload leaves a draft for inspection; finish or remove that draft
before retrying the same version.

GitHub Pages also refreshes for release publication/edit events and changes to
its source files. It reads the newest published release containing `release.json`
(including test prereleases), validates its signing fingerprint and APK metadata,
and renders the link, version, build, size, date and checksum together. Readers
do not need browser JavaScript or an unauthenticated GitHub API request to find
the correct APK. After publishing, verify **Deploy landing page** succeeded and
check the live page before announcing the update.

`--establish-baseline` is reserved for the one-time publication of the exact APK
pinned in `docs/release.json`. Ordinary updates must not use it. Keep the pinned
signing fingerprint unchanged. To preview the site locally:

```bash
python3 -m unittest discover -s scripts/tests -v
python3 scripts/download_page.py --github
python3 -m http.server 8765 --directory build/pages
```
