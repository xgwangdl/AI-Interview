<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createCodingSession, deleteCodingSession, getCodingCapabilities, listCodingSessions } from '../services/coding';

const router = useRouter();
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
const typeLabels = { coding: '上机编程', sql: '数据库题', fill_blank: '编程填空', choice: '选择题', short_answer: '简答题' };
const typeHints = {
  coding: '写完整程序，跑测试用例判分',
  sql: '写 SQL 查询并比对结果集',
  fill_blank: '补全代码中的空位',
  choice: '选择正确选项（可多选）',
  short_answer: '简答，由模型按要点评分',
};

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const [list, caps] = await Promise.all([listCodingSessions(), getCodingCapabilities()]);
    sessions.value = list || [];
    capabilities.value = caps || capabilities.value;
  } catch (error) {
    ElMessage.error(error.message || '无法加载笔试列表');
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
    ElMessage.warning('先填写目标岗位');
    return;
  }
  if (!form.questions.length) {
    ElMessage.warning('至少添加一道题');
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
    ElMessage.success('题目已生成');
    router.push(`/coding/${session.id}`);
  } catch (error) {
    ElMessage.error(error.message || '出题失败，请稍后重试');
  } finally {
    busy.value = false;
    window.clearInterval(elapsedTimer);
  }
}

async function remove(session) {
  await ElMessageBox.confirm('删除后题目与作答记录都会消失。', '删除这次笔试', { type: 'warning' });
  try {
    await deleteCodingSession(session.id);
    sessions.value = sessions.value.filter((item) => item.id !== session.id);
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
}
</script>

<template>
  <section v-loading="loading" class="coding-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">CODING ASSESSMENT</div>
        <h1>笔试练习</h1>
        <p>AI 出题（参考解自检通过才会出给你），支持上机编程、数据库、填空、选择与简答。</p>
      </div>
      <el-tag :class="capabilities.codeRunnerEnabled ? 'tag-ready' : 'tag-muted'">
        {{ capabilities.codeRunnerEnabled ? '代码沙箱已启用' : '代码沙箱未启用（只能出题不能运行）' }}
      </el-tag>
    </div>

    <div class="setup-grid">
      <section class="panel setup-panel">
        <div class="panel-title">出题条件</div>
        <el-form label-position="top">
          <el-form-item label="目标岗位">
            <el-input v-model="form.role" maxlength="80" placeholder="例如：Java 后端工程师" />
          </el-form-item>
          <div class="field-grid">
            <el-form-item label="目标公司（选填）">
              <el-input v-model="form.company" maxlength="80" placeholder="例如：某互联网公司" />
            </el-form-item>
            <el-form-item label="难度">
              <el-select v-model="form.difficulty">
                <el-option label="基础" value="foundation" />
                <el-option label="标准" value="standard" />
                <el-option label="进阶" value="advanced" />
              </el-select>
            </el-form-item>
          </div>
          <el-form-item label="岗位描述 / JD（选填，填了题目会更贴）">
            <el-input v-model="form.jobDescription" type="textarea" :rows="4" maxlength="3000" show-word-limit placeholder="粘贴岗位职责与技能要求" />
          </el-form-item>
        </el-form>
      </section>

      <section class="panel setup-panel">
        <div class="panel-title">题目组合（{{ form.questions.length }} 题）</div>
        <div class="add-buttons">
          <el-button v-for="(label, type) in typeLabels" :key="type" class="soft-button" @click="addQuestion(type)">
            <el-icon><Plus /></el-icon>{{ label }}
          </el-button>
        </div>
        <div class="question-rows">
          <div v-for="(question, questionIndex) in form.questions" :key="questionIndex" class="question-row">
            <span class="row-index">{{ questionIndex + 1 }}</span>
            <div class="row-body">
              <select v-model="question.type" class="row-select">
                <option v-for="(label, type) in typeLabels" :key="type" :value="type">{{ label }}</option>
              </select>
              <select v-if="question.type === 'coding'" v-model="question.language" class="row-select">
                <option v-for="language in languageOptions" :key="language" :value="language">{{ languageLabels[language] || language }}</option>
              </select>
              <small>{{ typeHints[question.type] }}</small>
            </div>
            <button class="row-remove" type="button" aria-label="移除" @click="removeQuestion(questionIndex)">×</button>
          </div>
        </div>
        <el-button class="primary-button create-button" :loading="busy" @click="create">
          {{ busy ? `生成中… 已等待 ${elapsed} 秒` : '生成题目并开始' }} <el-icon v-if="!busy"><ArrowRight /></el-icon>
        </el-button>
        <p class="runner-note">
          出题时会用模型给的参考解在沙箱里跑一遍用例，全部通过才会发给你；没通过会换一批重试（最多 3 次），
          所以可能要等 20 秒到 2 分钟。左侧按钮上的秒数是真实等待时间，不是卡住了。
        </p>
      </section>
    </div>

    <section class="panel history-panel">
      <div class="panel-title">历史笔试</div>
      <el-empty v-if="!sessions.length" description="还没有笔试记录" />
      <div v-for="session in sessions" :key="session.id" class="history-row">
        <div>
          <strong>{{ session.role }}</strong>
          <small>{{ session.questions.length }} 题 · {{ String(session.createdAt).slice(0, 10) }} · 保留至 {{ String(session.retentionUntil).slice(0, 10) }}</small>
        </div>
        <div class="history-actions">
          <el-button class="soft-button" @click="router.push(`/coding/${session.id}`)">继续作答</el-button>
          <el-button class="soft-button" @click="router.push(`/coding/${session.id}/report`)">查看报告</el-button>
          <el-button class="soft-button danger" @click="remove(session)">删除</el-button>
        </div>
      </div>
    </section>
  </section>
</template>

<style scoped>
.coding-page { display: grid; gap: 14px; }
.setup-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 14px; }
.setup-panel, .history-panel { padding: 18px; box-shadow: none; }
.panel-title { margin-bottom: 14px; color: #35433b; font-size: 14px; font-weight: 600; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.add-buttons { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.question-rows { display: grid; gap: 8px; }
.question-row { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #e6ebe7; border-radius: 6px; background: #fafcfb; }
.row-index { display: grid; width: 22px; height: 22px; flex: none; place-items: center; border-radius: 50%; background: var(--green-soft); color: var(--green); font-size: 11px; }
.row-body { display: flex; flex: 1; flex-wrap: wrap; align-items: center; gap: 8px; }
.row-body small { color: #8d9892; font-size: 12px; }
.row-select { height: 30px; padding: 0 8px; border: 1px solid #dfe6e1; border-radius: 5px; background: #fff; color: #3f4c45; font-size: 12px; }
.row-remove { width: 26px; height: 26px; border: 0; border-radius: 50%; background: transparent; color: #98a19c; font-size: 16px; cursor: pointer; }
.row-remove:hover { background: #f2f5f3; color: #a4483a; }
.create-button { width: 100%; margin-top: 16px; }
.runner-note { margin: 10px 0 0; color: #8d9892; font-size: 12px; line-height: 1.7; }
.history-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-top: 1px solid #eef1ee; }
.history-row strong { display: block; color: #35433b; font-size: 13px; }
.history-row small { color: #8d9892; font-size: 12px; }
.history-actions { display: flex; gap: 8px; }
.soft-button.danger { color: #a4483a; }
@media (max-width: 900px) { .setup-grid { grid-template-columns: 1fr; } .history-row { flex-direction: column; align-items: flex-start; } }
</style>
