<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import CodeEditor from '../components/CodeEditor.vue';
import { getCodingSession, runCodingAnswer, submitCodingAnswer } from '../services/coding';

const route = useRoute();
const router = useRouter();
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
const typeLabels = { coding: '上机编程', sql: '数据库题', fill_blank: '编程填空', choice: '选择题', short_answer: '简答题' };

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
    ElMessage.error(error.message || '无法加载笔试');
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
    if (result.value.status === 'passed') ElMessage.success('可见示例全部通过');
    else ElMessage.warning('还有用例没通过，看看下面的差异');
  } catch (error) {
    ElMessage.error(error.message || '运行失败');
  } finally {
    busy.value = false;
  }
}

async function submit() {
  if (!current.value) return;
  const answer = payloadFor(current.value);
  if (!answer || answer === '[]') {
    ElMessage.warning('先作答再提交');
    return;
  }
  busy.value = true;
  result.value = null;
  try {
    result.value = await submitCodingAnswer(session.value.id, current.value.id, answer);
    const passed = result.value.status === 'passed';
    if (passed) ElMessage.success('本题通过');
    else ElMessage.warning(`本题未通过（${result.value.passedCases}/${result.value.totalCases} 个用例）`);
    await refreshSummary();
  } catch (error) {
    ElMessage.error(error.message || '提交失败');
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
  await ElMessageBox.confirm('结束后会生成笔试报告，之后仍可回来修改。', '结束笔试', { confirmButtonText: '结束并查看报告', cancelButtonText: '继续作答' });
  router.push(`/coding/${session.value.id}/report`);
}

function caseLabel(item) {
  return item.hidden ? `隐藏用例 ${item.index}` : `用例 ${item.index}${item.name ? ` · ${item.name}` : ''}`;
}
</script>

<template>
  <section v-loading="loading" class="coding-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">CODING ASSESSMENT</div>
        <h1>笔试作答</h1>
        <p v-if="session">{{ session.role }} · 共 {{ questions.length }} 题 · 数据保留至 {{ String(session.retentionUntil).slice(0, 10) }}</p>
      </div>
      <div class="heading-actions">
        <el-button class="soft-button" @click="router.push('/coding')">返回列表</el-button>
        <el-button class="primary-button" @click="finish">结束笔试</el-button>
      </div>
    </div>

    <div v-if="session" class="coding-layout">
      <aside class="question-rail panel">
        <div class="rail-header"><span>题目</span><strong>{{ questions.length }}</strong></div>
        <button
          v-for="(question, target) in questions"
          :key="question.id"
          class="question-nav"
          :class="{ active: target === index, done: submissionFor(question.id) }"
          @click="selectQuestion(target)"
        >
          <span class="question-index">{{ submissionFor(question.id) ? '✓' : String(target + 1).padStart(2, '0') }}</span>
          <span>
            <small>{{ typeLabels[question.type] }}{{ question.language ? ` · ${languageLabels[question.language] || question.language}` : '' }}</small>
            <strong>{{ question.title }}</strong>
          </span>
        </button>
      </aside>

      <div v-if="current" class="coding-main">
        <section class="panel question-panel">
          <div class="question-head">
            <span class="question-kicker">第 {{ index + 1 }} 题 · {{ typeLabels[current.type] }}<template v-if="current.language"> · {{ languageLabels[current.language] || current.language }}</template></span>
            <el-tag v-if="submissionFor(current.id)" :class="submissionFor(current.id).status === 'passed' ? 'tag-ready' : 'tag-warn'" size="small">
              {{ submissionFor(current.id).status === 'passed' ? '已通过' : `${submissionFor(current.id).passedCases}/${submissionFor(current.id).totalCases} 通过` }}
            </el-tag>
          </div>
          <h2>{{ current.title }}</h2>
          <pre class="statement">{{ current.statement }}</pre>

          <div v-if="current.visibleTestCases?.length" class="samples">
            <div class="samples-title">示例</div>
            <div v-for="(sample, sampleIndex) in current.visibleTestCases" :key="sampleIndex" class="sample-row">
              <span>{{ sample.name || `示例 ${sampleIndex + 1}` }}</span>
              <code>输入：{{ sample.stdin || '(无)' }}</code>
              <code>输出：{{ sample.expectedStdout }}</code>
            </div>
          </div>

          <div v-if="current.type === 'sql' && current.sqlSetup" class="sql-setup">
            <div class="samples-title">表结构与数据</div>
            <pre>{{ current.sqlSetup }}</pre>
          </div>
        </section>

        <section class="panel answer-panel">
          <div class="answer-title">你的作答</div>

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
                <span>第 {{ blank.index + 1 }} 空<small v-if="blank.hint">（{{ blank.hint }}）</small></span>
                <el-input v-model="extra[current.id][blankIndex]" placeholder="填写这一空的内容" />
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
            <el-input v-model="answers[current.id]" type="textarea" :rows="8" maxlength="4000" show-word-limit placeholder="写清楚你的思路与结论" />
          </template>

          <div class="answer-actions">
            <el-button
              v-if="current.type === 'coding' || current.type === 'sql'"
              class="soft-button"
              :loading="busy"
              @click="run"
            >运行示例</el-button>
            <el-button class="primary-button" :loading="busy" @click="submit">提交本题</el-button>
            <span class="answer-hint">运行只跑可见示例、不留记录；提交会跑全部用例（含隐藏）并计分。</span>
          </div>
        </section>

        <section v-if="result" class="panel result-panel">
          <div class="result-head">
            <strong>判分结果</strong>
            <el-tag :class="result.status === 'passed' ? 'tag-ready' : 'tag-warn'" size="small">
              {{ result.status === 'passed' ? '通过' : result.status === 'error' ? '运行错误' : '未通过' }}
            </el-tag>
            <span class="result-score">{{ result.score ?? 0 }} 分</span>
            <span class="result-cases">{{ result.passedCases }}/{{ result.totalCases }} 用例</span>
            <span v-if="result.runtimeMs" class="result-cases">耗时 {{ result.runtimeMs }} ms</span>
          </div>

          <pre v-if="result.compileOutput" class="console-output error">{{ result.compileOutput }}</pre>
          <pre v-if="result.stdout" class="console-output">{{ result.stdout }}</pre>
          <pre v-if="result.stderr" class="console-output error">{{ result.stderr }}</pre>

          <div v-if="result.review" class="review-box">
            <div class="review-score">模型评分：{{ result.review.score }}</div>
            <p>{{ result.review.summary }}</p>
            <div v-if="result.review.matched?.length" class="review-list"><span>已覆盖：</span>{{ result.review.matched.join('、') }}</div>
            <div v-if="result.review.missing?.length" class="review-list"><span>未覆盖：</span>{{ result.review.missing.join('、') }}</div>
          </div>

          <div v-if="result.cases?.length" class="case-list">
            <div v-for="item in result.cases" :key="item.index" class="case-row" :class="{ failed: !item.passed }">
              <span class="case-name">{{ caseLabel(item) }}</span>
              <span class="case-status">{{ item.passed ? '通过' : item.message || '未通过' }}</span>
              <template v-if="!item.passed && !item.hidden">
                <code>输入：{{ item.input || '(无)' }}</code>
                <code>期望：{{ item.expected }}</code>
                <code>实际：{{ item.actual || '(无输出)' }}</code>
              </template>
              <small v-else-if="!item.passed && item.hidden">隐藏用例不展示输入输出</small>
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
.rail-header { display: flex; justify-content: space-between; padding: 0 14px 10px; color: #59655e; font-size: 12px; }
.question-nav { display: flex; width: 100%; align-items: center; gap: 9px; padding: 11px 12px; border: 0; border-left: 2px solid transparent; background: #fff; text-align: left; cursor: pointer; }
.question-nav:hover { background: #fafcfb; }
.question-nav.active { border-left-color: var(--green); background: #f1f7f3; }
.question-nav > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 4px; }
.question-nav small { color: #98a19c; font-size: 11px; }
.question-nav strong { overflow: hidden; color: #616d66; font-size: 12px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.question-nav.active strong { color: #35433b; font-weight: 600; }
.question-index { display: grid; width: 24px; height: 24px; flex: none; place-items: center; border: 1px solid #e4e9e5; border-radius: 50%; color: #98a39d; font-size: 11px; }
.question-nav.done .question-index { border-color: #d5eadb; background: var(--green-soft); color: var(--green); }
.coding-main { display: grid; gap: 12px; }
.question-panel, .answer-panel, .result-panel { padding: 16px 18px; box-shadow: none; }
.question-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.question-kicker { color: var(--green); font-size: 12px; font-weight: 600; }
.question-panel h2 { margin: 10px 0 8px; color: #2f3c35; font-size: 17px; font-weight: 650; }
.statement { margin: 0; color: #47534d; font-family: inherit; font-size: 13px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.blank-code { margin-bottom: 12px; padding: 12px; border-radius: 6px; background: #f7faf8; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
.samples, .sql-setup { margin-top: 14px; }
.samples-title { margin-bottom: 6px; color: #59655e; font-size: 12px; font-weight: 600; }
.sample-row { display: grid; gap: 3px; padding: 8px 10px; border-left: 2px solid var(--green-soft); background: #fafcfb; }
.sample-row span { color: #6b7770; font-size: 11px; }
.sample-row code { color: #3f4c45; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
.sql-setup pre { margin: 0; overflow-x: auto; padding: 10px; border-radius: 6px; background: #f7faf8; color: #47534d; font-size: 12px; }
.answer-title { margin-bottom: 10px; color: #35433b; font-size: 13px; font-weight: 600; }
.blank-list { display: grid; gap: 10px; }
.blank-row { display: grid; gap: 5px; }
.blank-row > span { color: #59655e; font-size: 12px; }
.blank-row small { color: #8d9892; }
.choice-list { display: grid; gap: 8px; }
.answer-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 14px; }
.answer-hint { color: #8a958e; font-size: 11px; }
.result-head { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.result-head strong { color: #35433b; font-size: 13px; }
.result-score { color: var(--green); font-size: 15px; font-weight: 650; }
.result-cases { color: #7b877f; font-size: 12px; }
.console-output { margin: 10px 0 0; max-height: 220px; overflow: auto; padding: 10px; border-radius: 6px; background: #f6f8f6; color: #3f4c45; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; white-space: pre-wrap; }
.console-output.error { background: #fff4f1; color: #a4483a; }
.review-box { margin-top: 12px; padding: 12px; border: 1px solid #e2ece6; border-radius: 6px; background: #f7fbf8; }
.review-score { color: var(--green); font-weight: 650; }
.review-box p { margin: 6px 0; color: #47534d; font-size: 12px; line-height: 1.7; }
.review-list { color: #6b7770; font-size: 12px; }
.review-list span { color: #59655e; }
.case-list { display: grid; gap: 8px; margin-top: 12px; }
.case-row { display: grid; gap: 3px; padding: 9px 11px; border-left: 2px solid #d5eadb; background: #fafcfb; }
.case-row.failed { border-left-color: #e8b4a6; background: #fffaf8; }
.case-name { color: #3f4c45; font-size: 12px; font-weight: 600; }
.case-status { color: #6b7770; font-size: 12px; }
.case-row.failed .case-status { color: #a4483a; }
.case-row code { color: #47534d; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
.case-row small { color: #8d9892; font-size: 11px; }
@media (max-width: 900px) { .coding-layout { grid-template-columns: 1fr; } }
</style>
