<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getMedia } from '../services/mediaStore';
import { getAnswerMedia } from '../services/api';
import { buildQuestions, styleLabels, typeLabels } from '../data/questions';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const currentIndex = ref(0);
const mediaUrl = ref('');
const mediaLoading = ref(false);
const session = computed(() => practice.find(route.params.id));
const questions = computed(() => session.value ? (session.value.questions || buildQuestions(session.value.role, session.value.questionCount, session.value.interviewType)) : []);
const currentQuestion = computed(() => questions.value[currentIndex.value]);
const currentAnswer = computed(() => session.value?.answers.find((answer) => answer.questionIndex === currentIndex.value));
const totalSeconds = computed(() => session.value?.answers.reduce((sum, answer) => sum + answer.durationSeconds, 0) || 0);
const totalTime = computed(() => `${Math.floor(totalSeconds.value / 60)} 分 ${totalSeconds.value % 60} 秒`);
let activeUrl;
let mediaRequest = 0;

onMounted(() => { if (session.value?.answers.length) selectQuestion(session.value.answers[0].questionIndex); });
watch(() => route.params.id, () => { if (!session.value) router.replace('/history'); });

async function selectQuestion(index) {
  const requestId = ++mediaRequest;
  currentIndex.value = index;
  if (activeUrl) URL.revokeObjectURL(activeUrl);
  activeUrl = undefined;
  mediaUrl.value = '';
  if (!currentAnswer.value) return;
  mediaLoading.value = true;
  try {
    let blob = await getMedia(currentAnswer.value.localMediaId || currentAnswer.value.mediaId);
    if (!blob && currentAnswer.value.mediaId) blob = await getAnswerMedia(currentAnswer.value.mediaId);
    if (requestId !== mediaRequest) return;
    if (blob) {
      activeUrl = URL.createObjectURL(blob);
      mediaUrl.value = activeUrl;
      if (video.value) { video.value.src = activeUrl; video.value.load(); }
    }
  } catch {
    if (requestId !== mediaRequest) return;
    ElMessage.error('无法读取视频片段，请确认练习仍在保留期内。');
  } finally { mediaLoading.value = false; }
}

function playAt(offset) {
  if (!video.value || !mediaUrl.value || !Number.isFinite(video.value.duration)) return;
  video.value.currentTime = Math.max(0, Math.min(offset, video.value.duration - 0.1));
  video.value.play();
}

async function removeSession() {
  try { await ElMessageBox.confirm('删除后，这次练习的所有视频片段与报告数据都会清除，无法恢复。', '删除练习？', { confirmButtonText: '删除全部数据', cancelButtonText: '取消', type: 'warning' }); }
  catch { return; }
  try {
    await practice.remove(session.value.id);
    ElMessage.success('练习视频与记录已删除');
    router.replace('/history');
  } catch (error) {
    ElMessage.error(error.message || '删除失败，请检查连接后重试。');
  }
}

function formatDuration(seconds) { return `${String(Math.floor(seconds / 60)).padStart(2,'0')}:${String(seconds % 60).padStart(2,'0')}`; }
onBeforeUnmount(() => { if (activeUrl) URL.revokeObjectURL(activeUrl); });
</script>

<template>
  <section v-if="session" class="report-page">
    <div class="report-heading"><div><button class="report-back" @click="router.push('/history')"><el-icon><ArrowLeft /></el-icon>练习记录</button><h1>{{ session.role }} <span>练习报告</span></h1><p>{{ typeLabels[session.interviewType] || '模拟面试' }} · {{ styleLabels[session.interviewerStyle] || '专业平和' }} · {{ new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(session.completedAt || session.createdAt)) }}</p></div><el-button class="delete-button" @click="removeSession"><el-icon><Delete /></el-icon>删除练习数据</el-button></div>
    <div class="report-status panel"><div class="status-symbol"><el-icon><VideoCamera /></el-icon></div><div class="status-copy"><strong>视频练习已完成</strong><span>已保存 {{ session.answers.length }} 段回答，共 {{ totalTime }}。转写与 AI 评分服务尚未连接，因此暂不显示评分。</span></div><el-tag class="analysis-tag" size="small"><i></i>{{ session.answers.some(answer => answer.mediaSizeBytes || answer.mediaId) ? '等待分析服务' : '录像未同步' }}</el-tag></div>
    <div class="report-layout">
      <section class="review-section"><div class="section-title">逐题回放 <small>{{ session.answers.length }} 段视频 · 点击问题可切换片段</small></div><div class="player-panel panel"><div class="player-frame"><video v-if="mediaUrl" ref="video" :src="mediaUrl" controls playsinline></video><div v-else class="player-empty"><el-icon v-if="mediaLoading" class="loading"><Loading /></el-icon><template v-else><el-icon><VideoCamera /></el-icon><strong>{{ currentAnswer ? '这段视频暂时无法读取' : '本题尚未录制' }}</strong><span>{{ currentAnswer ? '本地媒体文件可能已被清理。' : '本轮练习未完成全部问题。' }}</span></template></div><div class="player-label">{{ String(currentIndex + 1).padStart(2,'0') }} / {{ String(questions.length).padStart(2,'0') }}</div></div><div class="transcript-panel"><div class="transcript-header"><strong>回答转写</strong><el-tag class="tag-muted" size="small">尚未生成</el-tag></div><p>ASR 服务配置后，回答转写和时间戳会显示在这里，并可点击时间跳转到对应视频片段。</p><div v-if="currentAnswer" class="answer-meta"><span><el-icon><Timer /></el-icon>回答时长 {{ formatDuration(currentAnswer.durationSeconds) }}</span><span><el-icon><Files /></el-icon>{{ (currentAnswer.bytes / 1024 / 1024).toFixed(1) }} MB</span></div></div></div>
      <div class="question-list"><button v-for="(question,index) in questions" :key="question.id" class="question-card panel" :class="{ active: currentIndex === index }" @click="selectQuestion(index)"><span class="question-number">{{ String(index + 1).padStart(2,'0') }}</span><span class="question-info"><small>{{ question.category }}</small><strong>{{ question.text }}</strong></span><span class="question-duration">{{ session.answers.find((answer) => answer.questionIndex === index) ? formatDuration(session.answers.find((answer) => answer.questionIndex === index).durationSeconds) : '未回答' }}</span><el-icon v-if="currentIndex === index"><ArrowRight /></el-icon></button></div></section>
      <aside class="score-section"><div class="section-title">回答分析 <el-tag class="tag-muted" size="small">等待接入</el-tag></div><div class="score-panel panel"><div class="score-empty"><div class="score-mark"><el-icon><DataAnalysis /></el-icon></div><strong>分析服务尚未配置</strong><p>接入 ASR 与结构化评分服务后，此处会展示回答相关性、完整性、专业性和 STAR 结构等反馈。</p></div><div class="rubric-list"><div><span>内容相关性</span><i>—</i></div><div><span>回答完整度</span><i>—</i></div><div><span>STAR 结构</span><i>—</i></div><div><span>表达节奏</span><i>—</i></div></div><div class="score-footnote"><el-icon><InfoFilled /></el-icon>报告仅评价回答内容与表达，不进行人脸或情绪判断。</div></div><div class="evidence-panel panel"><div class="section-title">可定位证据 <small>视频时间轴</small></div><div v-if="currentAnswer" class="evidence-row"><button class="timeline-play" aria-label="播放本题视频" title="播放本题视频" @click="playAt(0)"><el-icon><VideoPlay /></el-icon></button><div><strong>回答片段</strong><small>00:00 – {{ formatDuration(currentAnswer.durationSeconds) }}</small></div><span class="timeline-track"><i></i></span></div><p v-else class="no-evidence">录制本题后将可定位到对应视频时间点。</p></div></aside>
    </div>
  </section>
</template>

<style scoped>
.report-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; margin-bottom: 19px; }.report-back { display: inline-flex; align-items: center; gap: 4px; margin-bottom: 11px; padding: 0; border: 0; background: transparent; color: #85918a; font-size: 9px; }.report-heading h1 { margin: 0; color: #354139; font-size: 21px; font-weight: 650; }.report-heading h1 span { margin-left: 3px; color: #69766f; font-size: 16px; font-weight: 500; }.report-heading p { margin: 7px 0 0; color: #929c96; font-size: 9px; }.delete-button.el-button { height: 32px; border-color: #eeddd8; color: #ad6554; font-size: 9px; }
.report-status { display: flex; min-height: 66px; align-items: center; gap: 10px; margin-bottom: 18px; padding: 12px 14px; border-color: #dcece1; background: #f4faf5; box-shadow: none; }.status-symbol { display: grid; width: 32px; height: 32px; flex: none; place-items: center; border-radius: 50%; background: #e4f2e8; color: #4c9366; }.status-copy { display: grid; min-width: 0; flex: 1; gap: 4px; }.status-copy strong { color: #477256; font-size: 10px; }.status-copy span { color: #778d7e; font-size: 9px; line-height: 1.6; }.analysis-tag.el-tag { flex: none; border-color: #e6e4d8; background: #f8f7f0; color: #8d855b; font-size: 8px; }.analysis-tag i { display: inline-block; width: 5px; height: 5px; margin-right: 4px; border-radius: 50%; background: #cfb85e; }
.report-layout { display: grid; grid-template-columns: minmax(0,1.35fr) minmax(245px,.75fr); align-items: start; gap: 17px; }.review-section { min-width: 0; }.review-section > .section-title { margin-bottom: 11px; }.player-panel { overflow: hidden; box-shadow: none; }.player-frame { position: relative; display: grid; min-height: 225px; aspect-ratio: 16/9; place-items: center; overflow: hidden; background: #1c2923; }.player-frame video { width: 100%; height: 100%; max-height: 380px; object-fit: contain; }.player-empty { display: flex; align-items: center; flex-direction: column; gap: 8px; color: #9eafa3; font-size: 21px; }.player-empty strong { color: #dce8df; font-size: 10px; font-weight: 500; }.player-empty span { color: #9baba0; font-size: 8px; }.player-empty .loading { animation: rotate 1s linear infinite; }@keyframes rotate { to { transform: rotate(360deg); } }.player-label { position: absolute; top: 10px; left: 10px; padding: 5px 6px; border-radius: 3px; background: rgba(13,25,18,.7); color: white; font-size: 8px; }
.transcript-panel { padding: 13px 14px 12px; }.transcript-header { display: flex; align-items: center; justify-content: space-between; }.transcript-header strong { color: #536058; font-size: 10px; }.transcript-panel > p { margin: 8px 0 12px; color: #959f99; font-size: 9px; line-height: 1.7; }.answer-meta { display: flex; flex-wrap: wrap; gap: 14px; padding-top: 9px; border-top: 1px solid #edf0ed; color: #7d8982; font-size: 8px; }.answer-meta span { display: flex; align-items: center; gap: 5px; }.question-list { display: grid; gap: 7px; margin-top: 9px; }.question-card { display: flex; min-height: 54px; align-items: center; gap: 9px; padding: 9px 11px; box-shadow: none; cursor: pointer; text-align: left; }.question-card.active { border-color: #9dc7ac; background: #f5faf6; }.question-number { display: grid; width: 25px; height: 25px; flex: none; place-items: center; border-radius: 50%; background: #f1f4f1; color: #829088; font-size: 8px; }.question-card.active .question-number { background: #e0f0e5; color: var(--green); }.question-info { display: grid; min-width: 0; flex: 1; gap: 4px; }.question-info small { color: #96a09a; font-size: 7px; }.question-info strong { display: -webkit-box; overflow: hidden; color: #57645c; font-size: 8px; font-weight: 500; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.question-duration { flex: none; color: #98a29c; font-size: 8px; }.question-card > .el-icon { flex: none; color: var(--green); font-size: 11px; }
.score-section { display: grid; gap: 10px; }.score-section > .section-title { margin: 0; }.score-panel { padding: 15px; box-shadow: none; }.score-empty { display: flex; align-items: center; flex-direction: column; padding: 10px 8px 13px; text-align: center; }.score-mark { display: grid; width: 39px; height: 39px; place-items: center; margin-bottom: 9px; border-radius: 50%; background: var(--blue-soft); color: var(--blue); font-size: 18px; }.score-empty strong { color: #4e5a53; font-size: 10px; }.score-empty p { margin: 7px 0 0; color: #89948e; font-size: 8px; line-height: 1.75; }.rubric-list { padding-top: 11px; border-top: 1px solid #edf0ed; }.rubric-list > div { display: flex; justify-content: space-between; margin: 11px 0; color: #7c8881; font-size: 8px; }.rubric-list i { color: #b0b8b2; font-style: normal; }.score-footnote { display: flex; gap: 5px; padding-top: 10px; border-top: 1px solid #edf0ed; color: #96a099; font-size: 7px; line-height: 1.6; }.score-footnote .el-icon { flex: none; color: #7298a5; }.evidence-panel { padding: 14px; box-shadow: none; }.evidence-panel .section-title { margin-bottom: 10px; }.evidence-row { display: flex; align-items: center; gap: 8px; }.timeline-play { display: grid; width: 25px; height: 25px; flex: none; place-items: center; border: 0; border-radius: 50%; background: var(--green-soft); color: var(--green); }.evidence-row > div { display: grid; flex: none; gap: 3px; }.evidence-row strong { color: #637067; font-size: 8px; font-weight: 600; }.evidence-row small { color: #99a29d; font-size: 7px; }.timeline-track { position: relative; height: 4px; flex: 1; overflow: hidden; border-radius: 3px; background: #e8eee9; }.timeline-track i { display: block; width: 15%; height: 100%; border-radius: inherit; background: var(--green); }.no-evidence { margin: 4px 0; color: #99a29d; font-size: 8px; line-height: 1.7; }
@media(max-width: 900px) { .report-layout { grid-template-columns: minmax(0,1.1fr) minmax(220px,.8fr); gap: 11px; }.player-frame { min-height: 185px; } }
@media(max-width: 720px) { .report-layout { grid-template-columns: 1fr; }.score-section { grid-row: 1; grid-template-columns: 1fr; }.report-heading { align-items: flex-start; flex-direction: column; }.report-status { align-items: flex-start; }.analysis-tag { align-self: flex-start; }.player-frame { min-height: 190px; } }
</style>
