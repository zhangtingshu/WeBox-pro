#!/usr/bin/env python3
"""Sync the PRD's product_images/ into frontend/public/images/ under stable names.

The source bundle names files like ``image_01_s-1770018186746.jpg`` (a vendor suffix),
while V2__seed.sql and the API contract reference ``/images/image_01.jpg``. This script
strips the suffix so every seeded dish resolves to a real static file.

Usage:
    python3 scripts/sync_images.py                       # auto-detect ../product_images
    python3 scripts/sync_images.py --source /path/to/product_images
    python3 scripts/sync_images.py --check              # verify canonical files only

Safe to re-run. If the source folder is absent (e.g. after cloning this repository),
the script runs in --check mode and verifies the bundled images are complete.
"""

import argparse
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / "frontend" / "public" / "images"
DEFAULT_SOURCE = ROOT.parent / "product_images"
NAME_RE = re.compile(r"^image_(\d{2})_.*\.(jpg|jpeg|png|webp)$", re.IGNORECASE)
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp", ".svg"}


def canonical(number: str, extension: str) -> str:
    return f"image_{number}.{extension.lower()}"


def sync(source: Path) -> int:
    if not source.is_dir():
        print(f"Source folder not found: {source}")
        print("Falling back to --check (images already bundled in this repository).")
        return check()

    TARGET.mkdir(parents=True, exist_ok=True)
    copied = []
    for path in sorted(source.iterdir()):
        if not path.is_file():
            continue
        match = NAME_RE.match(path.name)
        if not match:
            continue
        destination = TARGET / canonical(match.group(1), match.group(2))
        shutil.copy2(path, destination)
        copied.append((path.name, destination.name))

    if not copied:
        print(f"No image_XX_* files found in {source}", file=sys.stderr)
        return 1

    # Remove stale vendor-suffixed copies left over from earlier builds.
    removed = []
    for path in TARGET.iterdir():
        if not path.is_file() or path.suffix.lower() not in IMAGE_EXTENSIONS:
            continue
        if NAME_RE.match(path.name) or (path.name.startswith("image_") and "_s-" in path.name):
            path.unlink()
            removed.append(path.name)

    for original, new_name in copied:
        print(f"  {original}  ->  {new_name}")
    print(f"Synced {len(copied)} images into {TARGET}")
    if removed:
        print(f"Removed {len(removed)} stale vendor-named file(s).")
    return check()


def check() -> int:
    missing = []
    present = []
    for number in range(1, 21):
        found = [p for p in TARGET.glob(f"image_{number:02d}.*")
                 if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}]
        if found:
            present.append(found[0].name)
        else:
            missing.append(f"image_{number:02d}.jpg")

    if not (TARGET / "placeholder.svg").exists():
        missing.append("placeholder.svg")

    if missing:
        print("MISSING: " + ", ".join(missing), file=sys.stderr)
        return 1
    print(f"OK: {len(present)} canonical dish images + placeholder.svg are present in {TARGET}")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE,
                        help="folder containing the raw PRD product images")
    parser.add_argument("--check", action="store_true",
                        help="only verify the bundled canonical images")
    args = parser.parse_args()
    return check() if args.check else sync(args.source)


if __name__ == "__main__":
    sys.exit(main())
