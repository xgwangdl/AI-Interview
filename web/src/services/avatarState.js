import { computed, reactive } from 'vue';
import { STATE_FRAMES } from './avatarAssets';

/**
 * 数字人的状态机——**唯一来源**。
 *
 * 组件只负责"把状态画出来"，不允许自己改状态；需要切换状态的地方统一调
 * `setAvatarState()`。否则一圈组件各写各的标记，就会出现"音频停了嘴还在动"
 * 或者"已经重新提问了还显示在听"这类不一致。
 */

export const AVATAR_STATE = {
  IDLE: 'IDLE',
  PREPARING: 'PREPARING',
  SPEAKING: 'SPEAKING',
  LISTENING: 'LISTENING',
  RECORDING: 'RECORDING',
  FOLLOW_UP: 'FOLLOW_UP',
  ERROR: 'ERROR',
};

/** 后端 narration 接口返回的 state 字段 → 本地状态。 */
const FROM_BACKEND = {
  thinking: AVATAR_STATE.PREPARING,
  asking: AVATAR_STATE.SPEAKING,
  listening: AVATAR_STATE.LISTENING,
  finished: AVATAR_STATE.LISTENING,
};

const state = reactive({
  current: AVATAR_STATE.IDLE,
  /** 音频播放失败等异常信息，界面据此显示"语音暂不可用" */
  error: null,
  /** 浏览器拦了自动播放：提示用户点一下 */
  needsGesture: false,
});

export function fromBackendState(value, fallback = AVATAR_STATE.IDLE) {
  return FROM_BACKEND[value] || fallback;
}

export function setAvatarState(next, options = {}) {
  if (!Object.values(AVATAR_STATE).includes(next)) {
    console.warn('[avatar] 未知状态', next);
    return;
  }
  state.current = next;
  if (options.clearError !== false) state.error = null;
  if (options.needsGesture !== undefined) state.needsGesture = options.needsGesture;
}

export function setAvatarError(message) {
  state.current = AVATAR_STATE.ERROR;
  state.error = message || '数字人素材或配音异常';
  state.needsGesture = false;
}

export function clearAvatarError() {
  state.error = null;
}

export function resetAvatarState() {
  state.current = AVATAR_STATE.IDLE;
  state.error = null;
  state.needsGesture = false;
}

export function useAvatarState() {
  return {
    state: computed(() => state.current),
    error: computed(() => state.error),
    needsGesture: computed(() => state.needsGesture),
    isSpeaking: computed(() => state.current === AVATAR_STATE.SPEAKING),
    /** 静止帧：由状态决定，说话时会被动画逐帧覆盖 */
    restFrame: computed(() => STATE_FRAMES[state.current] || 'mouth-closed'),
  };
}
