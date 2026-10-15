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
# 第 3 行的标签是一条**整卡宽度的不透明白条**，从 y≈828 一直铺到卡片底边，
# 人物的身体是画在它**上面**的（中间露西装、两侧露白底）。所以这一行只能截在标签之前（827），
# 再多裁就会把白条带进画面 —— 界面上表现为人物下方多一条「聆听状态」白条。
# 不能靠"把白色抠掉"绕过：人物胸前是浅色衬衫（≈(247,247,249)），抠白会在胸口打洞。
# 代价是这 4 张状态帧只到胸口；人脸仍按同一锚点对齐，切换时不会跳动。
ROW_BANDS = [(60, 307), (360, 599), (672, 827)]
PORTRAIT_BOX = (0, 40, 731, 940)           # 左侧大图

# 卡片底 → 透明；人物 → 保留（中间线性过渡，保留边缘抗锯齿）
FILL_ALPHA_MAX = 150
SOLID_ALPHA_MIN = 250

# 画布高度与「状态帧被说明标签截断的位置」对齐：状态帧的身体只能到 y≈242（再往下就是标签），
# 嘴型帧本来能到 300。若沿用 300，说完话切到聆听时会看到身体缩回去 55px，
# 比白条更显眼。统一裁到 248，12 帧都成胸像，切换时身体长度不再跳。
CANVAS = (360, 248)
TARGET_HEAD_HEIGHT = 130
# 只在裁剪最上面这么多行里找脸（见 head_limit 的说明）；左侧半身像尺度不同，传 None 走比例
HEAD_WINDOW = 145
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

# 闭眼帧是**合成**出来的（组合图里没有闭眼素材）：把镜片内的眼睛区域换成肤色并画一条睫毛线，
# 镜框在更外圈、不动。坐标是原图绝对坐标，由下列探测得到：
#   左眼 x809-833 / 右眼 x856-880，y148-167（"非肤色列簇"定位，关于 x=844 对称）
# 合成结果需要人眼确认，脚本会额外输出一张放大对比图 docs/images/avatar-blink-check.png。
EYE_BOXES = [(809, 833, 148, 167), (856, 880, 148, 167)]


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


def looks_like_label(frame: np.ndarray, band: int = 14) -> bool:
    """裁剪结果底部是否混进了卡片标签。

    判断依据：标签是"整片不透明白底"，人物底部不会出现这种东西。
    """
    if frame.shape[0] < band:
        return False
    bottom = frame[-band:, :, :].astype(int)
    near_white = (
        (bottom[:, :, 3] > 200)
        & (bottom[:, :, 0] > 246)
        & (bottom[:, :, 1] > 246)
        & (bottom[:, :, 2] > 246)
    )
    return bool(near_white.sum(axis=1).max() > frame.shape[1] * 0.5)


def head_limit(height: int, window: int | None) -> int:
    """在裁剪结果里搜索人脸时，只看最上面多少行。

    两个坑都踩过，所以必须用**固定像素窗口**而不是"高度的比例"：
      * 窗口太小：第 3 行为了避开说明标签把裁剪改短后，0.62×149=92 会把脸截断，
        测出的脸高从 96 掉到 66、缩放翻倍，人物在界面上突然变大；
      * 窗口太大：含进脖子，脸高被测成 113-123，而状态帧的裁剪根本到不了脖子（只有 96），
        两者不一致 → 说话时人物缩小 25%。
    卡片里的人脸都在离卡片顶 30-136px 的范围内，取 145px 刚好覆盖整张脸而不含脖子。
    左侧那张半身像尺度完全不同，用 window=None 走比例。
    """
    if window is None:
        return int(height * 0.62)
    return min(height, window)


def close_eyes(sheet: np.ndarray, boxes: list[tuple[int, int, int, int]]) -> np.ndarray:
    """合成闭眼：镜片内的眼睛区域填成脸颊肤色，中间画一条睫毛线。

    为什么这么做而不是"整体压扁"：人物戴着眼镜，压扁会把镜框一起压变形。
    只处理镜片内圈、镜框留在原处，看起来才像"眼皮合上"。
    填充色取眼睛正下方的脸颊肤色，避免和周围有色差。
    """
    out = sheet.copy()
    for x0, x1, y0, y1 in boxes:
        # 取镜片下方几行的肤色当填充色（那里一定是纯皮肤）
        patch = out[y1 + 4:y1 + 13, x0:x1 + 1]
        mask = (patch[:, :, 3] > 200) & (patch[:, :, 0] > 170)
        if mask.sum() >= 10:
            color = np.median(patch[mask][:, :3], axis=0)
        else:  # 找不到就退回一个中性肤色
            color = np.array([232.0, 196.0, 172.0])

        inset = 2                      # 留出镜框，别把深色框线涂掉
        iy0, iy1, ix0, ix1 = y0 + inset, y1 - inset, x0 + inset, x1 - inset
        out[iy0:iy1 + 1, ix0:ix1 + 1, 0] = color[0]
        out[iy0:iy1 + 1, ix0:ix1 + 1, 1] = color[1]
        out[iy0:iy1 + 1, ix0:ix1 + 1, 2] = color[2]

        lash = color * 0.42            # 睫毛线：闭眼最关键的视觉特征
        cy = (iy0 + iy1) // 2
        out[cy:cy + 2, ix0:ix1 + 1, 0] = lash[0]
        out[cy:cy + 2, ix0:ix1 + 1, 1] = lash[1]
        out[cy:cy + 2, ix0:ix1 + 1, 2] = lash[2]
    return out


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


def extract(cell: np.ndarray, label: str, log: list[str], head_window: int | None = HEAD_WINDOW,
            shared_scale: float | None = None, scale_sink: list | None = None) -> Image.Image:
    clean = strip_card_background(cell)
    solid = clean[:, :, 3] > 150
    skin = skin_mask(clean)
    if skin.sum() < 50:
        raise ValueError(f"{label}: 找不到人脸（肤色像素太少）")

    ys, xs = np.nonzero(skin)
    keep = ys < head_limit(clean.shape[0], head_window)
    if keep.sum() < 50:
        raise ValueError(f"{label}: 找不到人脸（肤色像素太少）")
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

    # 换素材时行带很容易取多，把卡片下方的说明标签一起裁进来。
    # 标签的特征是"大面积不透明白底"，人物底下不会有，所以在这里直接拦下来，
    # 免得装到界面上才发现人物下面多出一条白条（第 3 行踩过这个坑）。
    if looks_like_label(trimmed):
        raise ValueError(
            f"{label}: 裁剪底部疑似包含卡片标签（大片不透明白底），请把对应行的下限往上调"
        )

    # 躯干中心作为水平锚点：比人脸中心稳（头部转动时脸心会漂，躯干不会）
    local_skin = skin_mask(trimmed)
    s_ys, _ = np.nonzero(local_skin)
    keep_local = s_ys < head_limit(trimmed.shape[0], head_window)
    if keep_local.sum() < 50:
        raise ValueError(f"{label}: 裁剪后找不到人脸")
    s_ys = s_ys[keep_local]
    face_top_local, face_bottom_local = int(s_ys.min()), int(s_ys.max())
    torso_start = face_bottom_local + int((trimmed.shape[0] - face_bottom_local) * 0.25)
    torso = np.nonzero(trimmed[torso_start:, :, 3] > 150)
    torso_cx = float(np.median(torso[1])) if len(torso[1]) else trimmed.shape[1] / 2.0

    # 统一缩放：同一个人物在所有帧里必须一样大，否则说话时会在两张图之间"呼吸"。
    # 逐帧测脸高会有 ±10% 噪声（张嘴会改变肤色区域），所以默认用外部传进来的统一值。
    measured_scale = TARGET_HEAD_HEIGHT / face_height
    if scale_sink is not None:
        scale_sink.append(measured_scale)
    scale = shared_scale if shared_scale else measured_scale
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
        f"实测脸高 {face_height:>3}  使用缩放 {scale:4.2f}  躯干中心 {torso_cx:6.1f}"
    )
    return canvas


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--preview-only", action="store_true")
    args = parser.parse_args()

    arr = load_sheet(SHEET)
    log: list[str] = []
    outputs: dict[str, Image.Image] = {}

    # 第一遍：只为了量出"人物应该多大"。逐帧测脸高有 ±10% 噪声（张嘴会改变肤色区域），
    # 直接用会让说话时人物在两张图之间忽大忽小，所以取所有帧的中位数当统一缩放。
    measured_scales: list[float] = []
    for frame in FRAMES:
        y0, y1 = ROW_BANDS[frame.row]
        x0, x1 = CARD_X[frame.col], CARD_X[frame.col + 1]
        try:
            extract(arr[y0:y1, x0:x1], frame.name, [], scale_sink=measured_scales)
        except ValueError as exc:
            print(f"× {exc}")
            return 1
    measured_scales.sort()
    shared_scale = measured_scales[len(measured_scales) // 2]
    print(f"统一缩放（12 帧中位数）：{shared_scale:.3f}"
          f"（逐帧实测 {min(measured_scales):.3f}..{max(measured_scales):.3f}）\n")

    # 第二遍：用统一缩放正式输出
    for frame in FRAMES:
        y0, y1 = ROW_BANDS[frame.row]
        x0, x1 = CARD_X[frame.col], CARD_X[frame.col + 1]
        try:
            outputs[frame.name] = extract(arr[y0:y1, x0:x1], frame.name, log,
                                          shared_scale=shared_scale)
        except ValueError as exc:
            print(f"× {exc}")
            return 1

    px0, py0, px1, py1 = PORTRAIT_BOX
    try:
        outputs["portrait"] = extract(arr[py0:py1, px0:px1], "portrait", log, head_window=None)
    except ValueError as exc:
        print(f"! 左侧大图跳过：{exc}")

    # 闭眼帧：在整图上合成后再走同一条裁剪/对齐流程，保证与 mouth-closed 完全同框。
    closed_sheet = close_eyes(arr, EYE_BOXES)
    try:
        outputs["eyes-closed"] = extract(
            closed_sheet[ROW_BANDS[0][0]:ROW_BANDS[0][1], CARD_X[0]:CARD_X[1]],
            "eyes-closed", log, shared_scale=shared_scale)
    except ValueError as exc:
        print(f"! 闭眼帧跳过：{exc}")

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

    # 闭眼是合成出来的，单独给一张放大对比图，方便一眼判断像不像（否则得在 360px 画布上找眼睛）
    if "eyes-closed" in outputs:
        x0, x1, y0, y1 = EYE_BOXES[0][0], EYE_BOXES[1][1], EYE_BOXES[0][2], EYE_BOXES[0][3]
        pad = 12
        box = (x0 - pad, y0 - pad, x1 + pad, y1 + pad)
        before = Image.fromarray(arr).crop(box).resize(
            ((box[2] - box[0]) * 5, (box[3] - box[1]) * 5), Image.NEAREST)
        after = Image.fromarray(close_eyes(arr, EYE_BOXES)).crop(box).resize(
            ((box[2] - box[0]) * 5, (box[3] - box[1]) * 5), Image.NEAREST)
        sheet_img = Image.new("RGB", (before.width, before.height * 2 + 30), (245, 246, 244))
        flat = Image.new("RGB", before.size, (245, 246, 244))
        flat.paste(before, (0, 0), before)
        sheet_img.paste(flat, (0, 0))
        flat2 = Image.new("RGB", after.size, (245, 246, 244))
        flat2.paste(after, (0, 0), after)
        sheet_img.paste(flat2, (0, before.height + 30))
        draw2 = ImageDraw.Draw(sheet_img)
        draw2.text((6, before.height + 7), "上：原图（睁眼）    下：合成后（闭眼）  ×5 放大", fill=(50, 60, 54))
        check = REPO / "docs/images/avatar-blink-check.png"
        sheet_img.save(check)
        print(f"闭眼对比图：{check}")

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
