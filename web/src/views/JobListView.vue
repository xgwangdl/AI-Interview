<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import ScoreRing from '../components/ScoreRing.vue';
import {
  createJobLead, getWorkReport, JOB_STATUSES, listJobLeads,
} from '../services/jobRadar';

const router = useRouter();
const { t } = useI18n();
const PAGE_SIZE = 20;
const loading = ref(true);
const creating = ref(false);
const page = ref(1);
const total = ref(0);
const items = ref([]);
const statusCounts = ref({});
const report = ref(null);
const dialogOpen = ref(false);
const filters = reactive({ status: '', role: '' });
const form = reactive({
  company: '', role: '', city: '', salaryMinK: null, salaryMaxK: null, sourceUrl: '', jdText: '',
});

const statusOptions = computed(() => [
  { value: '', label: t('jobRadar.status.all'), count: Object.values(statusCounts.value).reduce((sum, n) => sum + Number(n || 0), 0) },
  ...JOB_STATUSES.filter((status) => status !== 'archived').map((status) => ({
    value: status, label: t(`jobRadar.status.${status}`), count: statusCounts.value[status] || 0,
  })),
]);

const reportMetrics = computed(() => report.value ? [
  { value: report.value.jobsEvaluated, label: t('jobRadar.report.jobs') },
  { value: report.value.practiceFromJd, label: t('jobRadar.report.practices') },
  { value: report.value.debriefs, label: t('jobRadar.report.debriefs') },
  { value: `${report.value.minutesSaved}m`, label: t('jobRadar.report.saved') },
] : []);

onMounted(loadAll);

async function loadAll() {
  await Promise.all([loadJobs(), loadReport()]);
}

async function loadJobs() {
  loading.value = true;
  try {
    const data = await listJobLeads({
      status: filters.status, role: filters.role, offset: (page.value - 1) * PAGE_SIZE, limit: PAGE_SIZE,
    });
    items.value = data.items || [];
    total.value = data.total || 0;
    statusCounts.value = data.statusCounts || {};
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function loadReport() {
  try {
    report.value = await getWorkReport('week');
  } catch {
    report.value = null;
  }
}

function setStatus(status) {
  filters.status = status;
  page.value = 1;
  void loadJobs();
}

function searchRole() {
  page.value = 1;
  void loadJobs();
}

function changePage(nextPage) {
  page.value = nextPage;
  void loadJobs();
}

function openDialog() {
  Object.assign(form, { company: '', role: '', city: '', salaryMinK: null, salaryMaxK: null, sourceUrl: '', jdText: '' });
  dialogOpen.value = true;
}

async function submitJob() {
  if (!form.role.trim()) return ElMessage.warning(t('jobRadar.validation.role'));
  if (form.jdText.trim().length < 80) return ElMessage.warning(t('jobRadar.validation.jd'));
  if (form.salaryMinK != null && form.salaryMaxK != null && form.salaryMinK > form.salaryMaxK) {
    return ElMessage.warning(t('jobRadar.validation.salary'));
  }
  creating.value = true;
  try {
    const result = await createJobLead({
      ...form, company: form.company.trim() || null, role: form.role.trim(), city: form.city.trim() || null,
      sourceUrl: form.sourceUrl.trim() || null, jdText: form.jdText.trim(),
    });
    dialogOpen.value = false;
    ElMessage.success(result.duplicated ? t('jobRadar.duplicate') : t('jobRadar.created'));
    await loadAll();
    router.push(`/jobs/${result.leadId}`);
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.createFailed'));
  } finally {
    creating.value = false;
  }
}

function salary(row) {
  if (row.salaryMinK == null && row.salaryMaxK == null) return t('jobRadar.notSet');
  return `${row.salaryMinK ?? '?'}-${row.salaryMaxK ?? '?'}K`;
}

function recommendation(row) {
  return row.latestRecommendation ? t(`jobRadar.recommendation.${row.latestRecommendation}`) : t('jobRadar.pending');
}
</script>

<template>
  <section class="job-page">
    <div class="page-heading">
      <div>
        <span class="eyebrow">{{ t('jobRadar.eyebrow') }}</span>
        <h1>{{ t('jobRadar.title') }}</h1>
        <p>{{ t('jobRadar.subtitle') }}</p>
      </div>
      <div class="heading-actions">
        <el-button class="soft-button" @click="router.push('/jobs/profile')"><el-icon><User /></el-icon>{{ t('jobRadar.profile') }}</el-button>
        <el-button class="soft-button" @click="router.push('/debrief')"><el-icon><ChatLineSquare /></el-icon>{{ t('jobRadar.debrief') }}</el-button>
        <el-button class="primary-button" @click="openDialog"><el-icon><Plus /></el-icon>{{ t('jobRadar.add') }}</el-button>
      </div>
    </div>

    <div v-if="report" class="weekly-strip panel">
      <div class="weekly-copy">
        <span>{{ t('jobRadar.report.title') }}</span>
        <small>{{ t('jobRadar.report.range', { from: report.from, to: report.to }) }}</small>
      </div>
      <div v-for="metric in reportMetrics" :key="metric.label" class="weekly-metric">
        <strong>{{ metric.value }}</strong><small>{{ metric.label }}</small>
      </div>
      <p v-if="report.suggestions?.length" class="weekly-tip">{{ report.suggestions[0] }}</p>
    </div>

    <div class="job-toolbar">
      <div class="status-tabs">
        <button v-for="option in statusOptions" :key="option.value" :class="{ active: filters.status === option.value }" @click="setStatus(option.value)">
          {{ option.label }}<small>{{ option.count }}</small>
        </button>
      </div>
      <el-input v-model="filters.role" clearable :placeholder="t('jobRadar.searchRole')" @keyup.enter="searchRole" @clear="searchRole">
        <template #append><el-button :aria-label="t('jobRadar.search')" @click="searchRole"><el-icon><Search /></el-icon></el-button></template>
      </el-input>
    </div>

    <div v-loading="loading" class="panel job-list">
      <button v-for="row in items" :key="row.id" class="job-row" @click="router.push(`/jobs/${row.id}`)">
        <ScoreRing :score="row.latestScore" :size="68" />
        <span class="job-main">
          <span class="job-title"><strong>{{ row.role }}</strong><el-tag size="small" :type="row.status === 'archived' ? 'info' : 'success'">{{ t(`jobRadar.status.${row.status}`) }}</el-tag></span>
          <small>{{ row.company || t('jobRadar.companyUnknown') }}<template v-if="row.city"> · {{ row.city }}</template> · {{ salary(row) }}</small>
          <span class="job-recommendation">{{ recommendation(row) }}</span>
        </span>
        <span class="job-open"><el-icon><ArrowRight /></el-icon></span>
      </button>
      <div v-if="!loading && !items.length" class="empty-state">
        <span class="empty-state-icon"><el-icon><Aim /></el-icon></span>
        <strong>{{ t('jobRadar.empty') }}</strong><p>{{ t('jobRadar.emptyHint') }}</p>
        <el-button class="primary-button" @click="openDialog">{{ t('jobRadar.addFirst') }}</el-button>
      </div>
    </div>

    <div v-if="total > PAGE_SIZE" class="job-pager">
      <el-pagination background layout="prev, pager, next" :current-page="page" :page-size="PAGE_SIZE" :total="total" @current-change="changePage" />
    </div>

    <el-dialog v-model="dialogOpen" :title="t('jobRadar.add')" width="min(720px, 94vw)">
      <div class="job-form-grid">
        <label>{{ t('jobRadar.form.role') }}<el-input v-model="form.role" maxlength="120" /></label>
        <label>{{ t('jobRadar.form.company') }}<el-input v-model="form.company" maxlength="120" /></label>
        <label>{{ t('jobRadar.form.city') }}<el-input v-model="form.city" maxlength="60" /></label>
        <label>{{ t('jobRadar.form.sourceUrl') }}<el-input v-model="form.sourceUrl" maxlength="500" /></label>
        <label>{{ t('jobRadar.form.salaryMin') }}<el-input-number v-model="form.salaryMinK" :min="0" :max="500" controls-position="right" /></label>
        <label>{{ t('jobRadar.form.salaryMax') }}<el-input-number v-model="form.salaryMaxK" :min="0" :max="500" controls-position="right" /></label>
      </div>
      <label class="job-jd-field">{{ t('jobRadar.form.jd') }}<el-input v-model="form.jdText" type="textarea" :rows="12" maxlength="20000" show-word-limit /></label>
      <template #footer>
        <el-button class="soft-button" @click="dialogOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button class="primary-button" :loading="creating" :disabled="creating" @click="submitJob">{{ t('jobRadar.startEvaluate') }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.job-page { max-width: 1140px; }
.heading-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.weekly-strip { display: grid; grid-template-columns: minmax(180px, 1.4fr) repeat(4, minmax(72px, .5fr)); gap: 18px; margin-bottom: 20px; padding: 17px 20px; }
.weekly-copy span, .weekly-copy small { display: block; }
.weekly-copy span { color: #35413b; font-size: 14px; font-weight: 650; }
.weekly-copy small, .weekly-metric small { margin-top: 4px; color: #929b97; font-size: 12px; }
.weekly-metric strong { color: var(--green-dark); font-size: 20px; }
.weekly-metric small { display: block; }
.weekly-tip { grid-column: 1 / -1; margin: 0; padding-top: 11px; border-top: 1px solid #edf0ed; color: #6f7a74; font-size: 13px; }
.job-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-bottom: 12px; }
.status-tabs { display: flex; gap: 4px; overflow-x: auto; }
.status-tabs button { display: flex; align-items: center; gap: 6px; padding: 8px 10px; border: 0; border-radius: 5px; background: transparent; color: #77827c; font-size: 14px; white-space: nowrap; }
.status-tabs button small { color: #a2aaa6; font-size: 11px; }
.status-tabs button.active { background: var(--green-soft); color: var(--green-dark); font-weight: 650; }
.job-toolbar .el-input { width: 240px; flex: none; }
.job-list { min-height: 180px; overflow: hidden; }
.job-row { display: grid; width: 100%; grid-template-columns: 80px minmax(0, 1fr) 30px; align-items: center; gap: 16px; padding: 16px 20px; border: 0; border-bottom: 1px solid #edf0ed; background: #fff; text-align: left; }
.job-row:last-child { border-bottom: 0; }
.job-row:hover { background: #fafcf9; }
.job-main, .job-title { display: flex; min-width: 0; }
.job-main { flex-direction: column; gap: 6px; }
.job-title { align-items: center; gap: 9px; }
.job-title strong { overflow: hidden; color: #2f3b35; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.job-main > small { color: #7e8983; font-size: 13px; }
.job-recommendation { color: var(--green); font-size: 13px; font-weight: 600; }
.job-open { color: #a3aca7; }
.job-pager { display: flex; justify-content: flex-end; margin-top: 15px; }
.job-form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }
.job-form-grid label, .job-jd-field { display: grid; gap: 7px; color: #5f6b65; font-size: 13px; }
.job-jd-field { margin-top: 16px; }
:deep(.el-input-number) { width: 100%; }
@media (max-width: 860px) {
  .weekly-strip { grid-template-columns: repeat(2, 1fr); }
  .weekly-copy, .weekly-tip { grid-column: 1 / -1; }
  .job-toolbar { align-items: stretch; flex-direction: column; }
  .job-toolbar .el-input { width: 100%; }
}
@media (max-width: 560px) {
  .heading-actions { width: 100%; }
  .heading-actions .el-button { flex: 1; margin-left: 0; }
  .job-row { grid-template-columns: 60px minmax(0, 1fr); padding: 14px; }
  .job-open { display: none; }
  .job-form-grid { grid-template-columns: 1fr; }
}
</style>
