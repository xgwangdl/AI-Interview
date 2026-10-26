<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createCodingSession, deleteCodingSession, getCodingCapabilities, listCodingSessions } from '../services/coding';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
const busy = ref(false);
const elapsed = ref(0);
let elapsedTimer;
const loading = ref(true);
const sessions = ref([]);
const capabilities = ref({ codeRunnerEnabled: false, languages: [] });

const form = reactive({
  role: '',
  company: '',
  jobDescription: '',
  difficulty: 'standard',
  questions: [
    { type: 'coding', language: 'java' },
    { type: 'sql', language: '' },
    { type: 'choice', language: '' },
  ],
});

const languageOptions = computed(() => (capabilities.value.languages || []).map((item) => item.id));
const languageLabels = { java: 'Java', python: 'Python', cpp: 'C++', javascript: 'JavaScript', go: 'Go' };
const typeKeys = ['coding', 'sql', 'fill_blank', 'choice', 'short_answer'];

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const [list, caps] = await Promise.all([listCodingSessions(), getCodingCapabilities()]);
    sessions.value = list || [];
    capabilities.value = caps || capabilities.value;
  } catch (error) {
    ElMessage.error(error.message || t('coding.loadFailed'));
  } finally {
    loading.value = false;
  }
}

function addQuestion(type) {
  const needsLanguage = type === 'coding';
  form.questions.push({ type, language: needsLanguage ? 'java' : '' });
}

function removeQuestion(index) {
  form.questions.splice(index, 1);
}

async function create() {
  if (!form.role.trim()) {
    ElMessage.warning(t('coding.roleRequired'));
    return;
  }
  if (!form.questions.length) {
    ElMessage.warning(t('coding.questionRequired'));
    return;
  }
  busy.value = true;
  elapsed.value = 0;
  elapsedTimer = window.setInterval(() => { elapsed.value += 1; }, 1000);
  try {
    const session = await createCodingSession({
      role: form.role.trim(),
      company: form.company.trim(),
      jobDescription: form.jobDescription.trim(),
      difficulty: form.difficulty,
      questions: form.questions.map((item) => ({ type: item.type, language: item.language || undefined })),
    });
    ElMessage.success(t('coding.generated'));
    router.push(`/coding/${session.id}`);
  } catch (error) {
    ElMessage.error(error.message || t('coding.generateFailed'));
  } finally {
    busy.value = false;
    window.clearInterval(elapsedTimer);
  }
}

async function remove(session) {
  await ElMessageBox.confirm(t('coding.deleteConfirmBody'), t('coding.deleteConfirmTitle'), { type: 'warning' });
  try {
    await deleteCodingSession(session.id);
    sessions.value = sessions.value.filter((item) => item.id !== session.id);
  } catch (error) {
    ElMessage.error(error.message || t('coding.deleteFailed'));
  }
}
</script>

<template>
  <section v-loading="loading" class="coding-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('coding.eyebrow') }}</div>
        <h1>{{ t('coding.setupTitle') }}</h1>
        <p>{{ t('coding.setupSubtitle') }}</p>
      </div>
      <el-tag :class="capabilities.codeRunnerEnabled ? 'tag-ready' : 'tag-muted'">
        {{ capabilities.codeRunnerEnabled ? t('coding.sandboxReady') : t('coding.sandboxDisabled') }}
      </el-tag>
    </div>

    <div class="setup-grid">
      <section class="panel setup-panel">
        <div class="panel-title">{{ t('coding.conditions') }}</div>
        <el-form label-position="top">
          <el-form-item :label="t('coding.role')">
            <el-input v-model="form.role" maxlength="80" :placeholder="t('coding.rolePlaceholder')" />
          </el-form-item>
          <div class="field-grid">
            <el-form-item :label="t('coding.company')">
              <el-input v-model="form.company" maxlength="80" :placeholder="t('coding.companyPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('coding.difficulty')">
              <el-select v-model="form.difficulty">
                <el-option :label="t('domain.difficulty.foundation')" value="foundation" />
                <el-option :label="t('domain.difficulty.standard')" value="standard" />
                <el-option :label="t('domain.difficulty.advanced')" value="advanced" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item :label="t('coding.jobDescription')">
            <el-input v-model="form.jobDescription" type="textarea" :rows="4" maxlength="3000" show-word-limit :placeholder="t('coding.jobDescriptionPlaceholder')" />
          </el-form-item>
        </el-form>
      </section>

      <section class="panel setup-panel">
        <div class="panel-title">{{ t('coding.questionMix', { count: form.questions.length }) }}</div>
        <div class="add-buttons">
          <el-button v-for="type in typeKeys" :key="type" class="soft-button" @click="addQuestion(type)">
            <el-icon><Plus /></el-icon>{{ t(`domain.codingType.${type}`) }}
          </el-button>
        </div>
        <div class="question-rows">
          <div v-for="(question, questionIndex) in form.questions" :key="questionIndex" class="question-row">
            <span class="row-index">{{ questionIndex + 1 }}</span>
            <div class="row-body">
              <select v-model="question.type" class="row-select">
                <option v-for="type in typeKeys" :key="type" :value="type">{{ t(`domain.codingType.${type}`) }}</option>
              </select>
              <select v-if="question.type === 'coding'" v-model="question.language" class="row-select">
                <option v-for="language in languageOptions" :key="language" :value="language">{{ languageLabels[language] || language }}</option>
              </select>
              <small>{{ t(`coding.hints.${question.type}`) }}</small>
            </div>
            <button class="row-remove" type="button" :aria-label="t('coding.remove')" @click="removeQuestion(questionIndex)">×</button>
          </div>
        </div>
        <el-button class="primary-button create-button" :loading="busy" @click="create">
          {{ busy ? t('coding.generating', { seconds: elapsed }) : t('coding.generate') }} <el-icon v-if="!busy"><ArrowRight /></el-icon>
        </el-button>
        <p class="runner-note">
          {{ t('coding.runnerNote') }}
        </p>
      </section>
    </div>

    <section class="panel history-panel">
      <div class="panel-title">{{ t('coding.history') }}</div>
      <el-empty v-if="!sessions.length" :description="t('coding.noHistory')" />
      <div v-for="session in sessions" :key="session.id" class="history-row">
        <div>
          <strong>{{ session.role }}</strong>
          <small>{{ t('coding.historyMeta', { count: session.questions.length, created: String(session.createdAt).slice(0, 10), retention: String(session.retentionUntil).slice(0, 10) }) }}</small>
        </div>
        <div class="history-actions">
          <el-button class="soft-button" @click="router.push(`/coding/${session.id}`)">{{ t('coding.continue') }}</el-button>
          <el-button class="soft-button" @click="router.push(`/coding/${session.id}/report`)">{{ t('coding.viewReport') }}</el-button>
          <el-button class="soft-button danger" @click="remove(session)">{{ t('coding.delete') }}</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.coding-page { display: grid; gap: 14px; }
.setup-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; }
.setup-panel, .history-panel { padding: 18px; box-shadow: none; }
.panel-title { margin-bottom: 14px; color: #35433b; font-size: 16px; font-weight: 600; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.add-buttons { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.question-rows { display: grid; gap: 8px; }
.question-row { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #e6ebe7; border-radius: 6px; background: #fafcfb; }
.row-index { display: grid; width: 22px; height: 22px; flex: none; place-items: center; border-radius: 50%; background: var(--green-soft); color: var(--green); font-size: 14px; }
.row-body { display: flex; flex: 1; flex-wrap: wrap; align-items: center; gap: 8px; }
.row-body small { color: #8d9892; font-size: 14px; }
.row-select { height: 30px; padding: 0 8px; border: 1px solid #dfe6e1; border-radius: 5px; background: #fff; color: #3f4c45; font-size: 14px; }
.row-remove { width: 26px; height: 26px; border: 0; border-radius: 50%; background: transparent; color: #98a19c; font-size: 17px; cursor: pointer; }
.row-remove:hover { background: #f2f5f3; color: #a4483a; }
.create-button { width: 100%; margin-top: 16px; }
.runner-note { margin: 10px 0 0; color: #8d9892; font-size: 14px; line-height: 1.7; }
.history-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-top: 1px solid #eef1ee; }
.history-row strong { display: block; color: #35433b; font-size: 15px; }
.history-row small { color: #8d9892; font-size: 14px; }
.history-actions { display: flex; gap: 8px; }
.soft-button.danger { color: #a4483a; }
@media (max-width: 900px) { .setup-grid { grid-template-columns: 1fr; } .history-row { flex-direction: column; align-items: flex-start; } }
</style>
