#!/usr/bin/env python3
"""Generate the Cresto Dev update manifest.

The JSON shape matches the app's UpdateManifest (see
app/src/main/java/com/nevoit/cresto/feature/settings/update/UpdateManifest.kt).
Used by .github/workflows/dev-release.yml and by manual releases (see DEV.md).
"""
import argparse
import json
import pathlib
import sys

DEFAULT_CHANNEL = "dev"


def build_manifest(repo: str, version_code: int, version_name: str, notes: str) -> dict:
    items = [line.strip() for line in notes.splitlines() if line.strip()] or [
        "见该版本的 GitHub Release 说明"
    ]
    return {
        "schemaVersion": 1,
        "platform": "android",
        "channel": DEFAULT_CHANNEL,
        "versionCode": version_code,
        "versionName": version_name,
        "forceUpdate": False,
        "downloadUrl": (
            f"https://github.com/{repo}/releases/download/"
            f"dev-v{version_code}/cresto-dev-{version_code}.apk"
        ),
        "description": {
            "zh-CN": "Cresto Dev 个人开发版（与官方 Cresto 独立安装、独立数据）",
            "en": "Personal development build of Cresto (installs beside the official app)",
        },
        "releaseNotes": {
            "zh-CN": [{"type": "feature", "title": f"Cresto Dev {version_code}", "items": items}],
            "en": [{"type": "feature", "title": f"Cresto Dev {version_code}", "items": items}],
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", required=True, help="owner/name, e.g. mkinnqijn/Cresto")
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--version-name", required=True, help="must match the built APK, e.g. 1.0-dev")
    parser.add_argument("--notes", default="", help="release notes (one item per line)")
    parser.add_argument("--output", default=".dev-update/latest.json")
    args = parser.parse_args()

    manifest = build_manifest(args.repo, args.version_code, args.version_name, args.notes)
    path = pathlib.Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path}")
    print(path.read_text(encoding="utf-8"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
