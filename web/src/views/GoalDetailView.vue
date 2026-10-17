<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import {
  archiveGoal, deleteGoal, getGoal, getTodayPlan, GOAL_STATUS_LABELS, goalProgressPercent,
  interviewLabel, listGoalSessions, PLAN_KIND_LABELS, STAGE_LABELS, startPlanItem, updatePlanItem,
} from '../services/goals';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const goal = ref(null);
const plan = ref(null);
const sessions = ref([]);
const loading = ref(true);
const starting = ref('');
const dimensions = [
  ['relevance', '内容相关性'],
  ['completeness', '回答完整度'],
  ['professionalism', '专业程度'],
  ['starStructure', 'STAR 结构'],
  ['logic', '回答逻辑'],
];

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
    ElMessage.error(error?.message || '无法读取目标');
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
    ElMessage.error(error?.message || '无法开始这一题');
  } finally {
    starting.value = '';
  }
}

async function skip(item) {
  try {
    await updatePlanItem(item.id, 'skipped');
    plan.value = await getTodayPlan(goal.value.id);
  } catch (error) {
    ElMessage.error(error?.message || '操作失败');
  }
}

async function archive() {
  try {
    await ElMessageBox.confirm('归档后不再出现在首页和今日计划，历史仍可查看。', '归档这个目标？', {
      confirmButtonText: '归档', cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  try {
    goal.value = await archiveGoal(goal.value.id);
    ElMessage.success('目标已归档');
  } catch (error) {
    ElMessage.error(error?.message || '归档失败');
  }
}

async function remove() {
  try {
    await ElMessageBox.confirm('删除目标会同时删除它的训练计划；练习记录会保留，但不再挂在这个目标上。', '删除目标？', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await deleteGoal(goal.value.id);
    router.replace('/goals');
  } catch (error) {
    ElMessage.error(error?.message || '删除失败');
  }
}

function openSession(session) {
  router.push(session.status === 'completed' ? `/practice/${session.id}/report` : `/practice/${session.id}/session`);
}

function dateLabel(value) {
  if (!value) return '未设日期';
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    .format(new Date(value));
}
</script>

<template>
  <section v-loading="loading" class="goal-detail-page">
    <template v-if="goal">
      <div class="page-heading">
        <div>
          <button class="back-link" @click="router.push('/goals')"><el-icon><ArrowLeft /></el-icon>求职目标</button>
          <div class="title-row">
            <h1>{{ goal.company || '目标岗位' }} · {{ goal.role }}</h1>
            <el-tag :class="goal.status === 'archived' ? 'tag-muted' : 'tag-ready'" size="small">
              {{ GOAL_STATUS_LABELS[goal.status] || goal.status }}
            </el-tag>
          </div>
          <p>{{ STAGE_LABELS[goal.stage] || goal.stage }} · <template v-if="goal.interviewAt">{{ dateLabel(goal.interviewAt) }} · </template>
            <strong class="countdown">{{ interviewLabel(goal.daysUntilInterview) }}</strong></p>
        </div>
        <div class="heading-actions">
          <el-button class="soft-button" @click="router.push(`/practice/setup?goalId=${goal.id}`)">创建练习</el-button>
          <el-button class="soft-button" @click="router.push(`/goals/${goal.id}/edit`)">编辑</el-button>
          <el-button class="soft-button" @click="archive">归档</el-button>
          <el-button class="delete-button" @click="remove">删除</el-button>
        </div>
      </div>

      <div class="metric-grid">
        <article class="metric panel"><span>练习场次</span><strong>{{ goal.progress.sessions }}</strong></article>
        <article class="metric panel"><span>已答题目</span><strong>{{ goal.progress.answers }}</strong></article>
        <article class="metric panel"><span>平均分</span><strong>{{ goal.progress.averageScore ?? '—' }}</strong></article>
        <article class="metric panel">
          <span>计划完成</span><strong>{{ progressPercent }}<small>%</small></strong>
          <i class="metric-bar"><b :style="{ width: `${progressPercent}%` }"></b></i>
        </article>
      </div>

      <div class="detail-grid">
        <section class="panel today-panel">
          <div class="section-title">
            <span>今天练什么</span>
            <small>{{ completedToday }}/{{ plan?.totalCount || 3 }} 完成 · 连续 {{ plan?.streak?.current || 0 }} 天</small>
          </div>
          <div class="plan-list">
            <div v-for="item in plan?.items || []" :key="item.id" class="plan-row" :class="item.status">
              <span class="plan-kind">{{ PLAN_KIND_LABELS[item.kind] || item.kind }}</span>
              <span class="plan-main"><strong>{{ item.title }}</strong><small>约 {{ item.estimatedMinutes }} 分钟</small></span>
              <el-button v-if="item.status !== 'completed' && item.status !== 'skipped'" class="soft-button"
                         :loading="starting === item.id" @click="start(item)">开始</el-button>
              <span v-else class="plan-done">{{ item.status === 'completed' ? '已完成' : '已跳过' }}</span>
              <button v-if="item.status === 'pending'" class="mini-link" @click="skip(item)">跳过</button>
            </div>
          </div>
        </section>

        <section class="panel dimension-panel">
          <div class="section-title"><span>维度平均</span><small>只统计挂在这个目标上的练习</small></div>
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
        <div class="section-title"><span>针对这个目标的练习</span><small>{{ sessions.length }} 场</small></div>
        <div v-if="sessions.length" class="session-list">
          <button v-for="session in sessions" :key="session.id" class="session-row" @click="openSession(session)">
            <span class="session-status" :class="session.status">{{ session.status === 'completed' ? '已完成' : '进行中' }}</span>
            <span class="session-main">{{ session.questionCount }} 题 · {{ session.interviewType }}</span>
            <span class="session-date">{{ new Date(session.createdAt).toLocaleDateString('zh-CN') }}</span>
            <el-icon><ArrowRight /></el-icon>
          </button>
        </div>
        <p v-else class="empty-copy">还没有针对这个目标的练习。从上面的"今天练什么"开始。</p>
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
