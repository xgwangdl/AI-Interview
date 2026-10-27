/**
 * 数字人素材清单。
 *
 * 图片由 scripts/split-avatar-sheet.py 从组合图切出来，放在 web/public/assets/avatar/。
 * 前端不直接引用那张组合图——它里面还有标题、说明和状态图标。
 */

const BASE = '/assets/avatar';

/** 不同业务场景可以共用同一套动画逻辑，但人物素材彼此独立。 */
export const AVATAR_VARIANTS = Object.freeze({
  INTERVIEWER: 'interviewer',
  ENGLISH_COACH: 'english-coach',
});

const BASE_BY_VARIANT = {
  [AVATAR_VARIANTS.INTERVIEWER]: BASE,
  [AVATAR_VARIANTS.ENGLISH_COACH]: `${BASE}/english-coach`,
};

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
  // 以下状态**故意只用第 1/2 行的嘴型帧**，不用组合图第 3 行那 4 张"状态图"。
  // 原因：第 3 行的人物与第 1/2 行不是等比关系 —— 脸宽 110-113 时肩宽 260-270%
  // （第 1/2 行只有 173-189%），头和肩的比例本身就不同。
  // 也就是说"对齐脸就肩宽一半、对齐肩就脸小一圈"，靠缩放无法两全，
  // 用户看到的就是"说话和人静止时人物大小不一样"。
  // 好在这几张的差异本来就极小（同一个人、同一姿势），状态用文案与指示灯表达即可，
  // 这里用不同嘴型给出需要的那点表情差异。
  LISTENING: 'mouth-m',
  RECORDING: 'mouth-closed',
  FOLLOW_UP: 'mouth-smile',
  ERROR: 'mouth-closed',
};

export const ALL_FRAMES = [
  'mouth-closed', 'mouth-m', 'mouth-e', 'mouth-normal', 'mouth-o', 'mouth-a', 'mouth-smile',
  'eyes-closed', 'head-nod', 'portrait',
];

export function avatarBase(variant = AVATAR_VARIANTS.INTERVIEWER) {
  return BASE_BY_VARIANT[variant] || BASE_BY_VARIANT[AVATAR_VARIANTS.INTERVIEWER];
}

export function frameSrc(name, variant = AVATAR_VARIANTS.INTERVIEWER) {
  return `${avatarBase(variant)}/${name}.png`;
}

/**
 * 预加载全部帧。
 *
 * 说话时每秒要切好几次图，现取会闪白，所以先全部解码进内存。
 * 返回 Promise，等全部就绪再开始动画；失败的帧不会阻断其它帧。
 */
export function preloadFrames(names = ALL_FRAMES, variant = AVATAR_VARIANTS.INTERVIEWER) {
  if (typeof Image === 'undefined') return Promise.resolve([]);
  return Promise.all(names.map((name) => new Promise((resolve) => {
    const image = new Image();
    image.onload = () => resolve({ name, ok: true });
    image.onerror = () => resolve({ name, ok: false });
    image.src = frameSrc(name, variant);
  })));
}
