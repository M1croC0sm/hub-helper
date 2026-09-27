import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from download_page import ROOT, render, validate, current_release


class DownloadPageTests(unittest.TestCase):
    def setUp(self):
        self.release = json.loads((ROOT / 'docs/release.json').read_text())

    def test_version_link_and_checksum_come_from_one_manifest(self):
        data = dict(self.release, version='1.2.3', tag='v1.2.3', versionCode=123)
        with tempfile.TemporaryDirectory() as folder:
            render(data, Path(folder))
            page = (Path(folder) / 'index.html').read_text()
            self.assertIn('/download/v1.2.3/Hub.Helper.apk', page)
            self.assertIn('Version 1.2.3', page)
            self.assertIn('Build 123', page)
            self.assertIn(data['sha256'], page)
            self.assertNotIn('{{', page)
            self.assertEqual(json.loads((Path(folder) / 'release.json').read_text()), data)

    def test_published_prerelease_is_selected_and_checksum_checked(self):
        newer = dict(self.release, version='1.2.3', tag='v1.2.3', versionCode=123)
        release = dict(draft=False, prerelease=True, tag_name='v1.2.3', published_at='2027-01-01T00:00:00Z', assets=[
            dict(name='release.json', id=1),
            dict(name='Hub.Helper.apk', size=newer['sizeBytes'], digest='sha256:' + newer['sha256']),
        ])
        with patch('download_page.gh', side_effect=[json.dumps([release]), json.dumps(newer)]):
            self.assertEqual(current_release(), newer)
        release['assets'][1]['digest'] = 'sha256:' + '0' * 64
        with patch('download_page.gh', side_effect=[json.dumps([release]), json.dumps(newer)]):
            with self.assertRaisesRegex(AssertionError, 'checksum mismatch'):
                current_release()

    def test_changed_signing_key_is_rejected(self):
        data = dict(self.release, signerSha256='0' * 64)
        with self.assertRaisesRegex(AssertionError, 'Signing key changed'):
            validate(data, self.release['signerSha256'])

    def test_wrong_package_and_unsafe_tag_are_rejected(self):
        for patch in ({'applicationId': 'another.app'}, {'tag': '../bad'}, {'versionCode': 0}, {'sha256': 'bad'}):
            with self.subTest(patch=patch), self.assertRaises(AssertionError):
                validate(dict(self.release, **patch), self.release['signerSha256'])


if __name__ == '__main__':
    unittest.main()
