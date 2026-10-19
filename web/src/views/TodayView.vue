<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getTodayPlan, PLAN_KIND_LABEL_KEYS, startPlanItem, updatePlanItem } from '../services/goals';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
const practice = usePracticeStore();
const plan = ref(null);
const loading = ref(true);
const starting = ref('');

const completed = computed(() => plan.value?.completedCount || 0);
const progress = computed(() => {
  if (!plan.value?.totalCount) return 0;
  return Math.round((completed.value / plan.value.totalCount) * 100);
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    plan.value = await getTodayPlan();
  } catch (error) {
    ElMessage.error(error?.message || t('today.loadFailed'));
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
    ElMessage.error(error?.message || t('today.startFailed'));
  } finally {
    starting.value = '';
  }
}

async function skip(item) {
  try {
    await updatePlanItem(item.id, 'skipped');
    await load();
  } catch (error) {
    ElMessage.error(error?.message || t('today.actionFailed'));
  }
}
</script>

<template>
  <section v-loading="loading" class="today-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('today.eyebrow') }}</div>
        <h1>{{ t('today.title') }}</h1>
        <p v-if="plan">{{ t('today.subtitle', { goal: plan.goalTitle, days: plan.streak?.current || 0 }) }}</p>
      </div>
      <el-button v-if="plan?.goalId" class="soft-button" @click="router.push(`/goals/${plan.goalId}`)">
        {{ t('today.viewGoal') }}
      </el-button>
    </div>

    <div class="progress-panel panel">
      <div class="progress-copy">
        <strong>{{ completed }}/{{ plan?.totalCount || 3 }}</strong>
        <span>{{ completed >= 2 ? t('today.goalMet') : t('today.goalRule') }}</span>
      </div>
      <span class="progress-bar"><i :style="{ width: `${progress}%` }"></i></span>
    </div>

    <div class="panel plan-list">
      <div v-for="(item, index) in plan?.items || []" :key="item.id" class="plan-row" :class="item.status">
        <span class="plan-number">{{ index + 1 }}</span>
        <span class="plan-main">
          <small>{{ t('today.itemMeta', {
            kind: PLAN_KIND_LABEL_KEYS[item.kind] ? t(PLAN_KIND_LABEL_KEYS[item.kind]) : item.kind,
            minutes: item.estimatedMinutes,
          }) }}</small>
          <strong>{{ item.title }}</strong>
        </span>
        <el-button v-if="item.status === 'pending' || item.status === 'in_progress'" class="soft-button"
                   :loading="starting === item.id" @click="start(item)">
          {{ item.status === 'in_progress' ? t('today.resume') : t('today.start') }}
        </el-button>
        <span v-else class="done-label">{{ item.status === 'completed' ? t('today.completed') : t('today.skipped') }}</span>
        <button v-if="item.status === 'pending'" class="skip-link" @click="skip(item)">{{ t('today.skip') }}</button>
      </div>
    </div>

    <p class="today-note">{{ t('today.note') }}</p>
  </section>
</template>

<style scoped>
.today-page { max-width: 820px; }
.progress-panel { display: grid; gap: 10px; padding: 16px 18px; margin-bottom: 14px; box-shadow: none; }
.progress-copy { display: flex; align-items: baseline; gap: 10px; }
.progress-copy strong { color: #2f3c35; font-size: 22px; }
.progress-copy span { color: #8d9792; font-size: 13px; }
.progress-bar { display: block; height: 8px; overflow: hidden; border-radius: 4px; background: #eef1ee; }
.progress-bar i { display: block; height: 100%; background: var(--green); transition: width .25s ease; }
.plan-list { padding: 6px 0; box-shadow: none; }
.plan-row { display: flex; align-items: center; gap: 12px; padding: 13px 16px; border-bottom: 1px solid #eef1ee; }
.plan-row:last-child { border-bottom: 0; }
.plan-row.completed { background: #f5faf6; }
.plan-row.skipped { opacity: .55; }
.plan-number { display: grid; width: 26px; height: 26px; flex: none; place-items: center; border-radius: 50%; background: #f1f4f1; color: #7d8982; font-size: 12px; }
.plan-main { display: grid; min-width: 0; flex: 1; gap: 4px; }
.plan-main small { color: #a9722f; font-size: 11px; }
.plan-main strong { color: #46524c; font-size: 14px; font-weight: 550; line-height: 1.5; }
.done-label { color: #5d8b6c; font-size: 12px; }
.skip-link { padding: 0; border: 0; background: transparent; color: #9aa39f; font-size: 11px; }
.today-note { margin: 12px 0 0; color: #98a29d; font-size: 12px; }
</style>
