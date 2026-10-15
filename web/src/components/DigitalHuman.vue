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
import { TALK_ROTATION, frameSrc, preloadFrames } from '../services/avatarAssets';
import { frameForLevel, levelFromAmplitude, nextFallbackFrame } from '../services/mouthAnimation';

const props = defineProps({
  /** 需要播放的配音地址；为空则只做状态展示 */
  audioUrl: { type: String, default: '' },
  /** 是否正在朗读：由父组件根据播放器状态给出 */
  speaking: { type: Boolean, default: false },
  /** 音量取值函数，返回 0..1；返回 -1 表示没有分析器 */
  amplitude: { type: Function, default: null },
  /** 头部姿态帧的开启（默认关闭，避免频繁切换造成跳动） */
  allowHeadNod: { type: Boolean, default: false },
});

const emit = defineEmits(['speaking-end']);

const { state, restFrame } = useAvatarState();
const frame = ref('mouth-closed');
const ready = ref(false);
const loadFailed = ref(false);

// 动画节奏：最小驻留 90ms，避免几十毫秒一次地暴力切图产生闪烁
const MIN_DWELL = 90;
const ROTATION_DWELL = 220;
const NOD_EVERY = 4200;

let rafId;
let lastSwitch = 0;
let lastRotation = 0;
let lastNod = 0;
let rotationIndex = 0;
let fallbackIndex = 0;
let lastTick = 0;

function pickFrame(now) {
  const peak = props.speaking;
  if (!peak) {
    // 没在说话：立刻回到当前状态的静止帧
    if (frame.value !== restFrame.value) {
      frame.value = restFrame.value;
      lastSwitch = now;
    }
    return;
  }

  const value = props.amplitude ? props.amplitude() : -1;
  if (value < 0) {
    // 降级：没有分析器，按固定节奏轮换嘴型（比音量驱动简单，但不会静止）
    if (now - lastSwitch >= 130) {
      fallbackIndex += 1;
      frame.value = nextFallbackFrame(fallbackIndex);
      lastSwitch = now;
    }
    return;
  }

  const level = levelFromAmplitude(value);

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

/** 换状态时立刻切到静止帧，不等下一帧动画。 */
watch(restFrame, (next) => {
  if (!props.speaking) frame.value = next;
});

watch(() => props.speaking, (speaking) => {
  if (!speaking) {
    frame.value = restFrame.value;
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
      <img
        v-else
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
.dh-placeholder { width: 100%; height: 100%; min-height: 160px; }

/* 非常轻微的呼吸浮动：用独立的 translate 属性，不碰 transform，
   免得和响应式里的缩放打架，也不会被误当成"说话动作" */
.dh-frame { animation: dh-float 4.2s ease-in-out infinite; }
.digital-human.speaking .dh-frame { animation-duration: 2.6s; }
@keyframes dh-float { 0%, 100% { translate: 0 0; } 50% { translate: 0 -4px; } }

.dh-dots { position: absolute; top: 8px; right: 9px; display: flex; gap: 4px; }
.dh-dots i { width: 5px; height: 5px; border-radius: 50%; background: rgba(71, 112, 89, .25); }
.dh-dots i.on { background: #4f8a6b; }

.dh-warn { margin: 0; color: #a4483a; font-size: 11px; }

@media (prefers-reduced-motion: reduce) { .dh-frame { animation: none; } }
</style>
