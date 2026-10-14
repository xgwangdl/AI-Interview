<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getCodingReport } from '../services/coding';

const route = useRoute();
const router = useRouter();
const report = ref(null);
const loading = ref(true);

const typeLabels = { coding: '上机编程', sql: '数据库题', fill_blank: '编程填空', choice: '选择题', short_answer: '简答题' };
const languageLabels = { java: 'Java', python: 'Python', cpp: 'C++', javascript: 'JavaScript', go: 'Go', sql: 'SQL' };
const statusLabels = { passed: '已通过', failed: '未通过', error: '运行错误', unanswered: '未作答' };

onMounted(async () => {
  try {
    report.value = await getCodingReport(route.params.id);
  } catch (error) {
    ElMessage.error(error.message || '无法加载报告');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section v-loading="loading" class="coding-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">CODING REPORT</div>
        <h1>笔试报告</h1>
        <p v-if="report">{{ report.role }} · 共 {{ report.totalQuestions }} 题 · 已答 {{ report.answeredQuestions }} 题 · 通过 {{ report.passedQuestions }} 题</p>
      </div>
      <div class="heading-actions">
        <el-button class="soft-button" @click="router.push('/coding')">返回列表</el-button>
        <el-button class="primary-button" @click="router.push(`/coding/${route.params.id}`)">继续作答</el-button>
      </div>
    </div>

    <section v-if="report" class="panel summary-panel">
      <div class="summary-score">
        <strong>{{ report.averageScore ?? '—' }}</strong>
        <span>平均得分</span>
      </div>
      <div class="summary-item"><span>题目</span><strong>{{ report.totalQuestions }}</strong></div>
      <div class="summary-item"><span>已作答</span><strong>{{ report.answeredQuestions }}</strong></div>
      <div class="summary-item"><span>通过</span><strong>{{ report.passedQuestions }}</strong></div>
      <p class="summary-note">客观题按通过的测试用例比例计分；简答题由模型按参考要点评分。「运行示例」不计入报告，只有「提交本题」才会落库。</p>
    </section>

    <section v-for="question in report?.questions || []" :key="question.questionId" class="panel question-report">
      <div class="report-head">
        <span class="report-index">{{ String(question.position).padStart(2, '0') }}</span>
        <div>
          <strong>{{ question.title }}</strong>
          <small>{{ typeLabels[question.type] }}<template v-if="question.language"> · {{ languageLabels[question.language] || question.language }}</template></small>
        </div>
        <el-tag :class="question.status === 'passed' ? 'tag-ready' : question.status === 'unanswered' ? 'tag-muted' : 'tag-warn'" size="small">
          {{ statusLabels[question.status] || question.status }}
        </el-tag>
        <span class="report-score">{{ question.score ?? '—' }} 分</span>
        <span class="report-cases">{{ question.passedCases }}/{{ question.totalCases }} 用例</span>
        <span v-if="question.runtimeMs" class="report-cases">{{ question.runtimeMs }} ms</span>
      </div>

      <pre class="statement">{{ question.statement }}</pre>

      <div v-if="question.review" class="review-box">
        <div class="review-score">模型评分：{{ question.review.score }}</div>
        <p>{{ question.review.summary }}</p>
        <div v-if="question.review.matched?.length" class="review-list"><span>已覆盖：</span>{{ question.review.matched.join('、') }}</div>
        <div v-if="question.review.missing?.length" class="review-list"><span>未覆盖：</span>{{ question.review.missing.join('、') }}</div>
      </div>

      <details v-if="question.answerText" class="answer-details">
        <summary>查看提交内容</summary>
        <pre>{{ question.answerText }}</pre>
      </details>
    </section>
  </section>
</template>

<style scoped>
.coding-page { display: grid; gap: 14px; }
.heading-actions { display: flex; gap: 8px; }
.summary-panel { display: flex; flex-wrap: wrap; align-items: center; gap: 26px; padding: 18px; box-shadow: none; }
.summary-score { display: grid; gap: 2px; }
.summary-score strong { color: var(--green); font-size: 30px; font-weight: 650; }
.summary-score span, .summary-item span { color: #8d9892; font-size: 12px; }
.summary-item { display: grid; gap: 2px; }
.summary-item strong { color: #35433b; font-size: 16px; }
.summary-note { width: 100%; margin: 0; color: #8d9892; font-size: 12px; line-height: 1.7; }
.question-report { padding: 16px 18px; box-shadow: none; }
.report-head { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.report-index { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 50%; background: #f2f6f3; color: #6b7770; font-size: 12px; }
.report-head strong { display: block; color: #35433b; font-size: 14px; }
.report-head small { color: #8d9892; font-size: 12px; }
.report-score { color: var(--green); font-size: 15px; font-weight: 650; }
.report-cases { color: #7b877f; font-size: 12px; }
.statement { margin: 12px 0 0; color: #47534d; font-family: inherit; font-size: 12px; line-height: 1.75; white-space: pre-wrap; word-break: break-word; }
.review-box { margin-top: 12px; padding: 12px; border: 1px solid #e2ece6; border-radius: 6px; background: #f7fbf8; }
.review-score { color: var(--green); font-weight: 650; }
.review-box p { margin: 6px 0; color: #47534d; font-size: 12px; line-height: 1.7; }
.review-list { color: #6b7770; font-size: 12px; }
.answer-details { margin-top: 12px; }
.answer-details summary { color: #59655e; font-size: 12px; cursor: pointer; }
.answer-details pre { margin: 8px 0 0; max-height: 260px; overflow: auto; padding: 10px; border-radius: 6px; background: #f7faf8; color: #3f4c45; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; white-space: pre-wrap; }
</style>
