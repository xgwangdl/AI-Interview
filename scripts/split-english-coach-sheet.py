#!/usr/bin/env python3
"""Split the supplied English-coach reference sheet into aligned avatar frames.

The source sheet is a 6-column contact sheet with blue labels under every
portrait. The labels are intentionally excluded from every output frame. All
frames use the same source rectangle height and scale, so changing mouths does
not resize or vertically jump the character.

Usage:
    python scripts/split-english-coach-sheet.py
    python scripts/split-english-coach-sheet.py --preview-only
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


REPO = Path(__file__).resolve().parents[1]
ASSET_DIR = REPO / "web/public/assets/avatar/english-coach"
SOURCE = REPO / "scripts/assets/english-coach-expression-sheet.png"
PREVIEW = REPO / "docs/images/english-coach-crop-preview.png"

# Detected from the supplied 1312x1199 sheet. Each tuple excludes the white
# gutters and the blue label band below the portrait.
COLUMNS = [
    (8, 220),
    (227, 435),
    (443, 655),
    (663, 877),
    (886, 1093),
    (1102, 1304),
]
ROW_TOPS = [45, 297, 560, 802]

# A fixed crop height is the key alignment rule. Rows 3 and 4 have less image
# space before their labels, but the face and shoulders still fit in 176px.
CROP_HEIGHT = 176
CANVAS = (340, 260)


FRAMES = {
    # name: (row, column)
    "mouth-closed": (0, 0),
    "mouth-smile": (0, 1),
    "mouth-normal": (0, 2),
    "mouth-e": (2, 1),
    "mouth-a": (2, 0),
    "mouth-o": (2, 3),
    "mouth-m": (2, 4),
    "eyes-closed": (1, 4),
    "head-nod": (1, 0),
    "portrait": (0, 0),
    "state-asking": (0, 2),
    "state-listening": (0, 0),
    "state-recording": (0, 0),
    "state-followup": (0, 1),
}


def crop_frame(source: Image.Image, row: int, column: int) -> Image.Image:
    x0, x1 = COLUMNS[column]
    y0 = ROW_TOPS[row]
    portrait = source.crop((x0, y0, x1, y0 + CROP_HEIGHT)).convert("RGBA")

    scale = CANVAS[1] / portrait.height
    fitted = portrait.resize(
        (round(portrait.width * scale), CANVAS[1]),
        Image.Resampling.LANCZOS,
    )
    canvas = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    canvas.alpha_composite(fitted, ((CANVAS[0] - fitted.width) // 2, 0))
    return canvas


def write_preview(frames: dict[str, Image.Image]) -> None:
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    thumb_size = (170, 130)
    label_height = 24
    columns = 4
    rows = (len(frames) + columns - 1) // columns
    sheet = Image.new(
        "RGB",
        (columns * thumb_size[0], rows * (thumb_size[1] + label_height)),
        "#eef3f8",
    )
    draw = ImageDraw.Draw(sheet)
    font = ImageFont.load_default()

    for index, (name, frame) in enumerate(frames.items()):
        x = (index % columns) * thumb_size[0]
        y = (index // columns) * (thumb_size[1] + label_height)
        thumb = frame.copy()
        thumb.thumbnail(thumb_size, Image.Resampling.LANCZOS)
        sheet.paste(thumb, (x, y), thumb)
        draw.rectangle(
            (x, y + thumb_size[1], x + thumb_size[0], y + thumb_size[1] + label_height),
            fill="#315f9f",
        )
        draw.text((x + 6, y + thumb_size[1] + 6), name, fill="white", font=font)

    sheet.save(PREVIEW)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-only", action="store_true")
    args = parser.parse_args()

    if not SOURCE.exists():
        raise SystemExit(f"Missing source sheet: {SOURCE}")

    source = Image.open(SOURCE).convert("RGB")
    frames = {
        name: crop_frame(source, row, column)
        for name, (row, column) in FRAMES.items()
    }
    write_preview(frames)
    print(f"Preview: {PREVIEW.relative_to(REPO)}")

    if args.preview_only:
        return

    for name, frame in frames.items():
        frame.save(ASSET_DIR / f"{name}.png", optimize=True)

    manifest = {
        "source": str(SOURCE.relative_to(REPO)).replace("\\", "/"),
        "canvas": list(CANVAS),
        "cropHeight": CROP_HEIGHT,
        "note": "Same scale and anchor for every frame; blue labels are excluded.",
        "frames": {
            name: {"row": row + 1, "column": column + 1}
            for name, (row, column) in FRAMES.items()
        },
    }
    (ASSET_DIR / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Wrote {len(frames)} frames to {ASSET_DIR.relative_to(REPO)}")


if __name__ == "__main__":
    main()
