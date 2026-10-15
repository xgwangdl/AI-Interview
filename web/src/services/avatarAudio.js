/**
 * 面试官配音的播放与音量分析。
 *
 * 只用一个 HTMLAudioElement，并且**只给这个元素创建一次 MediaElementAudioSourceNode**——
 * 同一个元素创建第二次会直接抛 InvalidStateError，这是 Web Audio 最常见的坑之一。
 *
 * 关于"嘴型"的边界（避免误解为真实口型同步）：
 *   * 这里做的是**按音量大小挑嘴型**，属于"音量驱动的简单动画"；
 *   * 它无法识别音素，不会因为念的是 "a" 还是 "o" 而变化，只是"声音大 → 嘴张得大"；
 *   * 真正基于音素/专业模型的口型同步不在本次范围内。
 *   音量分析还要求音频能被 fetch 到（同源或允许 CORS），否则分析器拿不到数据。
 */

const SAMPLE_WINDOW = 1024;

export function createAvatarAudio() {
  let element = null;
  let context = null;
  let analyser = null;
  let source = null;
  let sourceBound = false;   // 保证 MediaElementSource 只创建一次
  let buffer = null;
  let listeners = {};

  function ensureElement() {
    if (element) return element;
    element = new Audio();
    element.preload = 'auto';
    element.crossOrigin = 'anonymous';
    element.addEventListener('ended', () => emit('ended'));
    element.addEventListener('error', () => emit('error', elementError()));
    return element;
  }

  function elementError() {
    const code = element?.error?.code;
    const reasons = {
      1: '播放被中止',
      2: '网络错误，音频无法下载',
      3: '音频解码失败（格式可能不受支持）',
      4: '音频格式不支持或地址不可用',
    };
    return new Error(reasons[code] || '音频播放失败');
  }

  function emit(type, payload) {
    (listeners[type] || []).forEach((handler) => {
      try {
        handler(payload);
      } catch (error) {
        console.error('[avatar-audio] 回调出错', error);
      }
    });
  }

  /**
   * 接上分析器。必须由用户手势触发，否则 AudioContext 处于 suspended 状态，
   * 分析出来永远是静音（声音反而正常）——会出现"有声音但嘴不动"。
   */
  async function attachAnalyser() {
    if (analyser) {
      if (context.state === 'suspended') await context.resume();
      return true;
    }
    try {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      if (!AudioCtx) return false;
      context = new AudioCtx();
      if (context.state === 'suspended') await context.resume();
      source = context.createMediaElementSource(ensureElement());
      sourceBound = true;
      analyser = context.createAnalyser();
      analyser.fftSize = SAMPLE_WINDOW * 2;
      analyser.smoothingTimeConstant = 0.6;
      source.connect(analyser);
      analyser.connect(context.destination);   // 必须接回输出，否则没声音
      buffer = new Float32Array(analyser.fftSize);
      return true;
    } catch (error) {
      // 跨域或浏览器限制都会走到这里：降级成"定时切嘴型"，不影响播放
      console.warn('[avatar-audio] 无法启用音量分析，改用定时嘴型动画', error?.message);
      analyser = null;
      context = null;
      source = null;
      return false;
    }
  }

  /** 当前音量（0..1）。没有分析器时返回 -1，调用方据此走定时降级。 */
  function amplitude() {
    if (!analyser || !buffer) return -1;
    analyser.getFloatTimeDomainData(buffer);
    let sum = 0;
    for (let i = 0; i < buffer.length; i += 1) sum += buffer[i] * buffer[i];
    const rms = Math.sqrt(sum / buffer.length);
    // 人声的 RMS 通常在 0.02~0.3，这里放大到 0..1 便于分档
    return Math.max(0, Math.min(1, rms * 4));
  }

  return {
    /** 换一段音频；会停止当前播放并重置进度。 */
    load(url) {
      const node = ensureElement();
      node.src = url;
      node.currentTime = 0;
      return node;
    },

    /**
     * 播放。返回 'played' | 'needs-gesture' | 'analyser-ready'
     * needs-gesture 表示浏览器拦了自动播放，界面应提示用户点一下。
     */
    async play() {
      const node = ensureElement();
      if (!node.src) throw new Error('还没有加载音频');
      await attachAnalyser();
      try {
        await node.play();
        return analyser ? 'analyser-ready' : 'played';
      } catch (error) {
        if (error?.name === 'NotAllowedError') return 'needs-gesture';
        throw error;
      }
    },

    /** 由用户手势调用：既能恢复播放，也能顺带把 AudioContext 解锁。 */
    async playFromGesture() {
      await attachAnalyser();
      const node = ensureElement();
      node.currentTime = 0;
      await node.play();
      return analyser ? 'analyser-ready' : 'played';
    },

    pause() {
      if (element && !element.paused) element.pause();
    },

    async resume() {
      if (!element) return;
      await attachAnalyser();
      await element.play();
    },

    stop() {
      if (!element) return;
      element.pause();
      try {
        element.currentTime = 0;
      } catch {
        // 某些浏览器在未加载完成时设置 currentTime 会抛错，忽略即可
      }
    },

    amplitude,
    hasAnalyser: () => Boolean(analyser),
    isPlaying: () => Boolean(element) && !element.paused && !element.ended,
    currentTime: () => element?.currentTime ?? 0,
    duration: () => (Number.isFinite(element?.duration) ? element.duration : null),
    element: () => element,
    on(type, handler) {
      listeners = { ...listeners, [type]: [...(listeners[type] || []), handler] };
      return () => {
        listeners = { ...listeners, [type]: (listeners[type] || []).filter((h) => h !== handler) };
      };
    },
    dispose() {
      listeners = {};
      if (element) {
        element.pause();
        element.removeAttribute('src');
      }
      if (context) context.close();
      element = null;
      context = null;
      analyser = null;
      source = null;
      buffer = null;
    },
    /** 供调试/测试查看内部状态 */
    _state: () => ({ sourceBound, hasAnalyser: Boolean(analyser) }),
  };
}
