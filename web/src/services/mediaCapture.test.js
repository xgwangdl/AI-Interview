import { beforeEach, describe, expect, it } from 'vitest';
import { getActiveStream, setActiveStream, stopActiveStream, supportedRecorderType } from './mediaCapture';

function fakeStream() {
  const track = {
    stopped: false,
    stop() {
      this.stopped = true;
    },
  };
  return { track, stream: { getTracks: () => [track] } };
}

describe('mediaCapture', () => {
  beforeEach(() => {
    stopActiveStream();
    delete globalThis.MediaRecorder;
  });

  it('stopActiveStream 会停止所有轨道并清空引用（离开页面的隐私保证）', () => {
    const { track, stream } = fakeStream();
    setActiveStream(stream);

    stopActiveStream();

    expect(track.stopped).toBe(true);
    expect(getActiveStream()).toBeUndefined();
  });

  it('没有活动流时停止操作是安全的', () => {
    expect(() => stopActiveStream()).not.toThrow();
  });

  it('按优先级挑选浏览器支持的录制格式', () => {
    globalThis.MediaRecorder = {
      isTypeSupported: (type) => type === 'video/webm' || type === 'video/webm;codecs=vp8,opus',
    };

    expect(supportedRecorderType()).toBe('video/webm;codecs=vp8,opus');
  });

  it('浏览器不支持任何 webm 格式时返回空字符串', () => {
    globalThis.MediaRecorder = { isTypeSupported: () => false };

    expect(supportedRecorderType()).toBe('');
  });

  it('没有 MediaRecorder 时返回空字符串而不是抛错', () => {
    expect(supportedRecorderType()).toBe('');
  });
});
