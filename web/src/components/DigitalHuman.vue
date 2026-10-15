<script setup>
/**
 * 真人数字人组件：用切好的图片素材做「按音量驱动的嘴型动画」。
 *
 * 明确的实现边界（不要对外宣称更多）：
 *   1. 这是**图片切帧动画** + **音量驱动的简单嘴型**，不是音素级口型同步；
 *   2. 音量分析只在音频可被 Web Audio 读取时可用（同源 / 允许 CORS），
 *      拿不到就自动降级为定时轮换嘴型，声音不受影响；
 *   3. 素材是人像照片切出来的，不做人脸变形，避免任何"整图缩放假装说话"。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { AVATAR_STATE, useAvatarState } from '../services/avatarState';
import { IDLE_VIDEO, TALK_ROTATION, frameSrc, preloadFrames } from '../services/avatarAssets';
import {
  frameForLevel, levelWithHysteresis, nextFallbackFrame, smoothAmplitude,
} from '../services/mouthAnimation';

const props = defineProps({
  /** 需要播放的配音地址；为空则只做状态展示 */
  audioUrl: { type: String, default: '' },
  /** 是否正在朗读：由父组件根据播放器状态给出 */
  speaking: { type: Boolean, default: false },
  /** 音量取值函数，返回 0..1；返回 -1 表示没有分析器 */
  amplitude: { type: Function, default: null },
  /** 头部姿态帧的开启（默认关闭，避免频繁切换造成跳动） */
  allowHeadNod: { type: Boolean, default: false },
  /** 待机循环视频；文件不存在时自动退回静态帧，不影响任何功能 */
  idleVideoUrl: { type: String, default: IDLE_VIDEO },
  /** 关掉就还是纯切帧（C 方案可回退） */
  useIdleVideo: { type: Boolean, default: true },
});

const emit = defineEmits(['speaking-end']);

const { state, restFrame } = useAvatarState();
const frame = ref('mouth-closed');
const ready = ref(false);
const loadFailed = ref(false);
const videoReady = ref(false);
const videoFailed = ref(false);

// 动画节奏：最小驻留 90ms，避免几十毫秒一次地暴力切图产生闪烁
const MIN_DWELL = 90;
const ROTATION_DWELL = 220;
const NOD_EVERY = 4200;
// 眨眼：3-5 秒随机一次、每次约 110ms。闭眼帧是从**闭嘴**帧合成的（嘴是闭的），
// 所以只在没说话时眨；说话时眨会看到嘴巴跟着闭一下。
const BLINK_MIN_MS = 3000;
const BLINK_MAX_MS = 5000;
const BLINK_DURATION_MS = 110;
// 说完话不要"啪"地闭嘴，先用低音量帧收一下
const CLOSING_MS = 90;

let rafId;
let lastSwitch = 0;
let lastRotation = 0;
let lastNod = 0;
let rotationIndex = 0;
let fallbackIndex = 0;
let lastTick = 0;
let smoothed = -1;      // 平滑后的音量；-1 表示没有分析器
let level = 0;          // 带迟滞的档位
let blinkAt = BLINK_MIN_MS;
let blinkUntil = 0;
let closingUntil = 0;

function pickFrame(now) {
  if (!props.speaking) {
    // 说完话的收尾：先用低音量帧过渡 90ms，再回到静止帧
    if (closingUntil && now < closingUntil) {
      if (frame.value !== 'mouth-m') frame.value = 'mouth-m';
      return;
    }
    closingUntil = 0;
    // 眨眼只在静止状态做（闭眼帧的嘴也是闭的，说话时用会露馅）
    if (now >= blinkAt && now >= blinkUntil) {
      blinkUntil = now + BLINK_DURATION_MS;
      blinkAt = now + BLINK_MIN_MS + Math.random() * (BLINK_MAX_MS - BLINK_MIN_MS);
    }
    if (now < blinkUntil) {
      if (frame.value !== 'eyes-closed') frame.value = 'eyes-closed';
      return;
    }
    // 没在说话：立刻回到当前状态的静止帧
    if (frame.value !== restFrame.value) {
      frame.value = restFrame.value;
      lastSwitch = now;
    }
    return;
  }

  // 起音快、落音慢的一阶平滑：直接用瞬时音量会让嘴型在阈值附近抖
  smoothed = smoothAmplitude(smoothed, props.amplitude ? props.amplitude() : -1);
  const value = smoothed;
  if (value < 0) {
    // 降级：没有分析器，按固定节奏轮换嘴型（比音量驱动简单，但不会静止）
    if (now - lastSwitch >= 130) {
      fallbackIndex += 1;
      frame.value = nextFallbackFrame(fallbackIndex);
      lastSwitch = now;
    }
    return;
  }

  // 带迟滞的分档：音量停在阈值附近时不会反复跳档
  level = levelWithHysteresis(level, value);

  // 同档位内轮换，避免一直停在同一张图；只在中等以上音量时轮换，静音时保持闭唇
  if (level >= 1 && now - lastRotation >= ROTATION_DWELL) {
    rotationIndex += 1;
    lastRotation = now;
  }
  const candidate = frameForLevel(level, rotationIndex);

  // 偶尔点头：低频点缀，且素材头部角度不同，切太勤会跳
  if (props.allowHeadNod && now - lastNod >= NOD_EVERY) {
    lastNod = now;
    frame.value = 'head-nod';
    lastSwitch = now;
    return;
  }

  if (candidate !== frame.value && now - lastSwitch >= MIN_DWELL) {
    frame.value = candidate;
    lastSwitch = now;
  }
}

function tick(now) {
  // 页面切到后台时 rAF 本就会停，这里再兜一道，避免回来时一次跳很多帧
  if (now - lastTick > 500) lastSwitch = now;
  lastTick = now;
  pickFrame(now);
  rafId = requestAnimationFrame(tick);
}

const source = computed(() => frameSrc(frame.value));
const showAsSpeaking = computed(() => props.speaking);
/**
 * 待机循环视频（C 方案）：视频可用且当前不在说话时播视频，说话时切回切帧。
 * 没有放视频文件是正常情况，静默退回静态帧。
 */
const showIdleVideo = computed(() => (
  props.useIdleVideo && videoReady.value && !props.speaking && Boolean(props.idleVideoUrl)
));

function onVideoReady() {
  videoReady.value = true;
}

function onVideoError() {
  videoReady.value = false;
  videoFailed.value = true;
}

/** 换状态时立刻切到静止帧，不等下一帧动画。 */
watch(restFrame, (next) => {
  if (!props.speaking) frame.value = next;
});

watch(() => props.speaking, (speaking) => {
  if (speaking) {
    // 新一轮说话从零开始，别带着上一轮的残留音量
    smoothed = -1;
    level = 0;
    closingUntil = 0;
    blinkUntil = 0;
  } else {
    closingUntil = performance.now() + CLOSING_MS;
    emit('speaking-end');
  }
});

onMounted(async () => {
  const results = await preloadFrames();
  loadFailed.value = results.filter((item) => !item.ok).length > 0;
  ready.value = true;
  rafId = requestAnimationFrame(tick);
});

onBeforeUnmount(() => {
  if (rafId) cancelAnimationFrame(rafId);
});

defineExpose({ frame, ready, loadFailed });
</script>

<template>
  <div class="digital-human" :class="[state.toLowerCase(), { speaking: showAsSpeaking }]">
    <div class="dh-stage">
      <!-- 素材加载完成前显示占位，避免出现白屏/黑屏 -->
      <div v-if="!ready" class="dh-placeholder"></div>
      <!-- 待机循环视频：有素材就播它，没有就退回静态帧（见 showIdleVideo） -->
      <video
        v-if="showIdleVideo"
        ref="idleVideo"
        class="dh-video"
        :src="idleVideoUrl"
        autoplay
        muted
        loop
        playsinline
        @loadeddata="onVideoReady"
        @error="onVideoError"
      ></video>
      <img
        v-else-if="ready"
        class="dh-frame"
        :src="source"
        :alt="`AI 面试官（${state}）`"
        draggable="false"
      />
      <span class="dh-dots"><i :class="{ on: state === AVATAR_STATE.PREPARING }"></i><i :class="{ on: state === AVATAR_STATE.SPEAKING }"></i><i :class="{ on: state === AVATAR_STATE.LISTENING || state === AVATAR_STATE.RECORDING }"></i></span>
    </div>
    <p v-if="loadFailed" class="dh-warn">部分素材没能加载，数字人可能显示不全。</p>
  </div>
</template>

<style scoped>
.digital-human { display: grid; gap: 6px; }
.dh-stage { position: relative; display: grid; place-items: center; overflow: hidden; border-radius: 8px; background: radial-gradient(circle at 50% 24%, #ffffff, #e4efe8); }
.dh-frame { width: 100%; height: 100%; object-fit: contain; user-select: none; }
.dh-video { width: 100%; height: 100%; object-fit: cover; user-select: none; }
.dh-placeholder { width: 100%; height: 100%; min-height: 160px; }

/* 非常轻微的呼吸浮动：用独立的 translate 属性，不碰 transform，
   免得和响应式里的缩放打架，也不会被误当成"说话动作" */
.dh-frame { animation: dh-float 4.2s ease-in-out infinite, dh-sway 7.3s ease-in-out infinite; }
.digital-human.speaking .dh-frame { animation-duration: 2.6s, 7.3s; }
@keyframes dh-float { 0%, 100% { translate: 0 0; } 50% { translate: 0 -4px; } }

/* 头部微摆动：只转 0.6 度、绕脖子附近转，看着像在轻微调整姿态。
   用 transform 而不是 translate，与上面的浮动互不干扰。 */
.dh-frame { transform-origin: 50% 88%; }
@keyframes dh-sway {
  0%, 100% { transform: rotate(-0.6deg); }
  50% { transform: rotate(0.6deg); }
}

.dh-dots { position: absolute; top: 8px; right: 9px; display: flex; gap: 4px; }
.dh-dots i { width: 5px; height: 5px; border-radius: 50%; background: rgba(71, 112, 89, .25); }
.dh-dots i.on { background: #4f8a6b; }

.dh-warn { margin: 0; color: #a4483a; font-size: 14px; }

@media (prefers-reduced-motion: reduce) { .dh-frame { animation: none; } .dh-video { display: none; } }
</style>
