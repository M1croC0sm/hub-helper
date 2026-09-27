#!/usr/bin/env python3
"""Create build metadata from Gradle output; never updates the live download page."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
artifacts = []
for variant in ("debug", "release"):
    folder = root / "app/build/outputs/apk" / variant
    metadata = folder / "output-metadata.json"
    if not metadata.exists():
        continue
    for item in json.loads(metadata.read_text())["elements"]:
        apk = folder / item["outputFile"]
        if apk.is_file():
            artifacts.append({"variant": variant, "version": item["versionName"],
                "versionCode": item["versionCode"], "file": str(apk.relative_to(root)),
                "sha256": hashlib.file_digest(apk.open("rb"), "sha256").hexdigest()})
assert artifacts, "Build an APK first"
out = root / "build/release-manifest.json"
out.parent.mkdir(exist_ok=True)
out.write_text(json.dumps({"artifacts": artifacts}, indent=2) + "\n")
print(out)
