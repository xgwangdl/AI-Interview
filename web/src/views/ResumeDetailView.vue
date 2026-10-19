<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  applyOptimization, deleteResume, downloadResume, getResume, optimizeResume, updateResume,
  RESUME_EXPORT_FORMATS,
} from '../services/resume';
import { useI18n } from 'vue-i18n';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const optimizing = ref(false);
const applying = ref(false);
const exporting = ref(false);
const resume = ref(null);
const optimization = ref(null);
const selected = ref([]);

const form = reactive({ title: '', rawText: '' });
const job = reactive({ role: '', jobDescription: '' });

const placeholderPattern = /【[^】]{1,60}】/g;

/** 把文本切成"普通片段 / 占位符片段"，用于高亮待补充内容（不用 v-html，避免注入）。 */
function segmentsOf(text) {
  if (!text) return [];
  const parts = [];
  let last = 0;
  for (const match of text.matchAll(placeholderPattern)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index), placeholder: false });
    parts.push({ text: match[0], placeholder: true });
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), placeholder: false });
  return parts;
}

const sections = computed(() => optimization.value?.sections || []);
const pendingCount = computed(() => optimization.value?.placeholders?.length || 0);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const data = await getResume(route.params.id);
    resume.value = data;
    form.title = data.title;
    form.rawText = data.rawText;
    // 从「从零创建」保存并勾了"顺手优化"过来的：把目标岗位填好并把优化面板滚到眼前
    if (route.query.optimize) {
      job.role = String(route.query.role || '');
      await nextTick();
      document.querySelector('.optimize-panel')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  } catch (error) {
    ElMessage.error(error.message || t('resume.loadDetailFailed'));
    router.replace('/resumes');
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!form.title.trim() || !form.rawText.trim()) {
    ElMessage.warning(t('resume.titleBodyRequired'));
    return;
  }
  saving.value = true;
  try {
    resume.value = await updateResume(resume.value.id, {
      title: form.title.trim(), source: resume.value.source,
      fileName: resume.value.fileName, rawText: form.rawText,
    });
    form.rawText = resume.value.rawText;
    ElMessage.success(t('resume.saved', { version: resume.value.version }));
  } catch (error) {
    ElMessage.error(error.message || t('resume.saveFailed'));
  } finally {
    saving.value = false;
  }
}

async function optimize() {
  if (!job.jobDescription.trim()) {
    ElMessage.warning(t('resume.jdRequired'));
    return;
  }
  optimizing.value = true;
  optimization.value = null;
  selected.value = [];
  try {
    const result = await optimizeResume(resume.value.id, job.role.trim(), job.jobDescription.trim());
    optimization.value = result;
    if (result.status !== 'completed') {
      ElMessage.error(result.error || t('resume.optimizeFailedRetry'));
      return;
    }
    // 默认勾选真正有改动、且原文能在正文里定位到的段
    selected.value = result.sections
      .map((section, index) => ({ section, index }))
      .filter(({ section }) => section.changeType !== 'keep'
        && section.original && form.rawText.includes(section.original))
      .map(({ index }) => index);
  } catch (error) {
    ElMessage.error(error.message || t('resume.optimizeFailed'));
  } finally {
    optimizing.value = false;
  }
}

async function apply(selectedOnly) {
  const indices = selectedOnly ? selected.value : [];
  if (selectedOnly && !indices.length) {
    ElMessage.warning(t('resume.noSelection'));
    return;
  }
  if (!selectedOnly) {
    await ElMessageBox.confirm(t('resume.allConfirm'), t('resume.allConfirmTitle'), { type: 'warning' });
  }
  applying.value = true;
  try {
    const updated = await applyOptimization(resume.value.id, optimization.value.id, indices);
    resume.value = updated;
    form.rawText = updated.rawText;
    ElMessage.success(t('resume.applied', { version: updated.version }));
  } catch (error) {
    ElMessage.error(error.message || t('resume.adoptFailed'));
  } finally {
    applying.value = false;
  }
}

async function remove() {
  await ElMessageBox.confirm(
    t('resume.deleteConfirmBody', { title: resume.value.title }),
    t('resume.deleteConfirmTitle'),
    { type: 'warning' },
  );
  try {
    await deleteResume(resume.value.id);
    router.replace('/resumes');
  } catch (error) {
    ElMessage.error(error.message || t('resume.deleteFailed'));
  }
}

async function exportResume(format) {
  exporting.value = true;
  try {
    await downloadResume(resume.value.id, resume.value.title, format);
  } catch (error) {
    ElMessage.error(error.message || t('resume.exportFailed'));
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="resume-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('resume.eyebrow') }}</div>
        <h1>{{ resume?.title || t('resume.fallbackTitle') }}</h1>
        <p v-if="resume">
          {{ resume.source === 'upload' ? t('resume.uploadSource') : t('resume.manualSource') }}
          · {{ t('resume.version', { version: resume.version }) }} · {{ t('resume.updatedAt', { date: String(resume.updatedAt).slice(0, 16).replace('T', ' ') }) }}
        </p>
      </div>
      <div class="heading-actions">
        <el-button class="soft-button" @click="router.push('/resumes')">{{ t('resume.returnList') }}</el-button>
        <el-dropdown trigger="click" @command="exportResume">
          <el-button class="soft-button" :loading="exporting">
            <el-icon><Download /></el-icon> {{ t('resume.export') }}
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in RESUME_EXPORT_FORMATS" :key="item.value" :command="item.value">
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button class="soft-button danger" @click="remove">{{ t('resume.delete') }}</el-button>
      </div>
    </div>

    <div v-if="resume" class="resume-layout">
      <section class="panel">
        <div class="section-title">{{ t('resume.content') }}</div>
        <el-input v-model="form.title" maxlength="200" :placeholder="t('resume.titlePlaceholder')" class="title-input" />
        <el-input v-model="form.rawText" type="textarea" :rows="18" maxlength="20000" show-word-limit />
        <div class="row-actions">
          <el-button class="primary-button" :loading="saving" @click="save">{{ t('resume.saveChanges') }}</el-button>
        </div>
      </section>

      <section class="panel optimize-panel">
        <div class="section-title">{{ t('resume.optimize') }}</div>
        <el-form label-position="top">
          <el-form-item :label="t('resume.targetRole')"><el-input v-model="job.role" maxlength="200" :placeholder="t('resume.targetRolePlaceholder')" /></el-form-item>
          <el-form-item :label="t('resume.jobDescription')">
            <el-input v-model="job.jobDescription" type="textarea" :rows="6" maxlength="10000" show-word-limit
                      :placeholder="t('resume.jobDescriptionPlaceholder')" />
          </el-form-item>
        </el-form>
        <el-button class="primary-button" :loading="optimizing" @click="optimize">
          {{ optimizing ? t('resume.optimizing') : t('resume.startOptimize') }}
        </el-button>
        <p class="hint">{{ t('resume.optimizeHint') }}</p>
      </section>
    </div>

    <section v-if="optimization" class="panel result-panel">
      <div class="result-head">
        <div class="section-title">{{ t('resume.result') }}</div>
        <el-tag :class="optimization.status === 'completed' ? 'tag-ready' : 'tag-warn'" size="small">
          {{ optimization.status === 'completed' ? t('resume.completed') : t('resume.failed') }}
        </el-tag>
        <span v-if="pendingCount" class="pending">{{ t('resume.pendingCount', { count: pendingCount }) }}</span>
      </div>

      <p v-if="optimization.summary" class="summary">{{ optimization.summary }}</p>
      <p v-if="optimization.error" class="error-text">{{ optimization.error }}</p>

      <div v-if="optimization.keywordCoverage" class="coverage">
        <span>{{ t('resume.jdKeywords') }}</span>
        <em v-for="word in optimization.keywordCoverage.matched || []" :key="word" class="matched">{{ word }}</em>
        <em v-for="word in optimization.keywordCoverage.missing || []" :key="word" class="missing">{{ t('resume.missingWord', { word }) }}</em>
      </div>

      <div class="section-list">
        <div v-for="(section, index) in sections" :key="index" class="section-row">
          <el-checkbox v-model="selected" :value="index" class="row-check"
                       :disabled="!section.original || !form.rawText.includes(section.original)">
            <span class="change-type">{{ section.changeType }}</span>
          </el-checkbox>
          <div class="pair">
            <div class="col">
              <small>{{ t('resume.original') }}</small>
              <p class="muted">{{ section.original || t('resume.newSection') }}</p>
            </div>
            <div class="col">
              <small>{{ t('resume.optimizedText') }}</small>
              <p>
                <template v-for="(seg, i) in segmentsOf(section.optimized)" :key="i">
                  <mark v-if="seg.placeholder">{{ seg.text }}</mark>
                  <template v-else>{{ seg.text }}</template>
                </template>
              </p>
              <span class="reason">{{ section.reason }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="row-actions">
        <el-button class="primary-button" :loading="applying" @click="apply(true)">
          {{ t('resume.adoptSelected', { count: selected.length }) }}
        </el-button>
        <el-button class="soft-button" :loading="applying" @click="apply(false)">{{ t('resume.adoptAll') }}</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.resume-page { display: grid; gap: 14px; }
.heading-actions { display: flex; gap: 8px; }
.resume-layout { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr); gap: 14px; align-items: start; }
.resume-layout .panel, .result-panel { padding: 16px; box-shadow: none; }
.title-input { margin-bottom: 10px; }
.row-actions { display: flex; gap: 8px; margin-top: 12px; }
.hint { margin: 10px 0 0; color: #8a958e; font-size: 12px; line-height: 1.7; }
.soft-button.danger { color: #a4483a; }
.result-head { display: flex; align-items: center; gap: 10px; }
.pending { color: #b4622a; font-size: 12px; }
.summary { margin: 10px 0 0; color: #47534d; font-size: 13px; line-height: 1.7; }
.error-text { margin: 10px 0 0; color: #a4483a; font-size: 13px; }
.coverage { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 10px; color: #6b7770; font-size: 12px; }
.coverage em { padding: 2px 7px; border-radius: 10px; font-style: normal; }
.coverage .matched { background: var(--green-soft); color: var(--green-dark); }
.coverage .missing { background: #fbeee4; color: #b4622a; }
.section-list { display: grid; gap: 10px; margin-top: 14px; }
.section-row { display: flex; gap: 10px; padding: 11px; border: 1px solid #e8eeea; border-radius: 6px; background: #fafcfb; }
.row-check { margin-right: 2px; }
.change-type { color: #8a958e; font-size: 11px; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; flex: 1; min-width: 0; }
.col small { color: #8a958e; font-size: 11px; }
.col p { margin: 5px 0 0; color: #3f4c45; font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.col p.muted { color: #7c8782; }
.col mark { padding: 1px 4px; border-radius: 3px; background: #fbeee4; color: #b4622a; }
.reason { display: block; margin-top: 6px; color: #8a958e; font-size: 11px; line-height: 1.6; }
@media (max-width: 900px) { .resume-layout { grid-template-columns: 1fr; } .pair { grid-template-columns: 1fr; } }
</style>
