<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import {
  archiveGoal, deleteGoal, getGoal, getTodayPlan, GOAL_STATUS_LABEL_KEYS, goalProgressPercent,
  interviewLabel, listGoalSessions, PLAN_KIND_LABEL_KEYS, STAGE_LABEL_KEYS, startPlanItem, updatePlanItem,
} from '../services/goals';
import { DIMENSION_LABEL_KEYS } from '../services/practiceArchive';
import { useI18n } from 'vue-i18n';

const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();
const practice = usePracticeStore();
const goal = ref(null);
const plan = ref(null);
const sessions = ref([]);
const loading = ref(true);
const starting = ref('');
const dimensions = computed(() => Object.keys(DIMENSION_LABEL_KEYS).map((key) => [key, t(DIMENSION_LABEL_KEYS[key])]));

const progressPercent = computed(() => goalProgressPercent(goal.value?.progress));
const completedToday = computed(() => plan.value?.completedCount || 0);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    goal.value = await getGoal(route.params.id);
    [plan.value, sessions.value] = await Promise.all([
      getTodayPlan(goal.value.id),
      listGoalSessions(goal.value.id),
    ]);
  } catch (error) {
    ElMessage.error(error?.message || t('goals.readFailed'));
  } finally {
    loading.value = false;
  }
}

async function start(item) {
  if (starting.value) return;
  starting.value = item.id;
  try {
    const session = await startPlanItem(item.id);
    await practice.loadFromServer();
    router.push(`/practice/device-check?id=${session.id}`);
  } catch (error) {
    ElMessage.error(error?.message || t('goals.startFailed'));
  } finally {
    starting.value = '';
  }
}

async function skip(item) {
  try {
    await updatePlanItem(item.id, 'skipped');
    plan.value = await getTodayPlan(goal.value.id);
  } catch (error) {
    ElMessage.error(error?.message || t('goals.actionFailed'));
  }
}

async function archive() {
  try {
    await ElMessageBox.confirm(t('goals.archiveConfirmBody'), t('goals.archiveConfirmTitle'), {
      confirmButtonText: t('goals.archiveAction'), cancelButtonText: t('common.cancel'),
    });
  } catch {
    return;
  }
  try {
    goal.value = await archiveGoal(goal.value.id);
    ElMessage.success(t('goals.archived'));
  } catch (error) {
    ElMessage.error(error?.message || t('goals.archiveFailed'));
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm(t('goals.deleteConfirmBody'), t('goals.deleteConfirmTitle'), {
      confirmButtonText: t('goals.delete'), cancelButtonText: t('common.cancel'), type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await deleteGoal(goal.value.id);
    router.replace('/goals');
  } catch (error) {
    ElMessage.error(error?.message || t('goals.deleteFailed'));
  }
}

function openSession(session) {
  router.push(session.status === 'completed' ? `/practice/${session.id}/report` : `/practice/${session.id}/session`);
}

function dateLabel(value) {
  if (!value) return t('goals.noDate');
  return new Intl.DateTimeFormat(locale.value, { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    .format(new Date(value));
}
</script>

<template>
  <section v-loading="loading" class="goal-detail-page">
    <template v-if="goal">
      <div class="page-heading">
        <div>
          <button class="back-link" @click="router.push('/goals')"><el-icon><ArrowLeft /></el-icon>{{ t('goals.back') }}</button>
          <div class="title-row">
            <h1>{{ goal.company || t('goals.targetRole') }} · {{ goal.role }}</h1>
            <el-tag :class="goal.status === 'archived' ? 'tag-muted' : 'tag-ready'" size="small">
              {{ GOAL_STATUS_LABEL_KEYS[goal.status] ? t(GOAL_STATUS_LABEL_KEYS[goal.status]) : goal.status }}
            </el-tag>
          </div>
          <p>{{ STAGE_LABEL_KEYS[goal.stage] ? t(STAGE_LABEL_KEYS[goal.stage]) : goal.stage }} · <template v-if="goal.interviewAt">{{ dateLabel(goal.interviewAt) }} · </template>
            <strong class="countdown">{{ interviewLabel(goal.daysUntilInterview, t) }}</strong></p>
        </div>
        <div class="heading-actions">
          <el-button class="soft-button" @click="router.push(`/practice/setup?goalId=${goal.id}`)">{{ t('goals.createPractice') }}</el-button>
          <el-button class="soft-button" @click="router.push(`/goals/${goal.id}/edit`)">{{ t('goals.edit') }}</el-button>
          <el-button class="soft-button" @click="archive">{{ t('goals.archive') }}</el-button>
          <el-button class="delete-button" @click="remove">{{ t('goals.delete') }}</el-button>
        </div>
      </div>

      <div class="metric-grid">
        <article class="metric panel"><span>{{ t('goals.metricSessions') }}</span><strong>{{ goal.progress.sessions }}</strong></article>
        <article class="metric panel"><span>{{ t('goals.metricAnswers') }}</span><strong>{{ goal.progress.answers }}</strong></article>
        <article class="metric panel"><span>{{ t('goals.metricAverage') }}</span><strong>{{ goal.progress.averageScore ?? '—' }}</strong></article>
        <article class="metric panel">
          <span>{{ t('goals.metricPlan') }}</span><strong>{{ progressPercent }}<small>%</small></strong>
          <i class="metric-bar"><b :style="{ width: `${progressPercent}%` }"></b></i>
        </article>
      </div>

      <div class="detail-grid">
        <section class="panel today-panel">
          <div class="section-title">
            <span>{{ t('goals.todayTitle') }}</span>
            <small>{{ t('goals.todayProgress', { completed: completedToday, total: plan?.totalCount || 3, days: plan?.streak?.current || 0 }) }}</small>
          </div>
          <div class="plan-list">
            <div v-for="item in plan?.items || []" :key="item.id" class="plan-row" :class="item.status">
              <span class="plan-kind">{{ PLAN_KIND_LABEL_KEYS[item.kind] ? t(PLAN_KIND_LABEL_KEYS[item.kind]) : item.kind }}</span>
              <span class="plan-main"><strong>{{ item.title }}</strong><small>{{ t('goals.minutes', { minutes: item.estimatedMinutes }) }}</small></span>
              <el-button v-if="item.status !== 'completed' && item.status !== 'skipped'" class="soft-button"
                         :loading="starting === item.id" @click="start(item)">{{ t('goals.start') }}</el-button>
              <span v-else class="plan-done">{{ item.status === 'completed' ? t('goals.completed') : t('goals.skipped') }}</span>
              <button v-if="item.status === 'pending'" class="mini-link" @click="skip(item)">{{ t('goals.skip') }}</button>
            </div>
          </div>
        </section>

        <section class="panel dimension-panel">
          <div class="section-title"><span>{{ t('goals.dimensionsTitle') }}</span><small>{{ t('goals.dimensionsHint') }}</small></div>
          <div class="dimension-list">
            <div v-for="[key, label] in dimensions" :key="key" class="dimension-row">
              <span>{{ label }}</span>
              <i><b :style="{ width: `${goal.progress.dimensions[key] || 0}%` }"></b></i>
              <strong>{{ goal.progress.dimensions[key] ?? '—' }}</strong>
            </div>
          </div>
        </section>
      </div>

      <section class="panel sessions-panel">
        <div class="section-title"><span>{{ t('goals.goalSessions') }}</span><small>{{ t('goals.sessionCount', { count: sessions.length }) }}</small></div>
        <div v-if="sessions.length" class="session-list">
          <button v-for="session in sessions" :key="session.id" class="session-row" @click="openSession(session)">
            <span class="session-status" :class="session.status">{{ session.status === 'completed' ? t('goals.completed') : t('goals.inProgress') }}</span>
            <span class="session-main">{{ t('goals.sessionMeta', { count: session.questionCount, type: t(`setup.${session.interviewType}`) }) }}</span>
            <span class="session-date">{{ new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(new Date(session.createdAt)) }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <p v-else class="empty-copy">{{ t('goals.noSessions') }}</p>
      </section>
    </template>
  </section>
</template>

<style scoped>
.goal-detail-page { max-width: 1120px; }
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 7px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.back-link:hover { color: var(--green); }
.title-row { display: flex; align-items: center; gap: 10px; }
.title-row h1 { margin: 0; }
.countdown { color: #a9722f; }
.heading-actions { display: flex; gap: 8px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin-bottom: 16px; }
.metric { display: grid; gap: 5px; padding: 14px 16px; box-shadow: none; }
.metric span { color: #8d9792; font-size: 12px; }
.metric strong { color: #2f3c35; font-size: 24px; font-weight: 650; }
.metric strong small { font-size: 13px; }
.metric-bar { display: block; height: 6px; overflow: hidden; border-radius: 3px; background: #eef1ee; }
.metric-bar b { display: block; height: 100%; background: var(--green); }
.detail-grid { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 0.7fr); gap: 16px; }
.today-panel, .dimension-panel, .sessions-panel { padding: 17px 18px; box-shadow: none; }
.plan-list { display: grid; gap: 8px; }
.plan-row { display: flex; align-items: center; gap: 10px; padding: 10px 11px; border: 1px solid #eef1ee; border-radius: 8px; }
.plan-row.completed { background: #f5faf6; }
.plan-row.skipped { opacity: .55; }
.plan-kind { width: 64px; flex: none; color: #a9722f; font-size: 11px; font-weight: 600; }
.plan-main { display: grid; min-width: 0; flex: 1; gap: 3px; }
.plan-main strong { overflow: hidden; color: #46524c; font-size: 13px; font-weight: 550; text-overflow: ellipsis; white-space: nowrap; }
.plan-main small { color: #9aa39f; font-size: 11px; }
.plan-done { color: #5d8b6c; font-size: 12px; }
.mini-link { padding: 0; border: 0; background: transparent; color: #9aa39f; font-size: 11px; }
.dimension-list { display: grid; gap: 10px; }
.dimension-row { display: grid; grid-template-columns: 78px minmax(0, 1fr) 32px; align-items: center; gap: 8px; }
.dimension-row span { color: #5d6a64; font-size: 12px; }
.dimension-row i { display: block; height: 7px; overflow: hidden; border-radius: 4px; background: #eef1ee; }
.dimension-row b { display: block; height: 100%; background: var(--green); }
.dimension-row strong { color: #46524c; font-size: 12px; text-align: right; }
.sessions-panel { margin-top: 16px; }
.session-list { display: grid; }
.session-row { display: flex; align-items: center; gap: 12px; padding: 10px 2px; border: 0; border-bottom: 1px solid #eef1ee; background: transparent; text-align: left; }
.session-row:last-child { border-bottom: 0; }
.session-status { width: 56px; flex: none; color: #a9722f; font-size: 12px; }
.session-status.completed { color: #5d8b6c; }
.session-main { flex: 1; color: #46524c; font-size: 13px; }
.session-date { color: #98a29d; font-size: 12px; }
.empty-copy { margin: 8px 0 0; color: #8d9792; font-size: 13px; }
@media (max-width: 900px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .detail-grid { grid-template-columns: 1fr; }
}
</style>
