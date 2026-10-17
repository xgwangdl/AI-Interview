/**
 * 只有麦克风的录音器（外语口语用）。
 *
 * 与面试模块的 `mediaCapture.js` 分开：那边要视频 + 分片 + 断网补传，
 * 这里只要"按住说一句、松手拿到一个几十 KB 的音频 Blob"，链路越短越好。
 */
const CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];

/** 挑一个当前浏览器支持的音频容器；都不支持就给空串（MediaRecorder 用默认值）。 */
export function pickAudioMimeType(supported = (type) => typeof MediaRecorder !== 'undefined'
  && MediaRecorder.isTypeSupported(type)) {
  return CANDIDATES.find((type) => supported(type)) || '';
}

export class AudioRecorder {
  constructor() {
    this.recorder = null;
    this.chunks = [];
    this.startedAt = 0;
  }

  async start(existingStream) {
    const stream = existingStream || await navigator.mediaDevices.getUserMedia({ audio: true });
    const mimeType = pickAudioMimeType();
    this.recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
    this.chunks = [];
    this.recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) this.chunks.push(event.data);
    };
    this.recorder.start();
    this.startedAt = Date.now();
    return stream;
  }

  get recording() {
    return Boolean(this.recorder && this.recorder.state === 'recording');
  }

  /** 停止并返回这一段音频；没录到东西时返回 null。 */
  async stop() {
    if (!this.recorder) return null;
    const recorder = this.recorder;
    const done = new Promise((resolve) => {
      recorder.onstop = () => resolve(new Blob(this.chunks, { type: recorder.mimeType || 'audio/webm' }));
    });
    if (recorder.state !== 'inactive') recorder.stop();
    const blob = await done;
    this.recorder = null;
    return blob.size > 0 ? { blob, durationMs: Date.now() - this.startedAt } : null;
  }

  cancel() {
    if (this.recorder && this.recorder.state !== 'inactive') {
      this.chunks = [];
      this.recorder.stop();
    }
    this.recorder = null;
  }
}
