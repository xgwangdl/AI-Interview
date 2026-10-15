#!/usr/bin/env python3
"""
把「AI 面试官表情与状态展示」组合图拆成数字人要用的单帧素材。

    python scripts/split-avatar-sheet.py                # 输出素材
    python scripts/split-avatar-sheet.py --preview-only # 只出预览图，不覆盖素材

为什么需要脚本：组合图不能直接当数字人用——一张图里既有大图、12 张卡片，
又有标题、说明文字和状态图标。手裁一次容易，换素材就要重来；这里把
「定位卡片 → 去卡片底 → 去掉大图的侵入 → 按人脸对齐 → 输出」做成可重复执行的流程。

原图事实（算法选择依据，换素材前请重新确认）：
  * 1674x940，RGBA，**真透明**（透明区 (0,0,0,0)，不是棋盘格）
  * 人物是实心像素，alpha ≈ 246-255
  * 卡片底是半透明白，alpha ≈ 30-160  → 直接裁会带上卡片底色，必须重映射 alpha
  * 卡片下方的标签（浅蓝胶囊 / 图标文字）也是实心 → 靠行带排除，不能只靠 alpha
  * **左侧大图的手臂在第 2、3 行会伸进第 1 张卡片的矩形范围** → 必须用连通域把人物抠出来，
    否则第 1 张卡会连大图的手臂一起裁进去（初版就是这样，mouth-o 宽度等于整条列带）
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import deque
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parents[1]
SHEET = REPO / "web/public/assets/avatar/avatar-expression-sheet.png"
OUT_DIR = REPO / "web/public/assets/avatar"
PREVIEW = REPO / "docs/images/avatar-crop-preview.png"

# --- 版面（由探测得到；卡片等宽，行高略有差异）---
CARD_X = [731, 959, 1187, 1414, 1642]      # 5 条竖边界 = 4 张卡，宽约 228
ROW_BANDS = [(60, 307), (360, 599), (672, 906)]
PORTRAIT_BOX = (0, 40, 731, 940)           # 左侧大图

# 卡片底 → 透明；人物 → 保留（中间线性过渡，保留边缘抗锯齿）
FILL_ALPHA_MAX = 150
SOLID_ALPHA_MIN = 250

CANVAS = (360, 300)
TARGET_HEAD_HEIGHT = 130                   # 统一人脸高度 → 各帧大小一致
ANCHOR = (180, 72)                         # 缩放后「人脸顶边 x=躯干中心」落点


@dataclass
class Frame:
    name: str
    row: int
    col: int
    note: str


# 命名沿用原图标注：前两行是嘴型/头部姿态，第三行是面试流程状态。
FRAMES = [
    Frame("mouth-closed", 0, 0, "闭嘴（准备）：待机 / 聆听 / 录制 / 配音结束"),
    Frame("mouth-normal", 0, 1, "正在说话：正常张嘴"),
    Frame("mouth-smile", 0, 2, "微笑：友善过渡帧"),
    Frame("mouth-a", 0, 3, "发音 A：较大张嘴"),
    Frame("mouth-o", 1, 0, "发音 O：圆口"),
    Frame("mouth-e", 1, 1, "发音 E：横向张嘴"),
    Frame("mouth-m", 1, 2, "发音 B/M：闭唇，低音量时用"),
    Frame("head-nod", 1, 3, "点头/侧视：头部有角度差，只做低频点缀"),
    Frame("state-asking", 2, 0, "提问状态（张口说话）"),
    Frame("state-listening", 2, 1, "聆听状态（闭嘴、点头）"),
    Frame("state-recording", 2, 2, "录制回答（保持安静）"),
    Frame("state-followup", 2, 3, "追问状态（再次提问）"),
]

# 说话时循环的嘴型顺序：闭唇 → 中 → 大 → 中 → 横向，避免长时间停在张大嘴。
TALK_CYCLE = ["mouth-m", "mouth-e", "mouth-normal", "mouth-a", "mouth-normal", "mouth-e"]


def load_sheet(path: Path) -> np.ndarray:
    if not path.exists():
        sys.exit(
            f"找不到素材：{path}\n"
            f"请把组合图复制到 {path}（Vite 静态目录，前端可按 /assets/... 直接访问）"
        )
    image = Image.open(path).convert("RGBA")
    print(f"原图：{image.size[0]}x{image.size[1]}  {image.mode}")
    return np.array(image)


def strip_card_background(cell: np.ndarray) -> np.ndarray:
    """半透明卡片底 → 全透明。

    人物（alpha≈246-255）与卡片底（alpha≈30-160）是分开的两段，
    中间那段是人物边缘的抗锯齿与柔和外发光。线性重映射比二值化干净，也不会残留底色。
    """
    alpha = cell[:, :, 3].astype(np.float32)
    scaled = np.clip((alpha - FILL_ALPHA_MAX) / (SOLID_ALPHA_MIN - FILL_ALPHA_MAX), 0.0, 1.0)
    out = cell.copy()
    out[:, :, 3] = (scaled * 255).astype(np.uint8)
    return out


def skin_mask(cell: np.ndarray) -> np.ndarray:
    r, g, b = cell[:, :, 0].astype(int), cell[:, :, 1].astype(int), cell[:, :, 2].astype(int)
    return (cell[:, :, 3] > 200) & (r > 170) & (g > 120) & (b > 100) & ((r - b) > 20) & ((r - g) > 10)


def keep_face_component(solid: np.ndarray, seed: tuple[int, int]) -> np.ndarray:
    """只保留包含人脸的那一团连续像素，用来甩掉左侧大图伸进来的手臂。

    没有用 scipy：这里的区域只有 228x247，四邻域 BFS 足够快，也少一个依赖。
    """
    h, w = solid.shape
    out = np.zeros_like(solid)
    sy, sx = seed
    if not solid[sy, sx]:
        # 种子落在抗锯齿缝隙里时，就近找一个实心点
        for radius in range(1, 12):
            found = False
            for dy in range(-radius, radius + 1):
                for dx in range(-radius, radius + 1):
                    y, x = sy + dy, sx + dx
                    if 0 <= y < h and 0 <= x < w and solid[y, x]:
                        sy, sx, found = y, x, True
                        break
                if found:
                    break
            if found:
                break
        else:
            raise ValueError("在人脸附近找不到实心像素")

    queue = deque([(sy, sx)])
    out[sy, sx] = True
    while queue:
        y, x = queue.popleft()
        for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
            if 0 <= ny < h and 0 <= nx < w and solid[ny, nx] and not out[ny, nx]:
                out[ny, nx] = True
                queue.append((ny, nx))
    return out


def extract(cell: np.ndarray, label: str, log: list[str]) -> Image.Image:
    clean = strip_card_background(cell)
    solid = clean[:, :, 3] > 150
    skin = skin_mask(clean)
    if skin.sum() < 50:
        raise ValueError(f"{label}: 找不到人脸（肤色像素太少）")

    ys, xs = np.nonzero(skin)
    head_limit = clean.shape[0] * 0.62      # 只取上半部分，避免把手也算进头
    keep = ys < head_limit
    ys, xs = ys[keep], xs[keep]
    face_top, face_bottom = int(ys.min()), int(ys.max())
    face_height = face_bottom - face_top + 1
    seed = (int(np.median(ys)), int(np.median(xs)))

    person = keep_face_component(solid, seed)
    pys, pxs = np.nonzero(person)
    bx0, bx1, by0, by1 = int(pxs.min()), int(pxs.max()), int(pys.min()), int(pys.max())

    # 只保留人物像素，其余置空
    masked = np.zeros_like(clean)
    masked[person] = clean[person]
    trimmed = masked[by0:by1 + 1, bx0:bx1 + 1]

    # 躯干中心作为水平锚点：比人脸中心稳（头部转动时脸心会漂，躯干不会）
    local_skin = skin_mask(trimmed)
    sys_, sxs_ = np.nonzero(local_skin)
    if len(sys_) < 50:
        raise ValueError(f"{label}: 裁剪后找不到人脸")
    face_bottom_local = int(sys_.max())
    torso_start = face_bottom_local + int((trimmed.shape[0] - face_bottom_local) * 0.25)
    torso = np.nonzero(trimmed[torso_start:, :, 3] > 150)
    torso_cx = float(np.median(torso[1])) if len(torso[1]) else trimmed.shape[1] / 2.0
    face_top_local = int(sys_.min())

    scale = TARGET_HEAD_HEIGHT / face_height
    resized = Image.fromarray(trimmed).resize(
        (max(1, round(trimmed.shape[1] * scale)), max(1, round(trimmed.shape[0] * scale))),
        Image.LANCZOS,
    )

    canvas = Image.new("RGBA", CANVAS, (0, 0, 0, 0))
    canvas.alpha_composite(resized, (
        round(ANCHOR[0] - torso_cx * scale),
        round(ANCHOR[1] - face_top_local * scale),
    ))

    log.append(
        f"{label:<18} 卡片内人物 {trimmed.shape[1]:>3}x{trimmed.shape[0]:<3} "
        f"脸高 {face_height:>3}  缩放 {scale:4.2f}  躯干中心 {torso_cx:6.1f}"
    )
    return canvas


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-only", action="store_true")
    args = parser.parse_args()

    arr = load_sheet(SHEET)
    log: list[str] = []
    outputs: dict[str, Image.Image] = {}

    for frame in FRAMES:
        y0, y1 = ROW_BANDS[frame.row]
        x0, x1 = CARD_X[frame.col], CARD_X[frame.col + 1]
        try:
            outputs[frame.name] = extract(arr[y0:y1, x0:x1], frame.name, log)
        except ValueError as exc:
            print(f"× {exc}")
            return 1

    px0, py0, px1, py1 = PORTRAIT_BOX
    try:
        outputs["portrait"] = extract(arr[py0:py1, px0:px1], "portrait", log)
    except ValueError as exc:
        print(f"! 左侧大图跳过：{exc}")

    print("\n逐帧对齐结果：")
    for line in log:
        print("  " + line)

    # 预览图放在棋盘格上：真透明会露出棋盘；残留卡片底会显示成一块灰，一眼能看出来。
    cols = 4
    rows = (len(outputs) + cols - 1) // cols
    cw, ch = CANVAS
    preview = Image.new("RGB", (cw * cols, (ch + 26) * rows), (245, 246, 244))
    draw = ImageDraw.Draw(preview)
    for index, (name, image) in enumerate(outputs.items()):
        ox, oy = (index % cols) * cw, (index // cols) * (ch + 26)
        for gy in range(0, ch, 20):
            for gx in range(0, cw, 20):
                shade = (231, 233, 230) if (gx // 20 + gy // 20) % 2 else (247, 248, 246)
                draw.rectangle([ox + gx, oy + gy, ox + gx + 20, oy + gy + 20], fill=shade)
        preview.paste(image, (ox, oy), image)
        draw.text((ox + 8, oy + ch + 6), f"{index + 1}. {name}", fill=(58, 68, 62))
        draw.rectangle([ox, oy, ox + cw - 1, oy + ch - 1], outline=(208, 212, 208))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    preview.save(PREVIEW)
    print(f"\n预览图：{PREVIEW}")

    if args.preview_only:
        print("（--preview-only：未写素材）")
        return 0

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, image in outputs.items():
        image.save(OUT_DIR / f"{name}.png")
    (OUT_DIR / "manifest.json").write_text(json.dumps({
        "canvas": list(CANVAS),
        "headAnchor": list(ANCHOR),
        "talkCycle": TALK_CYCLE,
        "frames": {f.name: f.note for f in FRAMES},
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已写出 {len(outputs)} 个素材 + manifest.json 到 {OUT_DIR}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
