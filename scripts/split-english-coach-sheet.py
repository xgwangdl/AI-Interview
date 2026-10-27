#!/usr/bin/env python3
"""Split the supplied English-coach reference sheet into aligned avatar frames.

The source sheet is a 6-column contact sheet with blue labels under every
portrait. The source rows use slightly different camera scales, so a fixed crop
still makes faces jump when mouths switch. This script detects each face,
normalizes face width and head-top position, and keeps the full cell height
above the blue label so the neck and shoulders remain visible.

Usage:
    python scripts/split-english-coach-sheet.py
    python scripts/split-english-coach-sheet.py --preview-only
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np
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
CELL_BOTTOMS = [262, 521, 755, 978]

# Face width and head-top are the stable anchors between frames. The taller
# canvas leaves room for the neck and upper shoulders instead of a floating
# head crop.
CANVAS = (340, 300)
TARGET_FACE_WIDTH = 150
HEAD_TOP = 10


FRAMES = {
    # name: (row, column)
    "mouth-closed": (0, 0),
    # Every speaking frame comes from the same source row. This keeps the
    # lighting, background, shoulder width and camera angle identical while
    # only the mouth changes.
    "mouth-smile": (2, 5),
    "mouth-normal": (2, 2),
    "mouth-e": (2, 1),
    "mouth-a": (2, 0),
    "mouth-o": (2, 3),
    "mouth-m": (2, 4),
    "eyes-closed": (1, 4),
    "head-nod": (1, 0),
    "portrait": (0, 0),
    "state-asking": (2, 2),
    "state-listening": (0, 0),
    "state-recording": (0, 0),
    "state-followup": (2, 5),
}


def detect_face(source_bgr: np.ndarray, row: int, column: int, name: str) -> tuple[int, int, int, int]:
    x0, x1 = COLUMNS[column]
    y0, y1 = ROW_TOPS[row], CELL_BOTTOMS[row]
    roi = cv2.equalizeHist(cv2.cvtColor(source_bgr[y0:y1, x0:x1], cv2.COLOR_BGR2GRAY))
    cascade = cv2.CascadeClassifier(
        cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
    )
    faces = cascade.detectMultiScale(
        roi,
        scaleFactor=1.05,
        minNeighbors=5,
        minSize=(45, 45),
        maxSize=(150, 150),
    )
    if len(faces) == 0:
        raise ValueError(f"{name}: no face detected in row {row + 1}, column {column + 1}")

    # Prefer the largest detection if a background poster happens to trigger.
    x, y, w, h = max(faces, key=lambda box: int(box[2]) * int(box[3]))
    return x0 + int(x), y0 + int(y), int(w), int(h)


def fade_bottom(image: Image.Image, fade_px: int = 18) -> Image.Image:
    """Soften the source-cell cutoff without hiding the neck."""
    alpha = image.getchannel("A")
    mask = Image.new("L", image.size, 255)
    fade = Image.new("L", (image.width, fade_px), 255)
    pixels = fade.load()
    for y in range(fade_px):
        value = round(255 * (1 - y / max(1, fade_px - 1)))
        for x in range(fade.width):
            pixels[x, y] = value
    mask.paste(fade, (0, image.height - fade_px))
    image.putalpha(Image.composite(alpha, Image.new("L", image.size, 0), mask))
    return image


def crop_frame(source: Image.Image, source_bgr: np.ndarray, row: int, column: int,
               name: str) -> Image.Image:
    x0, x1 = COLUMNS[column]
    y0, y1 = ROW_TOPS[row], CELL_BOTTOMS[row]
    face_x, face_y, face_w, face_h = detect_face(source_bgr, row, column, name)
    portrait = source.crop((x0, y0, x1, y1)).convert("RGBA")

    # Rows differ in source scale. Normalizing by face width removes the
    # visible size pop when switching from a row-1 frame to a row-3 frame.
    scale = TARGET_FACE_WIDTH / face_w
    fitted = portrait.resize(
        (round(portrait.width * scale), round(portrait.height * scale)),
        Image.Resampling.LANCZOS,
    )

    face_center_x = face_x + face_w / 2
    # Haar's face box starts near the forehead; the hairline is above it.
    estimated_head_top = face_y - face_h * 0.35
    x = round(CANVAS[0] / 2 - (face_center_x - x0) * scale)
    y = round(HEAD_TOP - (estimated_head_top - y0) * scale)

    canvas = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    layer = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    layer.paste(fitted, (x, y))
    canvas.alpha_composite(fade_bottom(layer))
    return canvas


def write_preview(frames: dict[str, Image.Image]) -> None:
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    thumb_size = (170, 150)
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
    source_bgr = cv2.imread(str(SOURCE))
    if source_bgr is None:
        raise SystemExit(f"OpenCV could not read source sheet: {SOURCE}")
    frames = {
        name: crop_frame(source, source_bgr, row, column, name)
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
        "targetFaceWidth": TARGET_FACE_WIDTH,
        "headTop": HEAD_TOP,
        "note": "Face-width normalized, head-top aligned, neck and shoulders retained; blue labels excluded.",
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
