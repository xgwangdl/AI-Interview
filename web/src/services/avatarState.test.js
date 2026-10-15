import { beforeEach, describe, expect, it } from 'vitest';
import {
  AVATAR_STATE, clearAvatarError, fromBackendState, resetAvatarState, setAvatarError,
  setAvatarState, useAvatarState,
} from './avatarState';

describe('avatarState（数字人状态的唯一来源）', () => {
  beforeEach(() => resetAvatarState());

  it('默认是 IDLE，静止帧为闭唇', () => {
    const { state, restFrame } = useAvatarState();
    expect(state.value).toBe(AVATAR_STATE.IDLE);
    expect(restFrame.value).toBe('mouth-closed');
  });

  it('每个状态都有对应的静止帧', () => {
    const { state, restFrame } = useAvatarState();
    setAvatarState(AVATAR_STATE.LISTENING);
    expect(state.value).toBe(AVATAR_STATE.LISTENING);
    expect(restFrame.value).toBe('mouth-m');

    setAvatarState(AVATAR_STATE.RECORDING);
    expect(restFrame.value).toBe('mouth-closed');

    setAvatarState(AVATAR_STATE.FOLLOW_UP);
    expect(restFrame.value).toBe('mouth-smile');
  });

  /**
   * 非说话状态**故意**只用第 1/2 行的嘴型帧，不用组合图第 3 行的"状态图"：
   * 那 4 张与第 1/2 行不是等比关系（脸宽相同时肩宽达 260-270%，而第 1/2 行只有 173-189%），
   * 对齐脸就肩宽近一倍、对齐肩就脸小一圈，靠缩放无法两全 ——
   * 用户看到的就是"说话和不说话时人物大小不一样"。
   */
  it('非说话状态不使用第 3 行的状态图（尺寸无法与嘴型帧对齐）', () => {
    const { restFrame } = useAvatarState();
    for (const target of [AVATAR_STATE.IDLE, AVATAR_STATE.PREPARING, AVATAR_STATE.LISTENING,
      AVATAR_STATE.RECORDING, AVATAR_STATE.FOLLOW_UP, AVATAR_STATE.ERROR]) {
      setAvatarState(target);
      expect(restFrame.value.startsWith('state-')).toBe(false);
    }
  });

  it('拒绝未知状态，不会把状态改坏', () => {
    setAvatarState(AVATAR_STATE.SPEAKING);
    setAvatarState('NOT_A_STATE');
    expect(useAvatarState().state.value).toBe(AVATAR_STATE.SPEAKING);
  });

  it('后端 narration 的 state 能映射成本地状态', () => {
    expect(fromBackendState('thinking')).toBe(AVATAR_STATE.PREPARING);
    expect(fromBackendState('asking')).toBe(AVATAR_STATE.SPEAKING);
    expect(fromBackendState('listening')).toBe(AVATAR_STATE.LISTENING);
    expect(fromBackendState('unknown-value', AVATAR_STATE.LISTENING)).toBe(AVATAR_STATE.LISTENING);
  });

  it('报错进入 ERROR，并带上可读原因；切状态时清掉', () => {
    const { state, error } = useAvatarState();
    setAvatarError('音频解码失败');
    expect(state.value).toBe(AVATAR_STATE.ERROR);
    expect(error.value).toBe('音频解码失败');

    setAvatarState(AVATAR_STATE.LISTENING);
    expect(error.value).toBeNull();
  });

  it('clearAvatarError 只清错误信息', () => {
    setAvatarError('boom');
    clearAvatarError();
    expect(useAvatarState().error.value).toBeNull();
  });

  it('自动播放被拦时记录 needsGesture', () => {
    setAvatarState(AVATAR_STATE.LISTENING, { needsGesture: true });
    expect(useAvatarState().needsGesture.value).toBe(true);
    setAvatarState(AVATAR_STATE.SPEAKING, { needsGesture: false });
    expect(useAvatarState().needsGesture.value).toBe(false);
  });
});
