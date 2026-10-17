<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { buildWeekly, loadWeeklyReport, shareText, weekLabel } from '../services/progress';

const route = useRoute();
const router = useRouter();
const report = ref(null);
const loading = ref(true);
const week = ref(typeof route.query.week === 'string' ? route.query.week : '');

onMounted(load);

const range = computed(() => {
  if (!report.value) return '';
  return `${weekLabel(report.value.weekStart)} – ${weekLabel(report.value.weekEnd)}`;
});
const maxDimension = computed(() => {
  const values = report.value?.dimensions?.map((item) => item.value || 0) || [];
  return Math.max(100, ...values);
});

async function load() {
  loading.value = true;
  try {
    report.value = buildWeekly(await loadWeeklyReport(week.value));
  } catch (error) {
    ElMessage.error(error?.message || '无法读取周报');
  } finally {
    loading.value = false;
  }
}

function shiftWeek(days) {
  const base = new Date(`${report.value.weekStart}T00:00:00`);
  base.setDate(base.getDate() + days);
  const pad = (value) => String(value).padStart(2, '0');
  week.value = `${base.getFullYear()}-${pad(base.getMonth() + 1)}-${pad(base.getDate())}`;
  router.replace({ path: '/progress/weekly', query: { week: week.value } });
  void load();
}

async function copyShare() {
  const text = shareText(report.value);
  try {
    await navigator.clipboard.writeText(text);
    ElMessage.success('分享文案已复制');
  } catch {
    ElMessage.info(text);
  }
}
</script>

<template>
  <section v-loading="loading" class="weekly-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">WEEKLY REVIEW</div>
        <h1>本周训练报告</h1>
        <p v-if="report">{{ range }} · 只统计这一周的计划、练习与分数</p>
      </div>
      <div class="week-actions">
        <el-button class="soft-button" @click="shiftWeek(-7)"><el-icon><ArrowLeft /></el-icon>上一周</el-button>
        <el-button class="soft-button" @click="shiftWeek(7)">下一周<el-icon><ArrowRight /></el-icon></el-button>
        <el-button class="soft-button" @click="copyShare"><el-icon><CopyDocument /></el-icon>复制分享文案</el-button>
      </div>
    </div>

    <template v-if="report">
      <div class="metric-grid">
        <article class="metric panel"><span>计划完成</span><strong>{{ report.planCompleted }}<small> / {{ report.planTotal }}</small></strong><i><b :style="{ width: `${report.completionRate}%` }"></b></i></article>
        <article class="metric panel"><span>练习场次</span><strong>{{ report.sessions }}</strong><small>{{ report.answers }} 个回答</small></article>
        <article class="metric panel"><span>综合平均</span><strong>{{ report.averageScore ?? '—' }}</strong><small>较上周 {{ report.deltaLabel }}</small></article>
        <article class="metric panel"><span>有效训练</span><strong>{{ report.planCompleted >= 2 ? '达标' : '继续' }}</strong><small>每天 2 题算达标</small></article>
      </div>

      <div class="weekly-grid">
        <section class="panel dimension-panel">
          <div class="section-title"><span>维度变化</span><small>低于 75 的会进入下周建议</small></div>
          <div class="dimension-list">
            <div v-for="item in report.dimensions" :key="item.key" class="dimension-row">
              <span>{{ item.label }}</span>
              <i><b :style="{ width: `${((item.value || 0) / maxDimension) * 100}%` }"></b></i>
              <strong>{{ item.value ?? '—' }}</strong>
            </div>
          </div>
        </section>

        <section class="panel suggestion-panel">
          <div class="section-title"><span>下周怎么练</span><small>最多 3 条</small></div>
          <ul>
            <li v-for="(item, index) in report.suggestions" :key="index">{{ item }}</li>
          </ul>
          <el-button class="primary-button" @click="router.push('/today')">开始今天训练</el-button>
        </section>
      </div>

      <section v-if="report.bestImprovement" class="panel best-panel">
        <div class="section-title"><span>本周进步最大</span><small>{{ report.bestImprovement.delta > 0 ? '+' : '' }}{{ report.bestImprovement.delta }} 分</small></div>
        <h2>{{ report.bestImprovement.questionText || `第 ${report.bestImprovement.questionIndex + 1} 题` }}</h2>
        <div class="best-grid">
          <div><span>第 1 次 · {{ report.bestImprovement.beforeScore }}</span><p>{{ report.bestImprovement.beforeTranscript }}</p></div>
          <div class="after"><span>当前 · {{ report.bestImprovement.afterScore }}</span><p>{{ report.bestImprovement.afterTranscript }}</p></div>
        </div>
      </section>
      <p v-else class="weekly-empty">本周还没有同一道题的两次回答，先完成一次重练就能看到 before/after。</p>
    </template>
  </section>
</template>

<style scoped>
.weekly-page { max-width: 1080px; }
.week-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin-bottom: 16px; }
.metric { display: grid; gap: 5px; padding: 14px 16px; box-shadow: none; }
.metric span { color: #8d9792; font-size: 12px; }
.metric strong { color: #2f3c35; font-size: 25px; font-weight: 650; }
.metric strong small { color: #8d9792; font-size: 13px; }
.metric > small { color: #98a29d; font-size: 11px; }
.metric i { display: block; height: 6px; overflow: hidden; border-radius: 3px; background: #eef1ee; }
.metric i b { display: block; height: 100%; background: var(--green); }
.weekly-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, .9fr); gap: 16px; }
.dimension-panel, .suggestion-panel, .best-panel { padding: 17px 18px; box-shadow: none; }
.dimension-list { display: grid; gap: 11px; }
.dimension-row { display: grid; grid-template-columns: 80px minmax(0, 1fr) 34px; align-items: center; gap: 9px; }
.dimension-row span { color: #5d6a64; font-size: 12px; }
.dimension-row i { display: block; height: 7px; overflow: hidden; border-radius: 4px; background: #eef1ee; }
.dimension-row b { display: block; height: 100%; background: var(--green); }
.dimension-row strong { color: #46524c; font-size: 12px; text-align: right; }
.suggestion-panel ul { margin: 0 0 14px; padding-left: 18px; color: #5d6a64; font-size: 13px; line-height: 1.9; }
.best-panel { margin-top: 16px; }
.best-panel h2 { margin: 0 0 10px; color: #2f3c35; font-size: 17px; }
.best-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.best-grid > div { padding: 10px 12px; border-radius: 7px; background: #f7f9f8; }
.best-grid > div.after { background: #f0f8f4; }
.best-grid span { color: #a9722f; font-size: 11px; font-weight: 600; }
.best-grid p { margin: 6px 0 0; color: #5d6a64; font-size: 12px; line-height: 1.7; }
.weekly-empty { margin: 14px 0 0; color: #98a29d; font-size: 12px; }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.weekly-grid { grid-template-columns: 1fr; } }
@media (max-width: 600px) { .metric-grid, .best-grid { grid-template-columns: 1fr; } }
</style>
