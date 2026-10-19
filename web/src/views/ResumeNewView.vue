<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createResume, draftResume, parseResumeFile, refineResume } from '../services/resume';
import {
  BASICS_FIELDS, ENTRY_STEPS, EXAMPLE_KEYS, WIZARD_STEPS, clearLocalDraft, countFilled, defaultTitle,
  emptyEntry, emptyState, loadLocalDraft, saveLocalDraft, toMaterials,
} from '../services/resumeWizard';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
const tab = ref('guided');

// ---- 从零创建 ----
const state = reactive(emptyState());
const stepIndex = ref(0);
const generating = ref(false);
const generated = ref(null);
const draftText = ref('');
const supplement = ref('');
const refining = ref(false);
const saving = ref(false);
const title = ref('');

const step = computed(() => WIZARD_STEPS[stepIndex.value]);
const entryConfig = computed(() => ENTRY_STEPS[step.value.key] || null);
const entries = computed(() => (entryConfig.value ? state[step.value.key] : null));
const filled = computed(() => countFilled(state));
const placeholderPattern = /【[^】]{1,60}】/g;

/** 占位符单独切片，用于高亮（不用 v-html，避免注入）。 */
function segmentsOf(text) {
  const parts = [];
  let last = 0;
  for (const match of String(text || '').matchAll(placeholderPattern)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index), placeholder: false });
    parts.push({ text: match[0], placeholder: true });
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), placeholder: false });
  return parts;
}

onMounted(() => {
  const restored = loadLocalDraft();
  if (restored) {
    Object.assign(state, restored);
    ElMessage.info(t('resume.restored'));
  }
});

let saveTimer = null;
watch(state, () => {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => saveLocalDraft(state), 300);
}, { deep: true });

function addEntry() {
  if (!entries.value) return;
  if (entries.value.length >= 12) {
    ElMessage.warning(t('resume.entryLimit'));
    return;
  }
  entries.value.push(emptyEntry());
}

function removeEntry(index) {
  entries.value.splice(index, 1);
}

async function generate() {
  generating.value = true;
  try {
    const result = await draftResume(toMaterials(state));
    generated.value = result;
    draftText.value = result.rawText;
    if (!title.value) title.value = defaultTitle(state, t);
  } catch (error) {
    ElMessage.error(error.message || t('resume.generateFailed'));
  } finally {
    generating.value = false;
  }
}

async function refine() {
  if (!supplement.value.trim()) {
    ElMessage.warning(t('resume.supplementRequired'));
    return;
  }
  refining.value = true;
  try {
    const result = await refineResume(draftText.value, supplement.value.trim(), generated.value?.questions || []);
    generated.value = result;
    draftText.value = result.rawText;
    supplement.value = '';
    ElMessage.success(result.placeholders?.length
      ? t('resume.updatedPending', { count: result.placeholders.length })
      : t('resume.updatedComplete'));
  } catch (error) {
    ElMessage.error(error.message || t('resume.refineFailed'));
  } finally {
    refining.value = false;
  }
}

/** optimize=true 时保存完直接跳到详情页的优化面板（Q10：不自动串，但要顺手）。 */
async function save(optimize = false) {
  if (!title.value.trim() || !draftText.value.trim()) {
    ElMessage.warning(t('resume.titleRequired'));
    return;
  }
  saving.value = true;
  try {
    const created = await createResume({
      title: title.value.trim(), source: 'generated', fileName: '', rawText: draftText.value,
    });
    clearLocalDraft();
    generated.value = null;
    const query = optimize ? { optimize: '1', role: state.targetRole || undefined } : {};
    router.push({ path: `/resumes/${created.id}`, query });
  } catch (error) {
    ElMessage.error(error.message || t('resume.saveFailed'));
  } finally {
    saving.value = false;
  }
}

function discard() {
  generated.value = null;
  draftText.value = '';
}

// ---- 上传解析 / 手动填写 ----
const manual = reactive({ title: '', rawText: '', fileName: '', source: 'manual' });
const parsing = ref(false);

async function onFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  parsing.value = true;
  try {
    const parsed = await parseResumeFile(file);
    if (!parsed?.text) {
      ElMessage.warning(t('resume.unreadableFile'));
      return;
    }
    manual.rawText = parsed.text;
    manual.fileName = parsed.fileName;
    manual.source = 'upload';
    if (!manual.title) manual.title = (parsed.fileName || t('resume.fileDefaultTitle')).replace(/\.[^.]+$/, '');
    ElMessage.success(t('resume.parsedFile', {
      count: parsed.characters,
      truncated: parsed.truncated ? t('resume.truncated') : '',
    }));
  } catch (error) {
    ElMessage.error(error.message || t('resume.parseFailed'));
  } finally {
    parsing.value = false;
    event.target.value = '';
  }
}

async function saveManual() {
  if (!manual.title.trim() || !manual.rawText.trim()) {
    ElMessage.warning(t('resume.titleRequired'));
    return;
  }
  saving.value = true;
  try {
    const created = await createResume({
      title: manual.title.trim(), source: manual.source, fileName: manual.fileName, rawText: manual.rawText,
    });
    router.push(`/resumes/${created.id}`);
  } catch (error) {
    ElMessage.error(error.message || t('resume.saveFailed'));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="resume-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('resume.eyebrow') }}</div>
        <h1>{{ t('resume.addedTitle') }}</h1>
        <p>{{ t('resume.addedSubtitle') }}</p>
      </div>
      <el-button class="soft-button" @click="router.push('/resumes')">{{ t('resume.returnList') }}</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane :label="t('resume.fromScratch')" name="guided">
        <template v-if="!generated">
          <div class="step-bar">
            <button v-for="(item, index) in WIZARD_STEPS" :key="item.key" type="button"
                    :class="{ active: index === stepIndex, done: index < stepIndex }"
                    @click="stepIndex = index">
              <i>{{ index + 1 }}</i>{{ t(item.labelKey) }}
            </button>
          </div>

          <section class="panel step-panel">
            <div class="section-title">
              {{ t(step.labelKey) }}
              <small>{{ t('resume.stepHint') }}</small>
            </div>

            <el-input v-if="step.kind === 'text'" v-model="state[step.key]" maxlength="200"
                      :placeholder="t(EXAMPLE_KEYS[step.key])" />
            <p v-if="step.kind === 'text'" class="hint">
              {{ t('resume.targetRoleHint') }}
            </p>

            <el-form v-else-if="step.kind === 'basics'" label-position="top">
              <el-form-item v-for="field in BASICS_FIELDS" :key="field.key" :label="t(field.labelKey)">
                <el-input v-model="state.basics[field.key]" maxlength="200" :placeholder="t(field.exampleKey)" />
              </el-form-item>
            </el-form>

            <template v-else-if="step.kind === 'entries'">
              <p v-if="!entries.length" class="hint">
                {{ t('resume.noEntries') }}
              </p>
              <div v-for="(entry, index) in entries" :key="index" class="entry-card">
                <div class="entry-head">
                  <strong>{{ t(entryConfig.entryLabelKey) }} {{ index + 1 }}</strong>
                  <el-button class="soft-button" size="small" @click="removeEntry(index)">{{ t('resume.deleteEntry') }}</el-button>
                </div>
                <el-form label-position="top">
                  <el-form-item v-for="field in entryConfig.fields" :key="field.key" :label="t(field.labelKey)">
                    <el-input v-if="field.rows" v-model="entry[field.key]" type="textarea" :rows="field.rows"
                              maxlength="600" :placeholder="t(field.exampleKey)" />
                    <el-input v-else v-model="entry[field.key]" maxlength="200" :placeholder="t(field.exampleKey)" />
                  </el-form-item>
                </el-form>
              </div>
              <el-button class="soft-button" @click="addEntry">{{ t(entryConfig.addLabelKey) }}</el-button>
            </template>

            <template v-else>
              <el-input v-model="state[step.key]" type="textarea" :rows="6" maxlength="600"
                        :placeholder="t(EXAMPLE_KEYS[step.key])" />
            </template>

            <div class="step-actions">
              <el-button class="soft-button" :disabled="stepIndex === 0" @click="stepIndex -= 1">{{ t('resume.prev') }}</el-button>
              <span class="step-hint">{{ t('resume.filledHint', { count: filled }) }}</span>
              <el-button v-if="stepIndex < WIZARD_STEPS.length - 1" class="soft-button"
                         @click="stepIndex += 1">{{ t('resume.next') }}</el-button>
              <el-button v-else class="primary-button" :loading="generating" @click="generate">
                {{ generating ? t('resume.generating') : t('resume.generate') }}
              </el-button>
            </div>
            <p v-if="!filled" class="hint">
              {{ t('resume.emptySkeleton') }}
            </p>
          </section>
        </template>

        <template v-else>
          <section class="panel draft-panel">
            <div class="section-title">
              {{ t('resume.generatedTitle') }}
              <span v-if="generated.placeholders?.length" class="pending">
                {{ t('resume.generatedPending', { count: generated.placeholders.length }) }}
              </span>
            </div>
            <p class="hint">{{ t('resume.generatedHint') }}</p>
            <div class="draft-preview">
              <template v-for="(seg, index) in segmentsOf(draftText)" :key="index">
                <mark v-if="seg.placeholder">{{ seg.text }}</mark>
                <template v-else>{{ seg.text }}</template>
              </template>
            </div>
            <el-input v-model="draftText" type="textarea" :rows="12" maxlength="20000" show-word-limit />
          </section>

          <section v-if="generated.questions?.length" class="panel">
            <div class="section-title">{{ t('resume.missingTitle') }}</div>
            <ul class="question-list">
              <li v-for="(question, index) in generated.questions" :key="index">{{ question }}</li>
            </ul>
            <el-input v-model="supplement" type="textarea" :rows="3" maxlength="4000"
                      :placeholder="t('resume.supplementPlaceholder')" />
            <div class="step-actions">
              <el-button class="soft-button" :loading="refining" @click="refine">{{ t('resume.refine') }}</el-button>
              <span class="step-hint">{{ t('resume.refineHint') }}</span>
            </div>
          </section>

          <section class="panel">
            <div class="section-title">{{ t('resume.saveSection') }}</div>
            <el-form label-position="top">
              <el-form-item :label="t('resume.resumeTitle')">
                <el-input v-model="title" maxlength="200" :placeholder="t('resume.resumeTitlePlaceholder')" />
              </el-form-item>
            </el-form>
            <div class="step-actions">
              <el-button class="soft-button" @click="discard">{{ t('resume.returnEdit') }}</el-button>
              <el-button class="soft-button" :loading="saving" @click="save(false)">{{ t('resume.saveOnly') }}</el-button>
              <el-button class="primary-button" :loading="saving" @click="save(true)">
                {{ t('resume.saveOptimize') }}
              </el-button>
            </div>
          </section>
        </template>
      </el-tab-pane>

      <el-tab-pane :label="t('resume.uploadParse')" name="upload">
        <section class="panel">
          <div class="section-title">{{ t('resume.uploadFileTitle') }}</div>
          <label class="upload-label">
            <input type="file" accept=".pdf,.doc,.docx,.txt" @change="onFile" />
            <el-icon><Upload /></el-icon>{{ parsing ? t('resume.parsing') : t('resume.chooseFile') }}
          </label>
          <small class="hint">{{ t('resume.uploadHint') }}</small>
        </section>
      </el-tab-pane>

      <el-tab-pane :label="t('resume.manual')" name="manual">
        <section class="panel">
          <div class="section-title">{{ t('resume.manualPaste') }}</div>
          <p class="hint">{{ t('resume.manualHint') }}</p>
        </section>
      </el-tab-pane>
    </el-tabs>

    <section v-if="tab !== 'guided'" class="panel">
      <div class="section-title">{{ t('resume.resumeContent') }}</div>
      <el-form label-position="top">
        <el-form-item :label="t('resume.resumeTitle')">
          <el-input v-model="manual.title" maxlength="200" :placeholder="t('resume.manualTitlePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('resume.body')">
          <el-input v-model="manual.rawText" type="textarea" :rows="14" maxlength="20000" show-word-limit
                    :placeholder="t('resume.bodyPlaceholder')" />
        </el-form-item>
      </el-form>
      <div class="step-actions">
        <span class="step-hint">{{ t('resume.saveResumeHint') }}</span>
        <el-button class="primary-button" :loading="saving" @click="saveManual">{{ t('resume.saveResume') }}</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.resume-page { display: grid; gap: 14px; }
.step-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.step-bar button { display: inline-flex; align-items: center; gap: 7px; padding: 7px 12px; border: 1px solid var(--line); border-radius: 999px; background: #fff; color: #6b7671; font-size: 13px; cursor: pointer; }
.step-bar button i { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: #f0f3f1; color: #8a958e; font-size: 11px; font-style: normal; }
.step-bar button.done { color: #4d5a54; }
.step-bar button.active { border-color: var(--green); color: var(--green-dark); font-weight: 600; }
.step-bar button.active i { background: var(--green); color: #fff; }
.step-panel, .draft-panel { padding: 18px; }
.entry-card { margin-bottom: 12px; padding: 14px; border: 1px solid var(--line); border-radius: 8px; background: #fbfcfb; }
.entry-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; color: #33413a; font-size: 14px; }
.step-actions { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
.step-hint { flex: 1; color: #8a958e; font-size: 13px; }
.hint { margin: 8px 0 0; color: #8a958e; font-size: 13px; line-height: 1.7; }
.pending { color: #b45309; font-size: 13px; font-weight: 500; }
.draft-preview { max-height: 170px; margin: 10px 0 12px; padding: 12px 14px; overflow: auto; border: 1px solid var(--line); border-radius: 8px; background: #fbfcfb; color: #3c4650; font-size: 13px; line-height: 1.75; white-space: pre-wrap; }
.draft-preview mark { background: #fdf0e2; color: #b45309; font-weight: 600; }
.question-list { margin: 0 0 12px; padding-left: 20px; color: #44514b; font-size: 14px; line-height: 1.9; }
.upload-label { display: inline-flex; align-items: center; gap: 6px; padding: 7px 12px; border: 1px dashed #cfe0d6; border-radius: 6px; color: var(--green-dark); font-size: 13px; cursor: pointer; }
.upload-label input { display: none; }
</style>
