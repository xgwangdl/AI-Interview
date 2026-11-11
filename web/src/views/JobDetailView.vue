<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import ScoreRing from '../components/ScoreRing.vue';
import {
  createPracticeFromJob, deleteJobLead, evaluateJobLead, getJobLead, isRunActive, patchJobLead,
} from '../services/jobRadar';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const job = ref(null);
const loading = ref(true);
const evaluating = ref(false);
const practiceOpen = ref(false);
const creatingPractice = ref(false);
const practiceForm = reactive({ questionCount: 3, difficulty: 'standard', interviewerStyle: 'balanced' });
let pollTimer = null;

const transitions = {
  new: ['skipped', 'archived'], shortlisted: ['applied', 'skipped', 'archived'],
  applied: ['interviewing', 'rejected', 'archived'], interviewing: ['offer', 'rejected', 'archived'],
  offer: ['archived'], rejected: ['archived'], skipped: ['archived'], evaluating: ['archived'], archived: [],
};
const breakdown = computed(() => {
  const value = job.value?.evaluation?.breakdown;
  if (!value) return [];
  return ['skill', 'seniority', 'salary', 'location', 'freshness'].map(key => ({
    key, label: t(`jobRadar.breakdown.${key}`), value: Math.round(Number(value[key] || 0) * 100),
  }));
});

onMounted(load);
onUnmounted(() => clearTimeout(pollTimer));

async function load() {
  clearTimeout(pollTimer);
  loading.value = !job.value;
  try {
    job.value = await getJobLead(route.params.id);
    if (isRunActive(job.value.evaluationRunStatus)) pollTimer = setTimeout(load, 4000);
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.detail.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function reevaluate() {
  evaluating.value = true;
  try {
    await evaluateJobLead(route.params.id, Boolean(job.value?.evaluation));
    ElMessage.success(t('jobRadar.detail.evaluating'));
    await load();
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.detail.evaluateFailed'));
  } finally {
    evaluating.value = false;
  }
}

async function changeStatus(status) {
  try {
    job.value = await patchJobLead(route.params.id, { status });
    ElMessage.success(t('jobRadar.detail.statusUpdated'));
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.detail.statusFailed'));
  }
}

async function createPractice() {
  creatingPractice.value = true;
  try {
    const result = await createPracticeFromJob(route.params.id, practiceForm);
    practiceOpen.value = false;
    router.push(`/practice/device-check?id=${result.sessionId}`);
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.detail.practiceFailed'));
  } finally {
    creatingPractice.value = false;
  }
}

async function removeJob() {
  try {
    await ElMessageBox.confirm(t('jobRadar.detail.deleteConfirm'), t('jobRadar.detail.deleteTitle'), {
      confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), type: 'warning',
    });
    await deleteJobLead(route.params.id);
    ElMessage.success(t('jobRadar.detail.deleted'));
    router.replace('/jobs');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || t('jobRadar.detail.deleteFailed'));
  }
}

function salary() {
  if (job.value?.salaryMinK == null && job.value?.salaryMaxK == null) return t('jobRadar.notSet');
  return `${job.value.salaryMinK ?? '?'}-${job.value.salaryMaxK ?? '?'}K`;
}

function recommendation() {
  const value = job.value?.evaluation?.recommendation;
  return value ? t(`jobRadar.recommendation.${value}`) : t('jobRadar.pending');
}
</script>

<template>
  <section v-loading="loading" class="job-detail-page">
    <template v-if="job">
      <div class="page-heading">
        <div>
          <button class="text-back" @click="router.push('/jobs')"><el-icon><ArrowLeft /></el-icon>{{ t('jobRadar.backToJobs') }}</button>
          <h1>{{ job.role }}</h1>
          <p>{{ job.company || t('jobRadar.companyUnknown') }}<template v-if="job.city"> · {{ job.city }}</template> · {{ salary() }}</p>
        </div>
        <div class="heading-actions">
          <el-dropdown v-if="transitions[job.status]?.length" trigger="click" @command="changeStatus">
            <el-button class="soft-button"><el-icon><Switch /></el-icon>{{ t('jobRadar.detail.changeStatus') }}</el-button>
            <template #dropdown><el-dropdown-menu><el-dropdown-item v-for="status in transitions[job.status]" :key="status" :command="status">{{ t(`jobRadar.status.${status}`) }}</el-dropdown-item></el-dropdown-menu></template>
          </el-dropdown>
          <el-button class="soft-button" :loading="evaluating" :disabled="evaluating || isRunActive(job.evaluationRunStatus)" @click="reevaluate"><el-icon><Refresh /></el-icon>{{ t('jobRadar.detail.reevaluate') }}</el-button>
          <el-button class="primary-button" @click="practiceOpen = true"><el-icon><VideoPlay /></el-icon>{{ t('jobRadar.detail.practice') }}</el-button>
        </div>
      </div>

      <div class="score-layout">
        <section class="panel score-card">
          <ScoreRing :score="job.evaluation?.score" :size="132" :label="t('jobRadar.detail.score')" />
          <div class="score-copy">
            <el-tag size="small" type="success">{{ t(`jobRadar.status.${job.status}`) }}</el-tag>
            <h2>{{ recommendation() }}</h2>
            <p>{{ job.evaluation?.reason || t('jobRadar.detail.noEvaluation') }}</p>
            <span v-if="job.evaluation?.degraded" class="degraded-tag"><el-icon><Warning /></el-icon>{{ t('jobRadar.detail.degraded') }}</span>
          </div>
          <div v-if="isRunActive(job.evaluationRunStatus)" class="run-state"><el-icon class="is-loading"><Loading /></el-icon>{{ t(`jobRadar.runStatus.${job.evaluationRunStatus}`) }}</div>
        </section>

        <section class="panel breakdown-panel">
          <div class="section-title">{{ t('jobRadar.detail.breakdown') }}<small>{{ t('jobRadar.detail.deterministic') }}</small></div>
          <div v-for="item in breakdown" :key="item.key" class="breakdown-row">
            <span>{{ item.label }}</span><div><i :style="{ width: `${item.value}%` }"></i></div><strong>{{ item.value }}</strong>
          </div>
          <p v-if="!breakdown.length" class="panel-empty">{{ t('jobRadar.detail.waiting') }}</p>
        </section>
      </div>

      <div class="skills-grid">
        <section class="panel skill-list-card">
          <div class="section-title">{{ t('jobRadar.detail.hits') }}<small>{{ job.evaluation?.hits?.length || 0 }}</small></div>
          <div v-for="hit in job.evaluation?.hits || []" :key="hit.name" class="match-row">
            <span><strong>{{ hit.name }}</strong><small>{{ t('jobRadar.detail.levelPair', { user: hit.userLevel, required: hit.requiredLevel }) }}</small></span>
            <el-progress :percentage="Math.round(Number(hit.levelMatch || 0) * 100)" :show-text="false" :stroke-width="6" />
          </div>
          <p v-if="!job.evaluation?.hits?.length" class="panel-empty">{{ t('jobRadar.detail.noData') }}</p>
        </section>
        <section class="panel skill-list-card">
          <div class="section-title">{{ t('jobRadar.detail.gaps') }}<small>{{ job.evaluation?.gaps?.length || 0 }}</small></div>
          <div v-for="gap in job.evaluation?.gaps || []" :key="gap.name" class="gap-row">
            <span><strong>{{ gap.name }}</strong><small>{{ t('jobRadar.detail.levelPair', { user: gap.userLevel, required: gap.requiredLevel }) }}</small></span>
            <el-tag size="small" type="warning">{{ t('jobRadar.detail.priority', { weight: Number(gap.weight).toFixed(1) }) }}</el-tag>
          </div>
          <p v-if="!job.evaluation?.gaps?.length" class="panel-empty">{{ t('jobRadar.detail.noGaps') }}</p>
        </section>
      </div>

      <details class="jd-card panel"><summary>{{ t('jobRadar.detail.jd') }}</summary><pre>{{ job.jdText }}</pre></details>
      <div class="detail-footer"><span>{{ t('jobRadar.detail.createdAt', { date: new Date(job.createdAt).toLocaleDateString() }) }}</span><el-button text type="danger" @click="removeJob"><el-icon><Delete /></el-icon>{{ t('jobRadar.detail.delete') }}</el-button></div>
    </template>

    <el-dialog v-model="practiceOpen" :title="t('jobRadar.practice.title')" width="min(520px, 94vw)">
      <p class="practice-hint">{{ t('jobRadar.practice.hint') }}</p>
      <div class="practice-form">
        <label>{{ t('jobRadar.practice.count') }}<el-input-number v-model="practiceForm.questionCount" :min="1" :max="5" controls-position="right" /></label>
        <label>{{ t('jobRadar.practice.difficulty') }}<el-select v-model="practiceForm.difficulty"><el-option v-for="value in ['foundation', 'standard', 'advanced']" :key="value" :value="value" :label="t(`jobRadar.practice.difficulty${value[0].toUpperCase()}${value.slice(1)}`)" /></el-select></label>
        <label>{{ t('jobRadar.practice.style') }}<el-select v-model="practiceForm.interviewerStyle"><el-option v-for="value in ['balanced', 'direct', 'supportive']" :key="value" :value="value" :label="t(`jobRadar.practice.style${value[0].toUpperCase()}${value.slice(1)}`)" /></el-select></label>
      </div>
      <template #footer><el-button class="soft-button" @click="practiceOpen = false">{{ t('common.cancel') }}</el-button><el-button class="primary-button" :loading="creatingPractice" :disabled="creatingPractice" @click="createPractice">{{ t('jobRadar.practice.start') }}</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.job-detail-page { max-width: 1140px; min-height: 420px; }
.text-back { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 8px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.score-layout { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(300px, .85fr); gap: 18px; margin-bottom: 18px; }
.score-card { position: relative; display: flex; gap: 24px; align-items: center; min-height: 210px; padding: 25px; }
.score-copy h2 { margin: 10px 0 8px; color: #2f3b35; font-size: 22px; }
.score-copy p { max-width: 480px; margin: 0; color: #75817b; font-size: 13px; line-height: 1.75; }
.degraded-tag { display: inline-flex; align-items: center; gap: 5px; margin-top: 11px; color: #aa762c; font-size: 12px; }
.run-state { position: absolute; right: 18px; top: 15px; display: flex; align-items: center; gap: 6px; color: var(--blue); font-size: 12px; }
.breakdown-panel, .skill-list-card { padding: 20px; }
.breakdown-row { display: grid; grid-template-columns: 76px minmax(0, 1fr) 34px; gap: 10px; align-items: center; margin: 16px 0; color: #68746e; font-size: 13px; }
.breakdown-row > div { height: 7px; overflow: hidden; border-radius: 9px; background: #edf1ee; }
.breakdown-row i { display: block; height: 100%; border-radius: inherit; background: var(--green); }
.breakdown-row strong { text-align: right; color: #435048; }
.skills-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.match-row, .gap-row { display: grid; grid-template-columns: minmax(120px, .8fr) minmax(90px, 1fr); gap: 12px; align-items: center; padding: 11px 0; border-top: 1px solid #edf0ed; }
.gap-row { grid-template-columns: minmax(0, 1fr) auto; }
.match-row span, .gap-row span { display: flex; min-width: 0; flex-direction: column; gap: 4px; }
.match-row strong, .gap-row strong { overflow: hidden; color: #46524c; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.match-row small, .gap-row small { color: #929b97; font-size: 11px; }
.panel-empty { margin: 20px 0; color: #99a29e; font-size: 13px; text-align: center; }
.jd-card { margin-top: 18px; padding: 15px 18px; color: #65716b; font-size: 13px; }
.jd-card summary { cursor: pointer; font-weight: 600; }
.jd-card pre { margin: 14px 0 0; overflow: auto; color: #56635c; font-family: inherit; font-size: 13px; line-height: 1.75; white-space: pre-wrap; }
.detail-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; color: #9ba49f; font-size: 11px; }
.practice-hint { margin: 0 0 17px; color: #7b8680; font-size: 13px; line-height: 1.7; }
.practice-form { display: grid; gap: 14px; }
.practice-form label { display: grid; gap: 7px; color: #606c66; font-size: 13px; }
:deep(.el-input-number), :deep(.el-select) { width: 100%; }
@media (max-width: 850px) { .score-layout, .skills-grid { grid-template-columns: 1fr; } }
@media (max-width: 560px) {
  .heading-actions { width: 100%; }
  .heading-actions .el-button, .heading-actions .el-dropdown { flex: 1; }
  .score-card { align-items: flex-start; flex-direction: column; padding: 20px; }
}
</style>
