#!/usr/bin/env python3
"""Prepare and optionally publish an update-compatible APK. Signing keys stay local."""
import argparse
from datetime import date
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess

from download_page import ROOT, REPO, current_release, validate


def inspect_apk(apk, tools):
    certs = subprocess.check_output([str(tools / 'apksigner'), 'verify', '--print-certs', str(apk)], text=True)
    signers = re.findall(r'certificate SHA-256 digest: ([a-f0-9]{64})', certs)
    assert len(signers) == 1, 'Expected one APK signer'
    badging = subprocess.check_output([str(tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    minimum = re.search(r"sdkVersion:'(\d+)'", badging)
    assert package and minimum, 'Unable to read APK metadata'
    with apk.open('rb') as stream:
        checksum = hashlib.file_digest(stream, 'sha256').hexdigest()
    return dict(applicationId=package[1], versionCode=int(package[2]), version=package[3],
                tag='v' + package[3], signerSha256=signers[0], minSdk=int(minimum[1]),
                sha256=checksum, sizeBytes=apk.stat().st_size, publishedAt=date.today().isoformat(),
                channel='Test build')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--build-tools', type=Path, required=True, help='Android SDK build-tools directory')
    parser.add_argument('--notes', type=Path, help='Release notes file; required for publishing')
    parser.add_argument('--establish-baseline', action='store_true', help='One-time publication of the exact APK pinned in docs/release.json')
    parser.add_argument('--publish', action='store_true', help='Upload a prerelease and refresh the website')
    args = parser.parse_args()
    baseline = current_release() if args.publish and not args.establish_baseline else json.loads((ROOT / 'docs/release.json').read_text())
    data = validate(inspect_apk(args.apk, args.build_tools), baseline['signerSha256'])
    if args.establish_baseline:
        pinned = json.loads((ROOT / 'docs/release.json').read_text())
        assert data['sha256'] == pinned['sha256'] and data['versionCode'] == pinned['versionCode'], 'Baseline must be the exact pinned APK'
    else:
        assert data['versionCode'] > baseline['versionCode'], 'Increment versionCode before publishing an update'
    if args.publish:
        assert args.notes and args.notes.is_file(), 'Provide release notes with --notes'
        assert not subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip(), 'Commit the release source before publishing'
    output = ROOT / 'build/publication' / data['tag']
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(args.apk, output / 'Hub.Helper.apk')
    (output / 'release.json').write_text(json.dumps(data, indent=2) + '\n')
    (output / 'SHA256SUMS').write_text(data['sha256'] + '  Hub.Helper.apk\n')
    print(json.dumps(data, indent=2))
    if not args.publish:
        print(f'Prepared {output}; no release was published.')
        return
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    subprocess.run(['gh', 'release', 'create', data['tag'], '--repo', REPO, '--target', commit,
                    '--title', f"Hub Helper {data['version']}", '--notes-file', str(args.notes),
                    '--prerelease', '--draft', str(output / 'Hub.Helper.apk'),
                    str(output / 'release.json'), str(output / 'SHA256SUMS')], check=True)
    # Publish only after all assets have uploaded successfully. Failed uploads leave a draft.
    subprocess.run(['gh', 'release', 'edit', data['tag'], '--repo', REPO, '--draft=false'], check=True)
    subprocess.run(['gh', 'workflow', 'run', 'pages.yml', '--repo', REPO, '--ref', 'main'], check=True)
    print('Published. Check the Deploy landing page workflow before announcing the update.')


if __name__ == '__main__':
    main()
