<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { usePracticeStore } from '../stores/practice';
import { buildArchive, loadArchive, trendPoints } from '../services/practiceArchive';
import { buildMistakes, loadMistakes } from '../services/practiceMistakes';
import { ElMessage } from 'element-plus';
import {
  getTodayPlan, goalProgressPercent, interviewLabel, listGoals, PLAN_KIND_LABELS, startPlanItem,
} from '../services/goals';

const router = useRouter();
const practice = usePracticeStore();
const archive = ref(null);
const archiveLoading = ref(true);
const mistakeBook = ref(null);
const retrying = ref('');
const goals = ref([]);
const todayPlan = ref(null);
const startingPlanItem = ref('');

// 折线画布用固定尺寸 + 自己算坐标：省一个图表库依赖，也不用联网装包
const TREND_WIDTH = 320;
const TREND_HEIGHT = 108;
const trendDots = computed(() => trendPoints(archive.value?.trend, TREND_WIDTH, TREND_HEIGHT));
const trendPath = computed(() => trendDots.value.map((dot) => `${dot.x},${dot.y}`).join(' '));

onMounted(() => {
  loadArchiveSummary();
  loadMistakeBook();
  loadGoalAndPlan();
});

const primaryGoal = computed(() => goals.value.find((goal) => goal.status !== 'archived') || null);
const goalPercent = computed(() => goalProgressPercent(primaryGoal.value?.progress));

/** 首页第一屏：目标卡 + 今日 3 题。没有目标也能拿到通用计划。 */
async function loadGoalAndPlan() {
  try {
    goals.value = await listGoals();
    todayPlan.value = await getTodayPlan();
  } catch {
    goals.value = [];
    todayPlan.value = null;
  }
}

async function startTodayItem(item) {
  if (startingPlanItem.value) return;
  startingPlanItem.value = item.id;
  try {
    const session = await startPlanItem(item.id);
    await practice.loadFromServer();
    router.push(`/practice/device-check?id=${session.id}`);
  } catch (error) {
    ElMessage.error(error?.message || '无法开始这一题');
  } finally {
    startingPlanItem.value = '';
  }
}

function createPractice() {
  router.push(primaryGoal.value ? `/practice/setup?goalId=${primaryGoal.value.id}` : '/practice/setup');
}

async function loadArchiveSummary() {
  archiveLoading.value = true;
  try {
    archive.value = buildArchive(await loadArchive(5));
  } catch {
    // 档案是增强项：拿不到就不显示这一块，不影响练习列表
    archive.value = null;
  } finally {
    archiveLoading.value = false;
  }
}

/**
 * 错题本。
 *
 * 和训练档案一样是增强项：拿不到就不显示这一块，不能拖累练习列表。
 */
async function loadMistakeBook() {
  try {
    // 总览页只看最差的前 5 条：一次列几十条会让人不想开始（更多的走 /mistakes）
    mistakeBook.value = buildMistakes(await loadMistakes({ limit: 5 }));
  } catch {
    mistakeBook.value = null;
  }
}

/** 重练这道题：store 里建好练习并刷新本地列表（设备检测页是从本地列表里找这场练习的）。 */
async function retryMistakeQuestion(item) {
  if (retrying.value) return;
  retrying.value = item.questionId;
  try {
    const sessionId = await practice.retryMistake(item.questionId);
    router.push(`/practice/device-check?id=${sessionId}`);
  } catch (error) {
    ElMessage.error(error?.message || '暂时无法开始这次重练，请稍后再试。');
  } finally {
    retrying.value = '';
  }
}

/** 针对短板再练一场：带上岗位与弱项维度，出题时会多出能暴露这些问题的题。 */
function retryWeakDimensions() {
  const query = { role: archive.value?.latestRole || '' };
  if (archive.value?.focusKeys?.length) query.focus = archive.value.focusKeys.join(',');
  router.push({ path: '/practice/setup', query });
}
const latest = computed(() => practice.sessions.slice(0, 4));
// 累计的是「实际录制时长」，不是创建练习时填的预计时长。
const recordedSeconds = computed(() => practice.completed.reduce(
  (total, session) => total + (session.answers || []).reduce((sum, answer) => sum + (answer.durationSeconds || 0), 0),
  0));
const recordedLabel = computed(() => (recordedSeconds.value >= 60
  ? { value: Math.round(recordedSeconds.value / 60), unit: '分钟' }
  : { value: recordedSeconds.value, unit: '秒' }));
const dateLabel = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date());

function openSession(session) {
  router.push(session.status === 'completed' ? `/practice/${session.id}/report` : `/practice/${session.id}/session`);
}

function timeLabel(value) {
  return new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(value));
}
</script>

<template>
  <section class="dashboard-page">
    <div class="page-heading dashboard-heading">
      <div><div class="eyebrow">{{ dateLabel }}</div><h1>每一次练习，都让表达更清晰</h1><p>从一个真实岗位开始，循序完成练习、录制与复盘。</p></div>
      <el-button class="primary-button" @click="createPractice"><el-icon><Plus /></el-icon> 创建练习</el-button>
    </div>

    <section class="goal-today-grid">
      <article class="panel goal-focus">
        <template v-if="primaryGoal">
          <div class="goal-focus-head">
            <span>当前目标</span>
            <el-tag class="tag-ready" size="small">{{ interviewLabel(primaryGoal.daysUntilInterview) }}</el-tag>
          </div>
          <strong class="goal-focus-title">{{ primaryGoal.company || '目标岗位' }} · {{ primaryGoal.role }}</strong>
          <div class="goal-focus-progress">
            <span><i :style="{ width: `${goalPercent}%` }"></i></span>
            <small>{{ primaryGoal.progress?.planCompleted || 0 }}/{{ primaryGoal.progress?.planItems || 0 }} 训练项 · {{ primaryGoal.progress?.sessions || 0 }} 场练习</small>
          </div>
          <button class="goal-focus-link" @click="router.push(`/goals/${primaryGoal.id}`)">
            查看目标与计划 <el-icon><ArrowRight /></el-icon>
          </button>
        </template>
        <template v-else>
          <div class="goal-focus-head"><span>当前目标</span></div>
          <strong class="goal-focus-title">还没有目标岗位</strong>
          <p class="goal-focus-empty">添加岗位和面试日期，首页就会告诉你今天练什么。</p>
          <el-button class="primary-button" @click="router.push('/goals/new')">添加求职目标</el-button>
        </template>
      </article>

      <article class="panel today-focus">
        <div class="today-focus-head">
          <div><strong>今天练什么</strong><small>{{ todayPlan?.goalTitle || '通用训练' }} · 连续 {{ todayPlan?.streak?.current || 0 }} 天</small></div>
          <button class="link-button" @click="router.push('/today')">查看全部 <el-icon><ArrowRight /></el-icon></button>
        </div>
        <div class="today-focus-list">
          <div v-for="(item, index) in todayPlan?.items || []" :key="item.id" class="today-focus-row" :class="item.status">
            <span class="today-index">{{ index + 1 }}</span>
            <span class="today-main"><small>{{ PLAN_KIND_LABELS[item.kind] || item.kind }}</small><strong>{{ item.title }}</strong></span>
            <el-button v-if="item.status === 'pending' || item.status === 'in_progress'" class="soft-button"
                       :loading="startingPlanItem === item.id" @click="startTodayItem(item)">开始</el-button>
            <span v-else class="today-done">{{ item.status === 'completed' ? '已完成' : '已跳过' }}</span>
          </div>
          <p v-if="!todayPlan?.items?.length" class="today-empty">计划正在生成；也可以先点「创建练习」。</p>
        </div>
      </article>
    </section>

    <div class="overview-grid">
      <article class="overview-card panel">
        <div class="overview-card-label"><span>完成练习</span><i class="icon-green"><el-icon><Checked /></el-icon></i></div>
        <div class="overview-number">{{ practice.completed.length }}<small> 次</small></div>
        <div class="overview-foot">每一次回答都值得复盘</div>
      </article>
      <article class="overview-card panel">
        <div class="overview-card-label"><span>练习时长</span><i class="icon-blue"><el-icon><Timer /></el-icon></i></div>
        <div class="overview-number">{{ recordedLabel.value }}<small> {{ recordedLabel.unit }}</small></div>
        <div class="overview-foot">累计录制与练习时间</div>
      </article>
      <article class="overview-card panel next-practice-card">
        <div class="overview-card-label"><span>下一步</span><i class="icon-coral"><el-icon><Microphone /></el-icon></i></div>
        <div class="next-practice-copy">用视频模拟一次<br />真实面试现场</div>
        <button class="text-action" @click="createPractice">配置岗位与题目 <el-icon><ArrowRight /></el-icon></button>
      </article>
    </div>

    <section v-if="archive" class="archive-section">
      <div class="section-title">
        <span>训练档案</span>
        <small class="archive-scope">{{ archive.scopeNote }}</small>
        <button class="weekly-link" @click="router.push('/progress/weekly')">本周报告 <el-icon><ArrowRight /></el-icon></button>
      </div>

      <div v-if="archive.scoredSessions" class="archive-grid">
        <article class="panel archive-card">
          <div class="archive-card-head">
            <strong>最近得分</strong>
            <span>{{ archive.scoredSessions }} 场 · {{ archive.evaluatedAnswers }} 个回答已评分</span>
          </div>
          <svg class="trend-svg" :viewBox="`0 0 ${TREND_WIDTH} ${TREND_HEIGHT}`" role="img" aria-label="最近练习得分趋势">
            <polyline v-if="trendDots.length > 1" :points="trendPath" class="trend-line" />
            <g v-for="(dot, index) in trendDots" :key="index">
              <circle :cx="dot.x" :cy="dot.y" r="3.6" class="trend-dot" />
              <text :x="dot.x" :y="dot.y - 8" text-anchor="middle" class="trend-score">{{ dot.score }}</text>
              <text :x="dot.x" :y="TREND_HEIGHT - 1" text-anchor="middle" class="trend-label">{{ dot.label }}</text>
            </g>
          </svg>
          <p v-if="archive.needsMoreSessions" class="archive-hint">
            再完成一场练习就能看到趋势；现在这个点是最近一场的成绩。
          </p>
        </article>

        <article class="panel archive-card">
          <div class="archive-card-head"><strong>维度平均</strong><span>橙色的是需要重点练的</span></div>
          <div class="dimension-list">
            <div v-for="item in archive.dimensions" :key="item.key" class="dimension-row"
                 :class="{ weak: archive.focusKeys.includes(item.key) }">
              <span class="dimension-name">{{ item.label }}</span>
              <span class="dimension-bar"><i :style="{ width: `${Math.min(item.average, 100)}%` }"></i></span>
              <strong>{{ item.average }}</strong>
            </div>
          </div>
        </article>

        <article class="panel archive-card">
          <div class="archive-card-head"><strong>下一步练什么</strong></div>
          <template v-if="archive.weakest.length">
            <ul class="weakest-list">
              <li v-for="item in archive.weakest" :key="item.key">
                <div class="weakest-head"><strong>{{ item.label }}</strong><span>{{ item.average }} 分</span></div>
                <p>{{ item.advice }}</p>
              </li>
            </ul>
            <el-button class="primary-button" @click="retryWeakDimensions">
              <el-icon><Aim /></el-icon> 针对短板再练一场
            </el-button>
            <p class="archive-hint">再练时会多出能暴露这些问题的题，岗位沿用「{{ archive.latestRole }}」。</p>
          </template>
          <template v-else>
            <p class="archive-hint">最近这几个维度都在 75 分以上，没有明显短板。可以试着提高难度。</p>
            <el-button class="soft-button" @click="router.push('/practice/setup')">提高难度再练一场</el-button>
          </template>
        </article>
      </div>

      <div v-else class="panel archive-empty">
        <strong>还没有可复盘的练习</strong>
        <p>完成一场练习、等分析跑完后，这里会出现你的成绩趋势、维度平均，以及下一步该重点练什么。</p>
        <el-button class="soft-button" @click="router.push('/practice/setup')">开始第一次练习</el-button>
      </div>
    </section>

    <section v-if="mistakeBook && mistakeBook.total" class="mistake-section">
      <div class="section-title">
        <span>错题本</span>
        <small class="archive-scope">{{ mistakeBook.scopeNote }}</small>
      </div>
      <div class="panel mistake-list">
        <div v-for="item in mistakeBook.mistakes" :key="item.questionId" class="mistake-row">
          <span class="mistake-score">
            <strong>{{ item.label }}</strong>
            <small v-if="item.gap">还差 {{ item.gap }} 分</small>
            <small v-else>待评分</small>
          </span>
          <span class="mistake-main">
            <strong>{{ item.text }}</strong>
            <small>
              <el-tag class="mistake-tag" size="small">{{ item.category }}</el-tag>
              {{ item.role }}<template v-if="item.difficultyLabel"> · {{ item.difficultyLabel }}</template><template v-if="item.weakestLabel"> · 主要栽在{{ item.weakestLabel }}</template>
              · {{ item.attemptsLabel }}
            </small>
          </span>
          <el-button class="soft-button mistake-action" :loading="retrying === item.questionId"
                     @click="retryMistakeQuestion(item)">
            <el-icon><Refresh /></el-icon> 重练这道题
          </el-button>
        </div>
        <p v-if="mistakeBook.total > mistakeBook.mistakes.length" class="mistake-all-row">
          <button class="all-mistakes" @click="router.push('/mistakes')">
            查看全部 {{ mistakeBook.total }} 道 <el-icon><ArrowRight /></el-icon>
          </button>
        </p>
      </div>
    </section>

    <div class="dashboard-lower">
      <section class="recent-section">
        <div class="section-title"><span>最近练习</span><button class="link-button" @click="router.push('/history')">全部记录 <el-icon><ArrowRight /></el-icon></button></div>
        <div v-if="latest.length" class="recent-list panel">
          <button v-for="session in latest" :key="session.id" class="recent-row" @click="openSession(session)">
            <div class="recent-symbol" :class="session.status"><el-icon><VideoCamera /></el-icon></div>
            <div class="recent-main"><strong>{{ session.role }}</strong><span>{{ session.company || '自主练习' }} · {{ session.questionCount }} 道题</span></div>
            <el-tag v-if="session.status === 'completed'" class="status-done" size="small">已完成</el-tag>
            <el-tag v-else class="status-progress" size="small">进行中</el-tag>
            <span class="recent-date">{{ timeLabel(session.createdAt) }}</span><el-icon class="recent-arrow"><ArrowRight /></el-icon>
          </button>
        </div>
        <div v-else class="empty-recent panel">
          <div class="empty-state-icon"><el-icon><VideoCamera /></el-icon></div>
          <strong>还没有练习记录</strong><p>从目标岗位和面试类型开始，完成第一段模拟练习。</p>
          <el-button class="soft-button" @click="router.push('/practice/setup')">开始第一次练习</el-button>
        </div>
      </section>

      <aside class="guidance-section">
        <div class="section-title">练习准备</div>
        <div class="guidance-panel panel">
          <div class="guidance-art"><div class="art-person"><span class="person-head"></span><span class="person-body"></span><span class="person-neck"></span></div><div class="art-window"><i></i><i></i><i></i></div><div class="art-spark spark-a">✦</div><div class="art-spark spark-b">✦</div></div>
          <strong>让练习尽量接近现场</strong>
          <p>准备岗位描述和简历，开启摄像头与麦克风后，每道题都可以单独录制与重来。</p>
          <div class="guidance-meta"><span><el-icon><Clock /></el-icon>建议 15–30 分钟</span><span><el-icon><Lock /></el-icon>录制保留 7 天</span></div>
        </div>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.goal-today-grid { display: grid; grid-template-columns: minmax(0, .72fr) minmax(0, 1.28fr); gap: 15px; margin-bottom: 18px; }
.goal-focus, .today-focus { padding: 15px 18px; box-shadow: none; }
.goal-focus { display: grid; align-content: start; gap: 9px; }
.goal-focus-head, .today-focus-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.goal-focus-head > span { color: #8d9792; font-size: 12px; }
.goal-focus-title { color: #2f3c35; font-size: 17px; font-weight: 650; line-height: 1.4; }
.goal-focus-progress { display: grid; gap: 5px; }
.goal-focus-progress > span { display: block; height: 6px; overflow: hidden; border-radius: 3px; background: #eef1ee; }
.goal-focus-progress i { display: block; height: 100%; background: var(--green); }
.goal-focus-progress small { color: #98a29d; font-size: 11px; }
.goal-focus-link { display: inline-flex; align-items: center; gap: 4px; justify-self: start; padding: 0; border: 0; background: transparent; color: var(--green); font-size: 12px; font-weight: 600; }
.goal-focus-empty { margin: 0; color: #8d9792; font-size: 12px; line-height: 1.7; }
.today-focus { display: grid; gap: 8px; }
.today-focus-head strong { display: block; color: #34403a; font-size: 14px; }
.today-focus-head small { display: block; margin-top: 3px; color: #98a29d; font-size: 11px; }
.today-focus-list { display: grid; gap: 6px; }
.today-focus-row { display: flex; align-items: center; gap: 9px; padding: 7px 9px; border: 1px solid #eef1ee; border-radius: 7px; }
.today-focus-row.completed { background: #f5faf6; }
.today-focus-row.skipped { opacity: .55; }
.today-index { display: grid; width: 22px; height: 22px; flex: none; place-items: center; border-radius: 50%; background: #f1f4f1; color: #829088; font-size: 11px; }
.today-main { display: grid; min-width: 0; flex: 1; gap: 2px; }
.today-main small { color: #a9722f; font-size: 10px; }
.today-main strong { overflow: hidden; color: #46524c; font-size: 12px; font-weight: 550; text-overflow: ellipsis; white-space: nowrap; }
.today-done { color: #5d8b6c; font-size: 11px; }
.today-empty { margin: 4px 0; color: #98a29d; font-size: 12px; }
.overview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 15px; margin-bottom: 28px; }
.overview-card { min-height: 143px; padding: 17px 19px 14px; box-shadow: none; }
.overview-card-label { display: flex; align-items: center; justify-content: space-between; color: #7c8782; font-size: 14px; }
.overview-card-label i { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 7px; font-style: normal; font-size: 16px; }
.icon-green { background: var(--green-soft); color: var(--green); }.icon-blue { background: var(--blue-soft); color: var(--blue); }.icon-coral { background: var(--coral-soft); color: var(--coral); }
.overview-number { margin-top: 8px; color: var(--ink); font-size: 29px; font-weight: 650; line-height: 1.2; }.overview-number small { color: #8b9691; font-size: 14px; font-weight: 500; }
.overview-foot { margin-top: 7px; color: #9aa39f; font-size: 13px; }
.next-practice-copy { margin-top: 6px; color: var(--ink); font-size: 16px; font-weight: 600; line-height: 1.45; }
.text-action, .link-button { display: inline-flex; align-items: center; gap: 5px; border: 0; background: transparent; color: var(--green); font-size: 13px; font-weight: 600; }
.text-action { margin-top: 9px; padding: 0; }
.dashboard-lower { display: grid; grid-template-columns: minmax(0, 1.55fr) minmax(250px, .85fr); gap: 21px; }
.archive-section { margin-bottom: 28px; }
.archive-scope { color: #98a29d; font-size: 12px; font-weight: 400; }
.weekly-link { display: inline-flex; align-items: center; gap: 3px; margin-left: auto; padding: 0; border: 0; background: transparent; color: var(--green); font-size: 12px; font-weight: 600; }
.archive-grid { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr) minmax(0, 1.05fr); gap: 15px; }
.archive-card { padding: 15px 17px 14px; box-shadow: none; }
.archive-card-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.archive-card-head strong { color: #33413a; font-size: 14px; font-weight: 650; }
.archive-card-head span { color: #98a29d; font-size: 12px; }
.trend-svg { width: 100%; height: 108px; }
.trend-line { fill: none; stroke: var(--green); stroke-width: 1.8; stroke-linejoin: round; stroke-linecap: round; }
.trend-dot { fill: var(--green); }
.trend-score { fill: #4c5952; font-size: 10px; font-weight: 600; }
.trend-label { fill: #98a29d; font-size: 9px; }
.dimension-list { display: grid; gap: 9px; }
.dimension-row { display: grid; grid-template-columns: 78px minmax(0, 1fr) 33px; align-items: center; gap: 8px; }
.dimension-name { color: #5d6a64; font-size: 13px; }
.dimension-bar { display: block; height: 7px; overflow: hidden; border-radius: 4px; background: #eef1ee; }
.dimension-bar i { display: block; height: 100%; border-radius: 4px; background: var(--green); }
.dimension-row.weak .dimension-bar i { background: #d9863f; }
.dimension-row strong { color: #46524c; font-size: 13px; text-align: right; }
.weakest-list { margin: 0 0 12px; padding: 0; list-style: none; }
.weakest-list li { padding: 8px 0; border-bottom: 1px dashed #eef1ee; }
.weakest-list li:last-child { border-bottom: 0; }
.weakest-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.weakest-head strong { color: #a7622c; font-size: 13px; font-weight: 650; }
.weakest-head span { color: #98a29d; font-size: 12px; }
.weakest-list p { margin: 4px 0 0; color: #7c8782; font-size: 13px; line-height: 1.7; }
.archive-hint { margin: 9px 0 0; color: #98a29d; font-size: 12px; line-height: 1.7; }
.archive-empty { display: grid; justify-items: center; gap: 8px; padding: 24px; text-align: center; box-shadow: none; }
.archive-empty strong { color: #46524c; font-size: 14px; }
.archive-empty p { max-width: 460px; margin: 0; color: #8d9792; font-size: 13px; line-height: 1.75; }
.archive-card .primary-button.el-button, .archive-card .soft-button.el-button { width: 100%; margin-top: 10px; }
/* 错题本的表格样式放在全局 styles.css：/mistakes 页要用同一套 */
.mistake-section { margin-bottom: 28px; }
.mistake-all-row { display: flex; justify-content: flex-end; margin: 8px 16px 6px; }
.all-mistakes { display: inline-flex; align-items: center; gap: 5px; padding: 0; border: 0; background: transparent; color: var(--green); font-size: 12px; font-weight: 600; }
.all-mistakes:hover { text-decoration: underline; }
.section-title { margin-bottom: 13px; }
.recent-list { overflow: hidden; box-shadow: none; }
.recent-row { display: flex; width: 100%; min-height: 69px; align-items: center; gap: 12px; padding: 0 15px; border: 0; border-bottom: 1px solid #eef1ee; background: white; text-align: left; }
.recent-row:last-child { border-bottom: 0; }.recent-row:hover { background: #fafcfb; }
.recent-symbol { display: grid; width: 34px; height: 34px; flex: none; place-items: center; border-radius: 7px; background: var(--blue-soft); color: var(--blue); }.recent-symbol.completed { background: var(--green-soft); color: var(--green); }
.recent-main { display: grid; min-width: 0; flex: 1; gap: 5px; }.recent-main strong { overflow: hidden; color: #34403a; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }.recent-main span { overflow: hidden; color: #929c97; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.status-done.el-tag { border-color: #d9eee2; background: #edf7f0; color: #4a8b62; }.status-progress.el-tag { border-color: #f4e7c9; background: #fbf5e9; color: #ad8844; }
.recent-date { width: 37px; flex: none; color: #98a19d; font-size: 13px; text-align: right; }.recent-arrow { color: #abb4af; font-size: 14px; }
.empty-recent { display: flex; min-height: 250px; align-items: center; flex-direction: column; justify-content: center; padding: 20px; box-shadow: none; }.empty-recent strong { color: #46524c; font-size: 14px; }.empty-recent p { max-width: 280px; color: #8d9792; font-size: 13px; line-height: 1.7; text-align: center; }
.guidance-panel { overflow: hidden; padding: 0 17px 16px; box-shadow: none; }.guidance-art { position: relative; display: flex; height: 116px; align-items: center; justify-content: center; margin: 0 -17px 15px; overflow: hidden; background: #eff6f2; }.art-window { position: absolute; top: 20px; right: 26px; display: flex; width: 97px; height: 67px; gap: 5px; align-items: flex-start; padding: 10px; border: 1px solid #dce8e1; border-radius: 6px; background: rgba(255,255,255,.72); transform: rotate(5deg); }.art-window i { width: 5px; height: 5px; border-radius: 50%; background: #df9b7c; }.art-window i:nth-child(2) { background: #e5c26c; }.art-window i:nth-child(3) { background: #78ad8b; }
.art-person { position: absolute; z-index: 2; bottom: -6px; left: calc(50% - 21px); width: 70px; height: 96px; }.person-head { position: absolute; top: 8px; left: 20px; width: 35px; height: 38px; border: 1px solid #d59c7d; border-radius: 46% 46% 43% 43%; background: #e7b08e; }.person-head::after { position: absolute; top: -4px; left: 0; width: 36px; height: 15px; border-radius: 50% 50% 35% 35%; background: #483c36; content: ''; }.person-neck { position: absolute; top: 42px; left: 31px; width: 14px; height: 16px; background: #dba282; }.person-body { position: absolute; bottom: 0; left: 5px; width: 65px; height: 48px; border-radius: 24px 24px 0 0; background: #4a826e; }.art-spark { position: absolute; color: var(--coral); font-size: 14px; }.spark-a { top: 21px; left: 31%; font-size: 17px; }.spark-b { right: 23%; bottom: 19px; color: #d7a845; font-size: 14px; }
.guidance-panel > strong { color: #36423c; font-size: 14px; }.guidance-panel > p { margin: 8px 0 12px; color: #87928c; font-size: 13px; line-height: 1.75; }.guidance-meta { display: flex; flex-wrap: wrap; gap: 12px; padding-top: 11px; border-top: 1px solid #edf0ed; color: #7c8782; font-size: 13px; }.guidance-meta span { display: inline-flex; align-items: center; gap: 5px; }
@media(max-width: 1080px) { .archive-grid { grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); }.archive-grid > :last-child { grid-column: 1 / -1; } }
@media(max-width: 920px) { .dashboard-lower { grid-template-columns: 1fr; }.guidance-section { display: none; }.archive-grid { grid-template-columns: 1fr; }.archive-grid > :last-child { grid-column: auto; } }
@media(max-width: 680px) { .overview-grid { grid-template-columns: repeat(2, minmax(0,1fr)); gap: 9px; }.overview-card { min-height: 130px; padding: 13px; }.overview-card:last-child { grid-column: 1 / -1; min-height: 133px; }.overview-number { font-size: 25px; }.recent-row { gap: 8px; padding: 0 10px; }.recent-date, .recent-arrow { display: none; }.recent-main span { max-width: 160px; } }
@media(max-width: 900px) { .goal-today-grid { grid-template-columns: 1fr; } }
</style>
