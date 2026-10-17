/**
 * L2 的两块音频基础件：麦克风 → PCM16 帧；PCM16 帧 → 排队播放。
 *
 * 为什么不用 MediaRecorder：实时识别要的是"边说边推"的原始 PCM，
 * 而 webm/ogg 是封装格式，后端再拆一层只会更慢。
 */
export const PCM_RATE = 16000;

export class PcmCapture {
  constructor(onFrame) {
    this.onFrame = onFrame;
  }

  async start(stream) {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    this.context = new Ctx({ sampleRate: PCM_RATE });
    this.source = this.context.createMediaStreamSource(stream);
    this.processor = this.context.createScriptProcessor(4096, 1, 1);
    this.processor.onaudioprocess = (event) => {
      const input = event.inputBuffer.getChannelData(0);
      const pcm = new Int16Array(input.length);
      for (let index = 0; index < input.length; index++) {
        pcm[index] = Math.max(-1, Math.min(1, input[index])) * 0x7fff;
      }
      this.onFrame?.(pcm.buffer);
    };
    const mute = this.context.createGain();
    mute.gain.value = 0;
    this.source.connect(this.processor);
    this.processor.connect(mute);
    mute.connect(this.context.destination);
  }

  stop() {
    try {
      this.processor?.disconnect();
      this.source?.disconnect();
      this.context?.close?.();
    } catch {
      // 关闭失败不影响已经推出去的帧
    }
    this.processor = null;
    this.source = null;
    this.context = null;
  }
}

/** 把服务端推来的 PCM 帧按时间轴排好，边到边播。 */
export class PcmPlayer {
  constructor(sampleRate = 24000) {
    this.sampleRate = sampleRate;
    this.sources = [];
    this.nextTime = 0;
    this.context = null;
  }

  ensureContext() {
    if (!this.context) {
      const Ctx = window.AudioContext || window.webkitAudioContext;
      this.context = new Ctx();
    }
    return this.context;
  }

  push(base64) {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
    const samples = new Int16Array(bytes.buffer, bytes.byteOffset, Math.floor(bytes.length / 2));
    if (!samples.length) return;
    const context = this.ensureContext();
    const buffer = context.createBuffer(1, samples.length, this.sampleRate);
    const channel = buffer.getChannelData(0);
    for (let index = 0; index < samples.length; index++) channel[index] = samples[index] / 0x8000;
    const source = context.createBufferSource();
    source.buffer = buffer;
    source.connect(context.destination);
    const startAt = Math.max(context.currentTime, this.nextTime);
    source.start(startAt);
    this.nextTime = startAt + buffer.duration;
    this.sources.push(source);
    source.onended = () => {
      this.sources = this.sources.filter((item) => item !== source);
    };
  }

  stop() {
    for (const source of this.sources) {
      try { source.stop(); } catch { /* 已经停了 */ }
    }
    this.sources = [];
    this.nextTime = 0;
  }

  get playing() {
    return this.sources.length > 0;
  }
}
