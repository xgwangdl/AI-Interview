/**
 * 只有麦克风的录音器（外语口语用）。
 *
 * 优先自己拼接 **16k 单声道 WAV**：后端拿到就能直接送识别，不用先跑 ffmpeg 转码
 * （实测这一步能省 0.3-0.8 秒）。浏览器不支持 AudioContext 时退回 MediaRecorder（webm），
 * 那条路后端仍会用 ffmpeg 转一次。
 */
const CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];
const TARGET_RATE = 16000;

export function pickAudioMimeType(supported = (type) => typeof MediaRecorder !== 'undefined'
  && MediaRecorder.isTypeSupported(type)) {
  return CANDIDATES.find((type) => supported(type)) || '';
}

/**
 * 把若干段 Float32 采样拼成一个 16k 单声道 16bit WAV。
 *
 * 采样率不一致时做一次线性重采样：`new AudioContext({sampleRate:16000})` 大多数浏览器会照办，
 * 但系统可能仍给 44.1k/48k，重采样比"让后端 ffmpeg 再转一次"便宜得多。
 */
export function encodeWav(chunks, sampleRate, targetRate = TARGET_RATE) {
  const total = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const source = new Float32Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    source.set(chunk, offset);
    offset += chunk.length;
  }
  const rate = Number(sampleRate) > 0 ? Number(sampleRate) : targetRate;
  const ratio = rate / targetRate;
  const length = ratio === 1 ? source.length : Math.max(1, Math.floor(source.length / ratio));
  const samples = new Int16Array(length);
  for (let index = 0; index < length; index++) {
    const position = index * ratio;
    const before = Math.floor(position);
    const after = Math.min(before + 1, source.length - 1);
    const fraction = position - before;
    const value = (source[before] || 0) * (1 - fraction) + (source[after] || 0) * fraction;
    samples[index] = Math.max(-1, Math.min(1, value)) * 0x7fff;
  }

  const buffer = new ArrayBuffer(44 + samples.length * 2);
  const view = new DataView(buffer);
  writeAscii(view, 0, 'RIFF');
  view.setUint32(4, 36 + samples.length * 2, true);
  writeAscii(view, 8, 'WAVE');
  writeAscii(view, 12, 'fmt ');
  view.setUint32(16, 16, true);       // fmt chunk 长度
  view.setUint16(20, 1, true);        // PCM
  view.setUint16(22, 1, true);        // 单声道
  view.setUint32(24, targetRate, true);
  view.setUint32(28, targetRate * 2, true);  // 字节率
  view.setUint16(32, 2, true);        // 块对齐
  view.setUint16(34, 16, true);       // 位深
  writeAscii(view, 36, 'data');
  view.setUint32(40, samples.length * 2, true);
  for (let index = 0; index < samples.length; index++) {
    view.setInt16(44 + index * 2, samples[index], true);
  }
  return new Blob([buffer], { type: 'audio/wav' });
}

function writeAscii(view, offset, text) {
  for (let index = 0; index < text.length; index++) {
    view.setUint8(offset + index, text.charCodeAt(index));
  }
}

export class AudioRecorder {
  constructor() {
    this.mode = null;
    this.startedAt = 0;
  }

  async start(existingStream) {
    this.stream = existingStream || await navigator.mediaDevices.getUserMedia({ audio: true });
    try {
      this.startWav();
    } catch {
      this.startMediaRecorder();
    }
    this.startedAt = Date.now();
    return this.stream;
  }

  startWav() {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) throw new Error('没有 AudioContext');
    this.context = new Ctx({ sampleRate: TARGET_RATE });
    this.source = this.context.createMediaStreamSource(this.stream);
    this.processor = this.context.createScriptProcessor(4096, 1, 1);
    this.chunks = [];
    this.processor.onaudioprocess = (event) => {
      this.chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)));
    };
    // 必须连到 destination 才会持续触发；用 0 增益避免把自己说的话外放
    const mute = this.context.createGain();
    mute.gain.value = 0;
    this.source.connect(this.processor);
    this.processor.connect(mute);
    mute.connect(this.context.destination);
    this.mode = 'wav';
  }

  startMediaRecorder() {
    const mimeType = pickAudioMimeType();
    this.recorder = new MediaRecorder(this.stream, mimeType ? { mimeType } : undefined);
    this.buffers = [];
    this.recorder.ondataavailable = (event) => {
      if (event.data && event.data.size > 0) this.buffers.push(event.data);
    };
    this.recorder.start();
    this.mode = 'media';
  }

  get recording() {
    return this.mode !== null;
  }

  /** 停止并返回这一段音频；没录到东西时返回 null。 */
  async stop() {
    if (this.mode === 'wav') {
      const sampleRate = this.context?.sampleRate || TARGET_RATE;
      this.disconnectWav();
      const blob = encodeWav(this.chunks || [], sampleRate);
      this.mode = null;
      return blob.size > 44 ? { blob, durationMs: Date.now() - this.startedAt } : null;
    }
    if (this.mode === 'media') {
      const recorder = this.recorder;
      const done = new Promise((resolve) => {
        recorder.onstop = () => resolve(new Blob(this.buffers, { type: recorder.mimeType || 'audio/webm' }));
      });
      if (recorder.state !== 'inactive') recorder.stop();
      const blob = await done;
      this.mode = null;
      this.recorder = null;
      return blob.size > 0 ? { blob, durationMs: Date.now() - this.startedAt } : null;
    }
    return null;
  }

  cancel() {
    this.chunks = [];
    this.buffers = [];
    this.disconnectWav();
    if (this.recorder && this.recorder.state !== 'inactive') this.recorder.stop();
    this.recorder = null;
    this.mode = null;
  }

  disconnectWav() {
    try {
      this.processor?.disconnect();
      this.source?.disconnect();
      this.context?.close?.();
    } catch {
      // 关闭失败不影响已经录到的音频
    }
    this.processor = null;
    this.source = null;
    this.context = null;
  }
}
