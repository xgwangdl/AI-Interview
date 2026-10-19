<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import CodeEditor from '../components/CodeEditor.vue';
import { getCodingSession, runCodingAnswer, submitCodingAnswer } from '../services/coding';
import { useI18n } from 'vue-i18n';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const session = ref(null);
const loading = ref(true);
const index = ref(0);
const busy = ref(false);
const result = ref(null);
const answers = reactive({});
const extra = reactive({});

const questions = computed(() => session.value?.questions || []);
const current = computed(() => questions.value[index.value] || null);
const submissionFor = (questionId) => (session.value?.submissions || []).find((item) => item.questionId === questionId);

const languageLabels = { java: 'Java', python: 'Python', cpp: 'C++', javascript: 'JavaScript', go: 'Go', sql: 'SQL' };
onMounted(load);

async function load() {
  loading.value = true;
  try {
    session.value = await getCodingSession(route.params.id);
    for (const question of questions.value) {
      const saved = (session.value.submissions || []).find((item) => item.questionId === question.id);
      if (question.type === 'coding' || question.type === 'sql') {
        answers[question.id] = question.starterCode || '';
      } else if (question.type === 'fill_blank') {
        extra[question.id] = (question.blanks || []).map(() => '');
      } else if (question.type === 'choice') {
        extra[question.id] = [];
      } else {
        answers[question.id] = '';
      }
      // 已经提交过的题，把上次的作答带回来，方便继续改。
      if (saved && question.type !== 'coding' && question.type !== 'sql') answers[question.id] = '';
    }
  } catch (error) {
    ElMessage.error(error.message || t('coding.loadExamFailed'));
  } finally {
    loading.value = false;
  }
}

function payloadFor(question) {
  if (question.type === 'fill_blank') return JSON.stringify(extra[question.id] || []);
  if (question.type === 'choice') return (extra[question.id] || []).join(',');
  return answers[question.id] || '';
}

async function run() {
  if (!current.value) return;
  busy.value = true;
  result.value = null;
  try {
    result.value = await runCodingAnswer(session.value.id, current.value.id, payloadFor(current.value));
    if (result.value.status === 'passed') ElMessage.success(t('coding.samplesPassed'));
    else ElMessage.warning(t('coding.samplesFailed'));
  } catch (error) {
    ElMessage.error(error.message || t('coding.runFailed'));
  } finally {
    busy.value = false;
  }
}

async function submit() {
  if (!current.value) return;
  const answer = payloadFor(current.value);
  if (!answer || answer === '[]') {
    ElMessage.warning(t('coding.answerRequired'));
    return;
  }
  busy.value = true;
  result.value = null;
  try {
    result.value = await submitCodingAnswer(session.value.id, current.value.id, answer);
    const passed = result.value.status === 'passed';
    if (passed) ElMessage.success(t('coding.questionPassed'));
    else ElMessage.warning(t('coding.questionFailed', { passed: result.value.passedCases, total: result.value.totalCases }));
    await refreshSummary();
  } catch (error) {
    ElMessage.error(error.message || t('coding.submitFailed'));
  } finally {
    busy.value = false;
  }
}

async function refreshSummary() {
  try {
    session.value = await getCodingSession(route.params.id);
  } catch {
    // 拉取失败不影响本次判分结果的展示。
  }
}

function selectQuestion(target) {
  index.value = target;
  result.value = null;
}

async function finish() {
  await ElMessageBox.confirm(t('coding.finishConfirmBody'), t('coding.finishConfirmTitle'), {
    confirmButtonText: t('coding.finishConfirmAction'),
    cancelButtonText: t('coding.continueAnswer'),
  });
  router.push(`/coding/${session.value.id}/report`);
}

function caseLabel(item) {
  if (item.hidden) return t('coding.hiddenCase', { index: item.index });
  return item.name
    ? t('coding.caseNamed', { index: item.index, name: item.name })
    : t('coding.caseNth', { index: item.index });
}
</script>

<template>
  <section v-loading="loading" class="coding-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('coding.eyebrow') }}</div>
        <h1>{{ t('coding.examTitle') }}</h1>
        <p v-if="session">{{ t('coding.examSubtitle', { role: session.role, count: questions.length, retention: String(session.retentionUntil).slice(0, 10) }) }}</p>
      </div>
      <div class="heading-actions">
        <el-button class="soft-button" @click="router.push('/coding')">{{ t('coding.backList') }}</el-button>
        <el-button class="primary-button" @click="finish">{{ t('coding.finish') }}</el-button>
      </div>
    </div>

    <div v-if="session" class="coding-layout">
      <aside class="question-rail panel">
        <div class="rail-header"><span>{{ t('coding.questions') }}</span><strong>{{ questions.length }}</strong></div>
        <button
          v-for="(question, target) in questions"
          :key="question.id"
          class="question-nav"
          :class="{ active: target === index, done: submissionFor(question.id) }"
          @click="selectQuestion(target)"
        >
          <span class="question-index">{{ submissionFor(question.id) ? '✓' : String(target + 1).padStart(2, '0') }}</span>
          <span>
            <small>{{ t(`domain.codingType.${question.type}`) }}{{ question.language ? ` · ${languageLabels[question.language] || question.language}` : '' }}</small>
            <strong>{{ question.title }}</strong>
          </span>
        </button>
      </aside>

      <div v-if="current" class="coding-main">
        <section class="panel question-panel">
          <div class="question-head">
            <span class="question-kicker">{{ t('coding.questionNth', { number: index + 1 }) }} · {{ t(`domain.codingType.${current.type}`) }}<template v-if="current.language"> · {{ languageLabels[current.language] || current.language }}</template></span>
            <el-tag v-if="submissionFor(current.id)" :class="submissionFor(current.id).status === 'passed' ? 'tag-ready' : 'tag-warn'" size="small">
              {{ submissionFor(current.id).status === 'passed' ? t('coding.passedStatus') : t('coding.partialPassed', { passed: submissionFor(current.id).passedCases, total: submissionFor(current.id).totalCases }) }}
            </el-tag>
          </div>
          <h2>{{ current.title }}</h2>
          <pre class="statement">{{ current.statement }}</pre>

          <div v-if="current.visibleTestCases?.length" class="samples">
            <div class="samples-title">{{ t('coding.sample') }}</div>
            <div v-for="(sample, sampleIndex) in current.visibleTestCases" :key="sampleIndex" class="sample-row">
              <span>{{ sample.name || t('coding.sampleNth', { number: sampleIndex + 1 }) }}</span>
              <code>{{ t('coding.input', { value: sample.stdin || t('coding.none') }) }}</code>
              <code>{{ t('coding.output', { value: sample.expectedStdout }) }}</code>
            </div>
          </div>

          <div v-if="current.type === 'sql' && current.sqlSetup" class="sql-setup">
            <div class="samples-title">{{ t('coding.sqlSetup') }}</div>
            <pre>{{ current.sqlSetup }}</pre>
          </div>
        </section>

        <section class="panel answer-panel">
          <div class="answer-title">{{ t('coding.yourAnswer') }}</div>

          <template v-if="current.type === 'coding' || current.type === 'sql'">
            <CodeEditor
              v-model="answers[current.id]"
              :language="current.type === 'sql' ? 'sql' : current.language"
              height="360px"
            />
          </template>

          <template v-else-if="current.type === 'fill_blank'">
            <pre class="statement blank-code">{{ current.starterCode }}</pre>
            <div class="blank-list">
              <label v-for="(blank, blankIndex) in current.blanks" :key="blank.index" class="blank-row">
                <span>{{ t('coding.blankNth', { number: blank.index + 1 }) }}<small v-if="blank.hint">{{ t('coding.blankHint', { hint: blank.hint }) }}</small></span>
                <el-input v-model="extra[current.id][blankIndex]" :placeholder="t('coding.blankPlaceholder')" />
              </label>
            </div>
          </template>

          <template v-else-if="current.type === 'choice'">
            <el-checkbox-group v-model="extra[current.id]" class="choice-list">
              <el-checkbox v-for="option in current.options" :key="option.key" :value="option.key" :label="option.key">
                <strong>{{ option.key }}</strong> {{ option.text }}
              </el-checkbox>
            </el-checkbox-group>
          </template>

          <template v-else>
            <el-input v-model="answers[current.id]" type="textarea" :rows="8" maxlength="4000" show-word-limit :placeholder="t('coding.shortAnswerPlaceholder')" />
          </template>

          <div class="answer-actions">
            <el-button
              v-if="current.type === 'coding' || current.type === 'sql'"
              class="soft-button"
              :loading="busy"
              @click="run"
            >{{ t('coding.runSamples') }}</el-button>
            <el-button class="primary-button" :loading="busy" @click="submit">{{ t('coding.submitQuestion') }}</el-button>
            <span class="answer-hint">{{ t('coding.runHint') }}</span>
          </div>
        </section>

        <section v-if="result" class="panel result-panel">
          <div class="result-head">
            <strong>{{ t('coding.result') }}</strong>
            <el-tag :class="result.status === 'passed' ? 'tag-ready' : 'tag-warn'" size="small">
              {{ result.status === 'passed' ? t('coding.passed') : result.status === 'error' ? t('coding.error') : t('coding.failed') }}
            </el-tag>
            <span class="result-score">{{ t('coding.score', { score: result.score ?? 0 }) }}</span>
            <span class="result-cases">{{ t('coding.cases', { passed: result.passedCases, total: result.totalCases }) }}</span>
            <span v-if="result.runtimeMs" class="result-cases">{{ t('coding.runtime', { ms: result.runtimeMs }) }}</span>
          </div>

          <pre v-if="result.compileOutput" class="console-output error">{{ result.compileOutput }}</pre>
          <pre v-if="result.stdout" class="console-output">{{ result.stdout }}</pre>
          <pre v-if="result.stderr" class="console-output error">{{ result.stderr }}</pre>

          <div v-if="result.review" class="review-box">
            <div class="review-score">{{ t('coding.modelScore', { score: result.review.score }) }}</div>
            <p>{{ result.review.summary }}</p>
            <div v-if="result.review.matched?.length" class="review-list"><span>{{ t('coding.covered') }}</span>{{ result.review.matched.join(' / ') }}</div>
            <div v-if="result.review.missing?.length" class="review-list"><span>{{ t('coding.missing') }}</span>{{ result.review.missing.join(' / ') }}</div>
          </div>

          <div v-if="result.cases?.length" class="case-list">
            <div v-for="item in result.cases" :key="item.index" class="case-row" :class="{ failed: !item.passed }">
              <span class="case-name">{{ caseLabel(item) }}</span>
              <span class="case-status">{{ item.passed ? t('coding.passed') : item.message || t('coding.failed') }}</span>
              <template v-if="!item.passed && !item.hidden">
                <code>{{ t('coding.input', { value: item.input || t('coding.none') }) }}</code>
                <code>{{ t('coding.output', { value: item.expected }) }}</code>
                <code>{{ t('coding.output', { value: item.actual || t('coding.noOutput') }) }}</code>
              </template>
              <small v-else-if="!item.passed && item.hidden">{{ t('coding.hiddenNoDetails') }}</small>
            </div>
          </div>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped>
.coding-page { display: grid; gap: 14px; }
.heading-actions { display: flex; gap: 8px; }
.coding-layout { display: grid; grid-template-columns: 250px minmax(0, 1fr); align-items: start; gap: 14px; }
.question-rail { padding: 14px 0 8px; box-shadow: none; }
.rail-header { display: flex; justify-content: space-between; padding: 0 14px 10px; color: #59655e; font-size: 14px; }
.question-nav { display: flex; width: 100%; align-items: center; gap: 9px; padding: 11px 12px; border: 0; border-left: 2px solid transparent; background: #fff; text-align: left; cursor: pointer; }
.question-nav:hover { background: #fafcfb; }
.question-nav.active { border-left-color: var(--green); background: #f1f7f3; }
.question-nav > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 4px; }
.question-nav small { color: #98a19c; font-size: 14px; }
.question-nav strong { overflow: hidden; color: #616d66; font-size: 14px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.question-nav.active strong { color: #35433b; font-weight: 600; }
.question-index { display: grid; width: 24px; height: 24px; flex: none; place-items: center; border: 1px solid #e4e9e5; border-radius: 50%; color: #98a39d; font-size: 14px; }
.question-nav.done .question-index { border-color: #d5eadb; background: var(--green-soft); color: var(--green); }
.coding-main { display: grid; gap: 12px; }
.question-panel, .answer-panel, .result-panel { padding: 16px 18px; box-shadow: none; }
.question-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.question-kicker { color: var(--green); font-size: 14px; font-weight: 600; }
.question-panel h2 { margin: 10px 0 8px; color: #2f3c35; font-size: 18px; font-weight: 650; }
.statement { margin: 0; color: #47534d; font-family: inherit; font-size: 15px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.blank-code { margin-bottom: 12px; padding: 12px; border-radius: 6px; background: #f7faf8; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 14px; }
.samples, .sql-setup { margin-top: 14px; }
.samples-title { margin-bottom: 6px; color: #59655e; font-size: 14px; font-weight: 600; }
.sample-row { display: grid; gap: 3px; padding: 8px 10px; border-left: 2px solid var(--green-soft); background: #fafcfb; }
.sample-row span { color: #6b7770; font-size: 14px; }
.sample-row code { color: #3f4c45; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 14px; }
.sql-setup pre { margin: 0; overflow-x: auto; padding: 10px; border-radius: 6px; background: #f7faf8; color: #47534d; font-size: 14px; }
.answer-title { margin-bottom: 10px; color: #35433b; font-size: 15px; font-weight: 600; }
.blank-list { display: grid; gap: 10px; }
.blank-row { display: grid; gap: 5px; }
.blank-row > span { color: #59655e; font-size: 14px; }
.blank-row small { color: #8d9892; }
.choice-list { display: grid; gap: 8px; }
.answer-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 14px; }
.answer-hint { color: #8a958e; font-size: 14px; }
.result-head { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.result-head strong { color: #35433b; font-size: 15px; }
.result-score { color: var(--green); font-size: 16px; font-weight: 650; }
.result-cases { color: #7b877f; font-size: 14px; }
.console-output { margin: 10px 0 0; max-height: 220px; overflow: auto; padding: 10px; border-radius: 6px; background: #f6f8f6; color: #3f4c45; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 14px; white-space: pre-wrap; }
.console-output.error { background: #fff4f1; color: #a4483a; }
.review-box { margin-top: 12px; padding: 12px; border: 1px solid #e2ece6; border-radius: 6px; background: #f7fbf8; }
.review-score { color: var(--green); font-weight: 650; }
.review-box p { margin: 6px 0; color: #47534d; font-size: 14px; line-height: 1.7; }
.review-list { color: #6b7770; font-size: 14px; }
.review-list span { color: #59655e; }
.case-list { display: grid; gap: 8px; margin-top: 12px; }
.case-row { display: grid; gap: 3px; padding: 9px 11px; border-left: 2px solid #d5eadb; background: #fafcfb; }
.case-row.failed { border-left-color: #e8b4a6; background: #fffaf8; }
.case-name { color: #3f4c45; font-size: 14px; font-weight: 600; }
.case-status { color: #6b7770; font-size: 14px; }
.case-row.failed .case-status { color: #a4483a; }
.case-row code { color: #47534d; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 14px; }
.case-row small { color: #8d9892; font-size: 14px; }
@media (max-width: 900px) { .coding-layout { grid-template-columns: 1fr; } }
</style>
