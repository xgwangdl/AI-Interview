<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  GOAL_STATUS_LABELS, STAGE_LABELS, goalProgressPercent, interviewLabel, listGoals,
} from '../services/goals';

const router = useRouter();
const goals = ref([]);
const loading = ref(true);
const showArchived = ref(false);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    goals.value = await listGoals();
  } catch (error) {
    ElMessage.error(error?.message || '无法读取求职目标');
  } finally {
    loading.value = false;
  }
}

function visible(goal) {
  return showArchived.value || goal.status !== 'archived';
}

function dateLabel(value) {
  if (!value) return '未设日期';
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })
    .format(new Date(value));
}
</script>

<template>
  <section v-loading="loading" class="goals-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">JOB GOALS</div>
        <h1>求职目标</h1>
        <p>一个岗位一个目标：JD、简历、轮次和面试日期。练习会回挂到对应目标上。</p>
      </div>
      <el-button class="primary-button" @click="router.push('/goals/new')">
        <el-icon><Plus /></el-icon> 添加目标
      </el-button>
    </div>

    <div class="goal-toolbar">
      <el-switch v-model="showArchived" active-text="显示已归档" />
    </div>

    <div v-if="goals.filter(visible).length" class="goal-grid">
      <button v-for="goal in goals.filter(visible)" :key="goal.id" class="goal-card panel"
              @click="router.push(`/goals/${goal.id}`)">
        <div class="goal-top">
          <span class="goal-company">{{ goal.company || '目标岗位' }}</span>
          <el-tag :class="goal.status === 'archived' ? 'tag-muted' : 'tag-ready'" size="small">
            {{ GOAL_STATUS_LABELS[goal.status] || goal.status }}
          </el-tag>
        </div>
        <strong class="goal-role">{{ goal.role }}</strong>
        <div class="goal-meta">
          <span>{{ STAGE_LABELS[goal.stage] || goal.stage }}</span>
          <span>{{ dateLabel(goal.interviewAt) }}</span>
        </div>
        <div class="goal-countdown" :class="{ urgent: goal.daysUntilInterview != null && goal.daysUntilInterview <= 3 }">
          {{ interviewLabel(goal.daysUntilInterview) }}
        </div>
        <div class="goal-progress">
          <span><i :style="{ width: `${goalProgressPercent(goal.progress)}%` }"></i></span>
          <small>{{ goal.progress?.planCompleted || 0 }}/{{ goal.progress?.planItems || 0 }} 训练项</small>
        </div>
        <div class="goal-stats">
          <span>{{ goal.progress?.sessions || 0 }} 场练习</span>
          <span>{{ goal.progress?.averageScore != null ? goal.progress.averageScore + ' 分' : '还没有分数' }}</span>
        </div>
      </button>
    </div>

    <div v-else class="panel empty-goals">
      <el-icon><Aim /></el-icon>
      <strong>还没有求职目标</strong>
      <p>先添加一个目标岗位和面试日期，系统才能生成"今天练什么"。</p>
      <el-button class="primary-button" @click="router.push('/goals/new')">添加第一个目标</el-button>
    </div>
  </section>
</template>

<style scoped>
.goals-page { max-width: 1120px; }
.goal-toolbar { display: flex; justify-content: flex-end; margin: -6px 0 13px; }
.goal-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 15px; }
.goal-card { display: grid; gap: 8px; padding: 17px 18px 15px; border: 1px solid var(--line); text-align: left; box-shadow: none; }
.goal-card:hover { border-color: #b9d3c2; background: #fbfdfb; }
.goal-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.goal-company { color: #7d8982; font-size: 12px; }
.goal-role { color: #2f3c35; font-size: 17px; font-weight: 650; }
.goal-meta { display: flex; flex-wrap: wrap; gap: 10px; color: #8d9792; font-size: 12px; }
.goal-countdown { color: #a9722f; font-size: 13px; font-weight: 600; }
.goal-countdown.urgent { color: #b4553f; }
.goal-progress { display: grid; gap: 5px; margin-top: 2px; }
.goal-progress > span { display: block; height: 6px; overflow: hidden; border-radius: 3px; background: #eef1ee; }
.goal-progress i { display: block; height: 100%; background: var(--green); }
.goal-progress small { color: #98a29d; font-size: 11px; }
.goal-stats { display: flex; justify-content: space-between; color: #7d8982; font-size: 12px; }
.empty-goals { display: grid; justify-items: center; gap: 9px; padding: 42px 20px; text-align: center; box-shadow: none; }
.empty-goals .el-icon { color: #8d9792; font-size: 26px; }
.empty-goals strong { color: #46524c; font-size: 15px; }
.empty-goals p { max-width: 420px; margin: 0; color: #8d9792; font-size: 13px; line-height: 1.7; }
@media (max-width: 900px) { .goal-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) { .goal-grid { grid-template-columns: 1fr; } }
</style>
