/**
 * 数字人素材清单。
 *
 * 图片由 scripts/split-avatar-sheet.py 从组合图切出来，放在 web/public/assets/avatar/。
 * 前端不直接引用那张组合图——它里面还有标题、说明和状态图标。
 */

const BASE = '/assets/avatar';

/**
 * 待机循环视频（可选）。
 *
 * 放一段人物坐着呼吸/眨眼的循环短片到 public/assets/avatar/idle-loop.mp4 就会自动启用：
 * 不在说话时播视频（看起来是活的），说话时切回切帧动画（因为嘴型要跟着动态配音走）。
 * **没有这个文件也完全正常** —— 组件会静默退回静态帧，不影响任何功能。
 */
export const IDLE_VIDEO = `${BASE}/idle-loop.mp4`;

/** 五档嘴型，按"张嘴程度"从闭到最大排列。 */
export const MOUTHS = {
  closed: 'mouth-closed',
  narrow: 'mouth-m',
  small: 'mouth-e',
  normal: 'mouth-normal',
  round: 'mouth-o',
  wide: 'mouth-a',
};

/** 说话时在"当前音量档位"附近轮换的帧，避免只用两张图来回闪。 */
export const TALK_ROTATION = ['mouth-normal', 'mouth-e', 'mouth-o', 'mouth-e', 'mouth-normal', 'mouth-smile'];

export const STATE_FRAMES = {
  IDLE: 'mouth-closed',
  PREPARING: 'mouth-closed',
  SPEAKING: 'mouth-normal',
  LISTENING: 'state-listening',
  RECORDING: 'state-recording',
  FOLLOW_UP: 'state-followup',
  ERROR: 'mouth-closed',
};

export const ALL_FRAMES = [
  'mouth-closed', 'mouth-m', 'mouth-e', 'mouth-normal', 'mouth-o', 'mouth-a', 'mouth-smile',
  'eyes-closed', 'head-nod', 'state-asking', 'state-listening', 'state-recording', 'state-followup',
  'portrait',
];

export function frameSrc(name) {
  return `${BASE}/${name}.png`;
}

/**
 * 预加载全部帧。
 *
 * 说话时每秒要切好几次图，现取会闪白，所以先全部解码进内存。
 * 返回 Promise，等全部就绪再开始动画；失败的帧不会阻断其它帧。
 */
export function preloadFrames(names = ALL_FRAMES) {
  if (typeof Image === 'undefined') return Promise.resolve([]);
  return Promise.all(names.map((name) => new Promise((resolve) => {
    const image = new Image();
    image.onload = () => resolve({ name, ok: true });
    image.onerror = () => resolve({ name, ok: false });
    image.src = frameSrc(name);
  })));
}
