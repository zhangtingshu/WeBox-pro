#!/usr/bin/env python3
"""Regression check for the dish-image pipeline.

Verifies that every image_url referenced by V2__seed.sql resolves to a real image
through the running nginx container, that the placeholder exists, and that a missing
image returns 404 instead of the SPA's index.html (HTTP 200 text/html).

Usage:
    python3 scripts/verify_images.py                       # checks http://localhost:8081
    python3 scripts/verify_images.py --base http://localhost:8081
"""

import argparse
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "backend" / "src" / "main" / "resources" / "db" / "V2__seed.sql"


def probe(base: str, path: str):
    try:
        with urllib.request.urlopen(base + path, timeout=8) as response:
            return response.status, response.headers.get_content_type(), len(response.read())
    except urllib.error.HTTPError as error:
        return error.code, error.headers.get_content_type(), 0
    except Exception:
        return 0, "", 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://localhost:8081",
                        help="base URL of the nginx/SPA container")
    args = parser.parse_args()
    base = args.base.rstrip("/")

    paths = sorted(set(re.findall(r"(/images/[A-Za-z0-9_.-]+)",
                                  SEED.read_text(encoding="utf-8"))))
    failures = []

    print(f"== WeBox dish image check against {base} ==")
    for path in paths:
        status, content_type, size = probe(base, path)
        ok = status == 200 and content_type.startswith("image/")
        print(f"{'PASS' if ok else 'FAIL'}  {path} · HTTP {status} {content_type} {size} bytes")
        if not ok:
            failures.append(path)

    status, content_type, _ = probe(base, "/images/placeholder.svg")
    ok = status == 200 and "svg" in content_type
    print(f"{'PASS' if ok else 'FAIL'}  fallback placeholder.svg · HTTP {status} {content_type}")
    if not ok:
        failures.append("placeholder.svg")

    status, content_type, _ = probe(base, "/images/definitely-missing.jpg")
    ok = status == 404
    print(f"{'PASS' if ok else 'FAIL'}  missing image returns 404 (no SPA fallback) · HTTP {status}")
    if not ok:
        failures.append("missing-image-404")

    print(f"== Result: {len(paths) + 2 - len(failures)} passed, {len(failures)} failed ==")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
