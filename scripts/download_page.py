#!/usr/bin/env python3
"""Render the download page from verified release metadata, without browser API calls."""
import argparse
import html
import json
from pathlib import Path
import re
import shutil
import subprocess
from datetime import date

ROOT = Path(__file__).resolve().parents[1]
REPO = 'M1croC0sm/hub-helper'
BASE = f'https://github.com/{REPO}/releases'


def validate(data, expected_signer):
    assert data['applicationId'] == 'app.hubhelper', 'Wrong application ID'
    assert re.fullmatch(r'\d+\.\d+\.\d+(?:[.-][A-Za-z0-9.-]+)?', data['version']), 'Invalid version'
    assert data['tag'] == 'v' + data['version'], 'Tag/version mismatch'
    assert type(data['versionCode']) is int and data['versionCode'] > 0, 'Invalid build number'
    assert type(data['minSdk']) is int and data['minSdk'] >= 26, 'Invalid minimum SDK'
    assert type(data['sizeBytes']) is int and data['sizeBytes'] > 0, 'Empty APK'
    assert re.fullmatch(r'[a-f0-9]{64}', data['sha256']), 'Invalid checksum'
    assert data['signerSha256'] == expected_signer, 'Signing key changed: existing installations cannot update'
    assert data['channel'] in ('Test build', 'Release'), 'Invalid channel'
    date.fromisoformat(data['publishedAt'])
    return data


def gh(*args):
    return subprocess.check_output(['gh', *args], text=True)


def current_release():
    baseline = json.loads((ROOT / 'docs/release.json').read_text())
    releases = json.loads(gh('api', f'repos/{REPO}/releases?per_page=100'))
    for release in sorted((r for r in releases if not r['draft']), key=lambda r: r['published_at'], reverse=True):
        assets = {a['name']: a for a in release['assets']}
        if 'release.json' not in assets:
            continue
        data = json.loads(gh('api', f"repos/{REPO}/releases/assets/{assets['release.json']['id']}", '-H', 'Accept: application/octet-stream'))
        validate(data, baseline['signerSha256'])
        assert data['tag'] == release['tag_name'], 'Manifest belongs to another release'
        apk = assets.get('Hub.Helper.apk')
        assert apk and apk['size'] == data['sizeBytes'], 'APK missing or wrong size'
        if apk.get('digest'):
            assert apk['digest'] == 'sha256:' + data['sha256'], 'Published APK checksum mismatch'
        assert data['versionCode'] >= baseline['versionCode'], 'Release would downgrade the website'
        return data
    published = next((r for r in releases if not r['draft'] and r['tag_name'] == baseline['tag']), None)
    assert published, 'Pinned baseline has not been published; keep the existing live page'
    apk = next((a for a in published['assets'] if a['name'] == 'Hub.Helper.apk'), None)
    assert apk and apk.get('digest') == 'sha256:' + baseline['sha256'], 'Pinned APK is not available or its checksum differs'
    return validate(baseline, baseline['signerSha256'])


def render(data, output):
    baseline = json.loads((ROOT / 'docs/release.json').read_text())
    validate(data, baseline['signerSha256'])
    output.mkdir(parents=True, exist_ok=True)
    for filename in ('styles.css', 'site.js', 'favicon.svg'):
        shutil.copy2(ROOT / 'docs' / filename, output / filename)
    replacements = {**data,
        'downloadUrl': f"{BASE}/download/{data['tag']}/Hub.Helper.apk",
        'releaseUrl': f"{BASE}/tag/{data['tag']}",
        'sizeMb': f"{data['sizeBytes'] / 1024 / 1024:.1f}",
    }
    template = (ROOT / 'docs/index.html').read_text()
    for key, value in replacements.items():
        template = template.replace('{{' + key + '}}', html.escape(str(value), quote=True))
    assert not re.search(r'\{\{\w+\}\}', template), 'Unresolved template field'
    (output / 'index.html').write_text(template)
    (output / 'release.json').write_text(json.dumps(data, indent=2) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--github', action='store_true', help='Use newest published release with release.json')
    parser.add_argument('--manifest', type=Path, default=ROOT / 'docs/release.json')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/pages')
    args = parser.parse_args()
    render(current_release() if args.github else json.loads(args.manifest.read_text()), args.output)
    print(args.output)
