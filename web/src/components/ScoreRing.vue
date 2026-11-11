<script setup>
import { computed } from 'vue';

const props = defineProps({
  score: { type: Number, default: null },
  size: { type: Number, default: 112 },
  label: { type: String, default: '' },
});

const safeScore = computed(() => Math.max(0, Math.min(100, Number(props.score) || 0)));
const tone = computed(() => safeScore.value >= 80 ? '#287b63' : safeScore.value >= 65 ? '#5689a1' : safeScore.value >= 50 ? '#c18a2d' : '#c65f4a');
const ringStyle = computed(() => ({
  width: `${props.size}px`, height: `${props.size}px`,
  background: `conic-gradient(${tone.value} ${safeScore.value * 3.6}deg, #edf1ee 0deg)`,
}));
</script>

<template>
  <div class="score-ring" :style="ringStyle" role="img" :aria-label="`${label} ${score ?? '-'}`">
    <div class="score-ring-inner">
      <strong>{{ score ?? '-' }}</strong>
      <small v-if="label">{{ label }}</small>
    </div>
  </div>
</template>

<style scoped>
.score-ring { display: grid; flex: none; place-items: center; border-radius: 50%; }
.score-ring-inner { display: grid; width: calc(100% - 12px); height: calc(100% - 12px); place-content: center; border-radius: 50%; background: #fff; text-align: center; }
.score-ring strong { color: #29342f; font-size: 30px; line-height: 1; }
.score-ring small { margin-top: 6px; color: #8b9691; font-size: 12px; }
</style>
