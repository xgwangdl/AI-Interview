<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getMedia } from '../services/mediaStore';
import {
  getAnswerMedia, getPracticeReport, reEvaluateAnswer, retryPracticeAnalysis, updateAnswerTranscript,
} from '../services/api';
import { buildQuestions, styleLabels, typeLabels } from '../data/questions';
import DigitalHuman from '../components/DigitalHuman.vue';
import { createAvatarAudio } from '../services/avatarAudio';
import { revealedSentenceCount, splitSentences } from '../services/mouthAnimation';
import {
  AVATAR_STATE, resetAvatarState, setAvatarError, setAvatarState, useAvatarState,
} from '../services/avatarState';
import { getSampleAnswerAudio, getSampleAnswerNarration } from '../services/api';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const currentIndex = ref(0);
const mediaUrl = ref('');
const mediaLoading = ref(false);
const report = ref(null);
const reportLoading = ref(false);
const session = computed(() => practice.find(route.params.id));
const questions = computed(() => session.value ? (session.value.questions || buildQuestions(session.value.role, session.value.questionCount, session.value.interviewType)) : []);
const currentQuestion = computed(() => questions.value[currentIndex.value]);
const currentAnswer = computed(() => session.value?.answers.find((answer) => answer.questionIndex === currentIndex.value));
const totalSeconds = computed(() => session.value?.answers.reduce((sum, answer) => sum + answer.durationSeconds, 0) || 0);
const totalTime = computed(() => `${Math.floor(totalSeconds.value / 60)} 分 ${totalSeconds.value % 60} 秒`);
const currentReport = computed(() => report.value?.questions?.find((item) => item.questionIndex === currentIndex.value) || null);
const currentEvaluation = computed(() => currentReport.value?.evaluation || null);
const transcriptEdited = computed(() => Boolean(
  currentReport.value?.transcriptEditedAt || currentReport.value?.transcriptOriginal));
const editingTranscript = ref(false);
const transcriptDraft = ref('');
const transcriptSaving = ref(false);
const showTranscriptOriginal = ref(false);
const signedMediaUrl = computed(() => currentReport.value?.mediaSignedUrl || '');
const evidenceList = computed(() => currentEvaluation.value?.evidence || []);
/** 追问在这份报告里算"顺着上一题问的"，不算凭空多出来的一道题。 */
const followUpCount = computed(() => report.value?.summary?.followUpCount || 0);

function followUpLabel(index) {
  const item = report.value?.questions?.find((question) => question.questionIndex === index);
  if (!item?.followUp) return '';
  const parent = Number.isInteger(item.parentQuestionIndex) ? item.parentQuestionIndex + 1 : null;
  const round = item.followUpRound > 1 ? `第 ${item.followUpRound} 次追问` : '面试官追问';
  return parent ? `${round} · 关于第 ${parent} 题` : round;
}

// --- 让面试官把「示范回答」念出来 ---
// 文本直接来自评分里的 sampleAnswer，不额外调用模型，只做一次语音合成。
const { state: avatarState, error: avatarError } = useAvatarState();
const avatarAudio = createAvatarAudio();
const sampleAudioUrl = ref('');
const sampleSpeaking = ref(false);
const sampleLoading = ref(false);
const sampleTruncated = ref(false);
/**
 * 已念出的句数。**初始为 0 —— 一开始不显示任何文字**，念完才显示完整内容。
 * 切到别的题会重置回 0（同样先不显示）。
 */
const revealedCount = ref(0);
/** 实际会被念出来的文本（音频被截断时只有前半段）；字幕按它来，才能和语音对齐 */
const spokenSampleText = ref('');
/** 是否已经念完（或已停止/失败）：此时显示完整示范回答 */
const finished = ref(false);

const sampleSentences = computed(() => splitSentences(spokenSampleText.value));
/** 字幕文本：初始为空，随朗读逐句出现；念完后显示完整示范回答 */
const visibleSampleText = computed(() => {
  // 注意 .value：这里少写会让 ref 对象恒为真，导致一开始就把全文显示出来
  if (finished.value) return currentEvaluation.value?.sampleAnswer || spokenSampleText.value;
  return sampleSentences.value.slice(0, revealedCount.value).join('');
});

avatarAudio.on('ended', () => {
  sampleSpeaking.value = false;
  finished.value = true;            // 念完 → 显示完整示范回答
  revealedCount.value = sampleSentences.value.length;
  setAvatarState(AVATAR_STATE.IDLE);
});

avatarAudio.on('timeupdate', ({ currentTime, duration }) => {
  if (!duration) return;
  revealedCount.value = revealedSentenceCount(sampleSentences.value, currentTime / duration);
});

avatarAudio.on('error', (error) => {
  sampleSpeaking.value = false;
  // 语音失败时把文字放出来，别让用户既听不到也看不到
  finished.value = true;
  setAvatarError(error?.message);
});

function releaseSampleAudio() {
  if (sampleAudioUrl.value) {
    URL.revokeObjectURL(sampleAudioUrl.value);
    sampleAudioUrl.value = '';
  }
}

async function speakSampleAnswer() {
  if (sampleSpeaking.value) {
    avatarAudio.stop();
    sampleSpeaking.value = false;
    finished.value = true;           // 手动停止 → 直接给完整文字，方便阅读
    setAvatarState(AVATAR_STATE.IDLE);
    return;
  }
  if (!currentReport.value?.evaluation?.sampleAnswer) return;
  sampleLoading.value = true;
  try {
    releaseSampleAudio();
    // 先问接口这段能不能念（没评分成功/正在分析时会 404），再去取音频
    const narration = await getSampleAnswerNarration(route.params.id, currentIndex.value);
    sampleTruncated.value = Boolean(narration?.truncated);
    spokenSampleText.value = narration?.text || currentEvaluation.value.sampleAnswer;
    finished.value = false;
    const blob = await getSampleAnswerAudio(route.params.id, currentIndex.value);
    sampleAudioUrl.value = URL.createObjectURL(blob);
    avatarAudio.load(sampleAudioUrl.value);
    revealedCount.value = 0;         // 从空开始，随朗读逐句出现
    setAvatarState(AVATAR_STATE.SPEAKING);
    const outcome = await avatarAudio.play();
    if (outcome === 'needs-gesture') {
      // 点击按钮本身就是用户手势，正常不会走到这里；真被拦就退回静止
      sampleSpeaking.value = false;
      setAvatarState(AVATAR_STATE.IDLE);
      ElMessage.warning('浏览器拦了自动播放，请再点一次。');
      return;
    }
    sampleSpeaking.value = true;
  } catch (error) {
    sampleSpeaking.value = false;
    finished.value = true;           // 拿不到语音也要让用户能读到文字
    if (error?.status === 503) ElMessage.warning('语音合成暂不可用，可以直接看下面的文字示范。');
    else ElMessage.error(error?.message || '无法播放示范回答');
    setAvatarState(AVATAR_STATE.IDLE);
  } finally {
    sampleLoading.value = false;
  }
}
const dimensions = [
  { key: 'relevance', label: '内容相关性' },
  { key: 'completeness', label: '回答完整度' },
  { key: 'professionalism', label: '专业程度' },
  { key: 'starStructure', label: 'STAR 结构' },
  { key: 'logic', label: '回答逻辑' },
];
const dimensionLabels = Object.fromEntries(dimensions.map((item) => [item.key, item.label]));
const reportStatusLabel = computed(() => {
  if (reportLoading.value) return '正在分析';
  const status = report.value?.status;
  if (status === 'ready') return '分析已完成';
  if (status === 'partial') return '部分题目已分析';
  if (status === 'analysis_failed') return '分析失败';
  if (report.value?.analysis?.status === 'processing') return '正在分析';
  if (status === 'awaiting_analysis') return '等待分析服务';
  return session.value?.answers.some((answer) => answer.mediaId) ? '等待分析服务' : '录像未同步';
});
const reportHeadline = computed(() => (report.value?.status === 'ready' ? '视频练习与复盘已完成' : '视频练习已完成'));
const reportSummaryText = computed(() => {
  const base = `已保存 ${session.value?.answers.length || 0} 段回答，共 ${totalTime.value}。`;
  if (reportLoading.value) return `${base} 正在读取 AI 复盘结果…`;
  const analysis = report.value?.analysis;
  if (analysis?.status === 'failed') {
    return `${base} 转写或评分任务失败${analysis.error ? `：${analysis.error}` : ''}。可以点击重试，或删除后重新录制。`;
  }
  const summary = report.value?.summary;
  if (summary?.averageOverallScore != null) {
    const details = [];
    if (summary.speechRateCharactersPerMinute) details.push(`平均语速 ${Math.round(summary.speechRateCharactersPerMinute)} 字/分`);
    if (summary.totalPauseCount) details.push(`平均停顿 ${summary.totalPauseCount} 次`);
    if (summary.totalFillerWordCount) details.push(`平均口头禅 ${summary.totalFillerWordCount} 次`);
    const followUps = followUpCount.value ? ` 其中 ${followUpCount.value} 个回答是面试官的追问。` : '';
    return `${base} 综合 ${summary.averageOverallScore.toFixed(1)} 分${details.length ? ` · ${details.join(' · ')}` : ''}。${followUps}`;
  }
  return `${base} 后台正在转写并评分，完成后会自动显示逐题反馈。`;
});
let activeUrl;
let mediaRequest = 0;
let reportPoller;
const REPORT_POLL_MS = 10000;

onMounted(() => {
  if (session.value?.answers.length) selectQuestion(session.value.answers[0].questionIndex);
});
watch(() => route.params.id, () => { if (!session.value) router.replace('/history'); });
// 直接打开或刷新报告页时，练习记录是异步从服务端拉取的。
// 只监听路由参数会在 store 尚未就绪时错过加载，页面会永远停在「尚未生成」。
watch(() => session.value?.id, (id) => { if (id) loadReport(); }, { immediate: true });

async function loadReport() {
  window.clearTimeout(reportPoller);
  report.value = null;
  if (!session.value?.id) return;
  reportLoading.value = true;
  try {
    report.value = await getPracticeReport(session.value.id);
  } catch {
    report.value = null;
  } finally {
    reportLoading.value = false;
    if (!mediaUrl.value && signedMediaUrl.value) mediaUrl.value = signedMediaUrl.value;
    scheduleReportPoll();
  }
}

/** 分析在后台异步进行：只要还有任务排在队列里就继续轮询，出结果后自动停下。 */
function scheduleReportPoll() {
  const status = report.value?.analysis?.status;
  if (status !== 'pending' && status !== 'processing') return;
  reportPoller = window.setTimeout(loadReport, REPORT_POLL_MS);
}

/** 分析失败是终态，需要用户点一次才会重新排队。 */
async function retryAnalysis() {
  if (!session.value?.id) return;
  reportLoading.value = true;
  try {
    report.value = await retryPracticeAnalysis(session.value.id);
  } catch {
    ElMessage.error('重试失败，请稍后再试。');
  } finally {
    reportLoading.value = false;
    scheduleReportPoll();
  }
}

async function selectQuestion(index) {
  // 切题时把正在念的示范回答停掉，否则会继续念上一题的内容
  if (sampleSpeaking.value) {
    avatarAudio.stop();
    sampleSpeaking.value = false;
    setAvatarState(AVATAR_STATE.IDLE);
  }
  releaseSampleAudio();
  // 换到新题：示范回答重新"藏起来"，等用户点了才逐句出现
  finished.value = false;
  editingTranscript.value = false;
  showTranscriptOriginal.value = false;
  revealedCount.value = 0;
  spokenSampleText.value = '';
  const requestId = ++mediaRequest;
  currentIndex.value = index;
  if (activeUrl) URL.revokeObjectURL(activeUrl);
  activeUrl = undefined;
  mediaUrl.value = '';
  if (!currentAnswer.value) return;
  mediaLoading.value = true;
  try {
    let blob = await getMedia(currentAnswer.value.localMediaId || currentAnswer.value.mediaId);
    if (!blob && currentAnswer.value.mediaId) {
      try { blob = await getAnswerMedia(currentAnswer.value.mediaId); } catch { blob = null; }
    }
    if (requestId !== mediaRequest) return;
    if (blob) {
      activeUrl = URL.createObjectURL(blob);
      mediaUrl.value = activeUrl;
    }
  } catch {
    if (requestId !== mediaRequest) return;
    if (!signedMediaUrl.value) ElMessage.error('无法读取视频片段，请确认练习仍在保留期内。');
  } finally {
    if (requestId === mediaRequest) {
      if (!mediaUrl.value && signedMediaUrl.value) mediaUrl.value = signedMediaUrl.value;
      mediaLoading.value = false;
    }
  }
}

function beginTranscriptEdit() {
  transcriptDraft.value = currentReport.value?.transcriptText || '';
  editingTranscript.value = true;
}

async function saveTranscript() {
  const text = transcriptDraft.value.trim();
  if (!text || !currentReport.value?.answerId) return;
  transcriptSaving.value = true;
  try {
    await updateAnswerTranscript(currentReport.value.answerId, text);
    await reEvaluateAnswer(currentReport.value.answerId);
    editingTranscript.value = false;
    ElMessage.success('转写已修正，正在重新评分这一题…');
    await loadReport();
  } catch (error) {
    ElMessage.error(error?.message || '保存转写失败');
  } finally {
    transcriptSaving.value = false;
  }
}

function playAt(offset) {
  if (!video.value || !mediaUrl.value || !Number.isFinite(video.value.duration)) return;
  video.value.currentTime = Math.max(0, Math.min(offset, video.value.duration - 0.1));
  video.value.play();
}

function scoreOf(key) {
  const value = currentEvaluation.value?.scores?.[key];
  return Number.isFinite(value) ? value.toFixed(1) : '—';
}

function evidenceWidth(item) {
  const duration = currentAnswer.value?.durationSeconds || 0;
  if (!duration) return '15%';
  const width = ((item.endSeconds - item.startSeconds) / duration) * 100;
  return `${Math.min(100, Math.max(4, width))}%`;
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

function formatDuration(seconds) { const value = Math.max(0, Math.round(Number(seconds) || 0)); return `${String(Math.floor(value / 60)).padStart(2,'0')}:${String(value % 60).padStart(2,'0')}`; }
onBeforeUnmount(() => {
  window.clearTimeout(reportPoller);
  if (activeUrl) URL.revokeObjectURL(activeUrl);
  avatarAudio.dispose();
  releaseSampleAudio();
  resetAvatarState();
});
</script>

<template>
  <section v-if="session" class="report-page">
    <div class="report-heading"><div><button class="report-back" @click="router.push('/history')"><el-icon><ArrowLeft /></el-icon>练习记录</button><h1>{{ session.role }} <span>练习报告</span></h1><p>{{ typeLabels[session.interviewType] || '模拟面试' }} · {{ styleLabels[session.interviewerStyle] || '专业平和' }} · {{ new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }).format(new Date(session.completedAt || session.createdAt)) }}</p></div><el-button class="delete-button" @click="removeSession"><el-icon><Delete /></el-icon>删除练习数据</el-button></div>
    <div class="report-status panel"><div class="status-symbol"><el-icon><VideoCamera /></el-icon></div><div class="status-copy"><strong>{{ reportHeadline }}</strong><span>{{ reportSummaryText }}</span></div><el-tag class="analysis-tag" size="small"><i></i>{{ reportStatusLabel }}</el-tag></div>
    <div class="report-layout">
      <section class="review-section"><div class="section-title">逐题回放 <small>{{ session.answers.length }} 段视频<template v-if="followUpCount">（含 {{ followUpCount }} 个追问）</template> · 点击问题可切换片段</small></div><div class="player-panel panel"><div class="player-frame"><video v-if="mediaUrl" ref="video" :src="mediaUrl" controls playsinline></video><div v-else class="player-empty"><el-icon v-if="mediaLoading" class="loading"><Loading /></el-icon><template v-else><el-icon><VideoCamera /></el-icon><strong>{{ currentAnswer ? '这段视频暂时无法读取' : '本题尚未录制' }}</strong><span>{{ currentAnswer ? '本地媒体文件可能已被清理。' : '本轮练习未完成全部问题。' }}</span></template></div><div class="player-label">{{ String(currentIndex + 1).padStart(2,'0') }} / {{ String(questions.length).padStart(2,'0') }}</div></div><div class="transcript-panel"><div class="transcript-header"><strong>回答转写</strong><el-tag :class="currentReport?.transcriptText ? 'tag-ready' : 'tag-muted'" size="small">{{ currentReport?.transcriptText ? '已生成' : '尚未生成' }}</el-tag></div><p v-if="currentReport?.transcriptText" class="transcript-text">{{ currentReport.transcriptText }}</p><p v-else>ASR 服务配置后，回答转写和时间戳会显示在这里，并可点击时间跳转到对应视频片段。</p><div v-if="currentAnswer" class="answer-meta"><span><el-icon><Timer /></el-icon>回答时长 {{ formatDuration(currentAnswer.durationSeconds) }}</span><span><el-icon><Files /></el-icon>{{ (currentAnswer.bytes / 1024 / 1024).toFixed(1) }} MB</span></div></div></div>
      <div v-if="currentReport?.transcriptText" class="transcript-edit-bar panel">
        <div class="transcript-edit-head">
          <div>
            <strong>转写校正</strong>
            <el-tag v-if="transcriptEdited" class="tag-warn" size="small">已人工修正</el-tag>
            <small>修正后只重新评分这一题，视频不会改变</small>
          </div>
          <button v-if="!editingTranscript" class="text-action" @click="beginTranscriptEdit">
            <el-icon><EditPen /></el-icon>修正转写
          </button>
        </div>
        <template v-if="editingTranscript">
          <el-input v-model="transcriptDraft" type="textarea" :rows="4" maxlength="20000" show-word-limit />
          <div class="transcript-edit-actions">
            <el-button class="soft-button" :disabled="transcriptSaving" @click="editingTranscript = false">取消</el-button>
            <el-button class="primary-button" :loading="transcriptSaving" @click="saveTranscript">保存并重新评分</el-button>
          </div>
        </template>
        <template v-else>
          <button v-if="currentReport?.transcriptOriginal" class="text-action muted"
                  @click="showTranscriptOriginal = !showTranscriptOriginal">
            {{ showTranscriptOriginal ? '收起原始转写' : '查看原始转写' }}
          </button>
          <p v-if="showTranscriptOriginal && currentReport?.transcriptOriginal" class="transcript-original">
            原始：{{ currentReport.transcriptOriginal }}
          </p>
        </template>
      </div>
      <div class="question-list"><button v-for="(question,index) in questions" :key="question.id" class="question-card panel" :class="{ active: currentIndex === index, 'is-follow-up': Boolean(followUpLabel(index)) }" @click="selectQuestion(index)"><span class="question-number">{{ String(index + 1).padStart(2,'0') }}</span><span class="question-info"><small>{{ followUpLabel(index) || question.category }}</small><strong>{{ question.text }}</strong></span><span class="question-duration">{{ session.answers.find((answer) => answer.questionIndex === index) ? formatDuration(session.answers.find((answer) => answer.questionIndex === index).durationSeconds) : '未回答' }}</span><el-icon v-if="currentIndex === index"><ArrowRight /></el-icon></button></div></section>
      <aside class="score-section"><div class="section-title">回答分析 <el-tag :class="currentEvaluation ? 'tag-ready' : 'tag-muted'" size="small">{{ currentEvaluation ? '已生成' : (reportLoading ? '分析中' : '等待接入') }}</el-tag></div><div class="score-panel panel"><template v-if="currentEvaluation"><div class="score-overall"><strong>{{ currentEvaluation.overallScore.toFixed(1) }}</strong><span>综合得分</span></div><div class="rubric-list"><div v-for="dimension in dimensions" :key="dimension.key"><span>{{ dimension.label }}</span><i class="filled">{{ scoreOf(dimension.key) }}</i></div></div><div class="delivery-list"><div><span>语速</span><i>{{ Math.round(currentEvaluation.delivery.speechRateCharactersPerMinute) }} 字/分</i></div><div><span>停顿</span><i>{{ currentEvaluation.delivery.pauseCount }} 次 · 共 {{ currentEvaluation.delivery.totalPauseSeconds }} 秒</i></div><div><span>口头禅</span><i>{{ currentEvaluation.delivery.fillerWordCount }} 次</i></div></div></template><template v-else><div class="score-empty"><div class="score-mark"><el-icon><DataAnalysis /></el-icon></div><strong>{{ reportLoading ? '正在读取分析结果' : (report?.analysis?.status === 'failed' ? '分析失败' : '分析结果尚未生成') }}</strong><p>练习完成后台会自动转写并评分，完成后此处会展示逐题反馈。</p><el-button v-if="report?.analysis?.status === 'failed'" class="soft-button" @click="retryAnalysis">重试分析</el-button></div><div class="rubric-list"><div v-for="dimension in dimensions" :key="dimension.key"><span>{{ dimension.label }}</span><i>—</i></div></div></template><div class="rubric-legend"><div class="legend-title">评分口径</div><ul><li><strong>80-100</strong> 表现良好：切题、有细节与结果</li><li><strong>60-79</strong> 基本合格：答到了，但缺细节</li><li><strong>40-59</strong> 需要加强：泛泛而谈</li><li><strong>0-39</strong> 明显不足：几乎没有有效信息</li></ul><p>分数衡量的是这一段回答本身，不代表对你的整体评价。</p></div><div class="score-footnote"><el-icon><InfoFilled /></el-icon>报告仅评价回答内容与表达，不进行人脸或情绪判断。</div></div><div v-if="currentEvaluation?.improvementSuggestions?.length" class="suggestion-panel panel"><div class="section-title">改进建议</div><ul class="suggestion-list"><li v-for="(tip, index) in currentEvaluation.improvementSuggestions" :key="index">{{ tip }}</li></ul></div><div v-if="currentEvaluation?.sampleAnswer" class="sample-panel panel"><div class="section-title">优秀回答示例</div><div class="sample-row"><div class="sample-avatar"><DigitalHuman :speaking="sampleSpeaking" :amplitude="avatarAudio.amplitude" /></div><div class="sample-side"><button class="sample-play" :class="{ playing: sampleSpeaking }" :disabled="sampleLoading" @click="speakSampleAnswer"><el-icon><VideoPause v-if="sampleSpeaking" /><Microphone v-else /></el-icon><span>{{ sampleSpeaking ? '停止' : (sampleLoading ? '准备中…' : '让它回答') }}</span></button><small v-if="!sampleSpeaking && !sampleLoading">点这里，面试官会把这段示范答一遍</small><small v-else-if="sampleSpeaking">正在回答，字幕会跟着出现</small></div></div><p v-if="visibleSampleText" class="sample-text" :class="{ speaking: sampleSpeaking }">{{ visibleSampleText }}</p><p v-else class="sample-text sample-hidden">点「让它回答」，面试官会把这段示范答一遍，字幕随朗读出现。</p><p v-if="sampleTruncated" class="sample-note">示范回答较长，语音只念了前半段；点「停止」或播完后会显示完整文字。</p><p v-if="avatarError" class="sample-note error">{{ avatarError }}</p></div><div class="evidence-panel panel"><div class="section-title">可定位证据 <small>视频时间轴</small></div><div v-if="evidenceList.length" class="evidence-list"><div v-for="(item, index) in evidenceList" :key="index" class="evidence-row"><button class="timeline-play" :aria-label="`播放证据 ${index + 1}`" :title="`播放证据 ${formatDuration(item.startSeconds)}`" @click="playAt(item.startSeconds)"><el-icon><VideoPlay /></el-icon></button><div><strong>{{ dimensionLabels[item.dimension] || item.dimension }}</strong><small>{{ formatDuration(item.startSeconds) }} – {{ formatDuration(item.endSeconds) }} · {{ item.quote }}</small></div><span class="timeline-track"><i :style="{ width: evidenceWidth(item) }"></i></span></div></div><div v-else-if="currentAnswer" class="evidence-row"><button class="timeline-play" aria-label="播放本题视频" title="播放本题视频" @click="playAt(0)"><el-icon><VideoPlay /></el-icon></button><div><strong>回答片段</strong><small>00:00 – {{ formatDuration(currentAnswer.durationSeconds) }}</small></div><span class="timeline-track"><i></i></span></div><p v-else class="no-evidence">录制本题后将可定位到对应视频时间点。</p></div></aside>
    </div>
  </section>
</template>

<style scoped>
.report-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; margin-bottom: 19px; }.report-back { display: inline-flex; align-items: center; gap: 4px; margin-bottom: 11px; padding: 0; border: 0; background: transparent; color: #85918a; font-size: 13px; }.report-heading h1 { margin: 0; color: #354139; font-size: 22px; font-weight: 650; }.report-heading h1 span { margin-left: 3px; color: #69766f; font-size: 17px; font-weight: 500; }.report-heading p { margin: 7px 0 0; color: #929c96; font-size: 13px; }.delete-button.el-button { height: 32px; border-color: #eeddd8; color: #ad6554; font-size: 13px; }
.report-status { display: flex; min-height: 66px; align-items: center; gap: 10px; margin-bottom: 18px; padding: 12px 14px; border-color: #dcece1; background: #f4faf5; box-shadow: none; }.status-symbol { display: grid; width: 32px; height: 32px; flex: none; place-items: center; border-radius: 50%; background: #e4f2e8; color: #4c9366; }.status-copy { display: grid; min-width: 0; flex: 1; gap: 4px; }.status-copy strong { color: #477256; font-size: 13px; }.status-copy span { color: #778d7e; font-size: 13px; line-height: 1.6; }.analysis-tag.el-tag { flex: none; border-color: #e6e4d8; background: #f8f7f0; color: #8d855b; font-size: 12px; }.analysis-tag i { display: inline-block; width: 5px; height: 5px; margin-right: 4px; border-radius: 50%; background: #cfb85e; }
.report-layout { display: grid; grid-template-columns: minmax(0,1.35fr) minmax(245px,.75fr); align-items: start; gap: 17px; }.review-section { min-width: 0; }.review-section > .section-title { margin-bottom: 11px; }.player-panel { overflow: hidden; box-shadow: none; }.player-frame { position: relative; display: grid; min-height: 225px; aspect-ratio: 16/9; place-items: center; overflow: hidden; background: #1c2923; }.player-frame video { width: 100%; height: 100%; max-height: 380px; object-fit: contain; }.player-empty { display: flex; align-items: center; flex-direction: column; gap: 8px; color: #9eafa3; font-size: 22px; }.player-empty strong { color: #dce8df; font-size: 13px; font-weight: 500; }.player-empty span { color: #9baba0; font-size: 12px; }.player-empty .loading { animation: rotate 1s linear infinite; }@keyframes rotate { to { transform: rotate(360deg); } }.player-label { position: absolute; top: 10px; left: 10px; padding: 5px 6px; border-radius: 3px; background: rgba(13,25,18,.7); color: white; font-size: 12px; }
.transcript-panel { padding: 13px 14px 12px; }.transcript-header { display: flex; align-items: center; justify-content: space-between; }.transcript-header strong { color: #536058; font-size: 13px; }.transcript-panel > p { margin: 8px 0 12px; color: #959f99; font-size: 13px; line-height: 1.7; }.answer-meta { display: flex; flex-wrap: wrap; gap: 14px; padding-top: 9px; border-top: 1px solid #edf0ed; color: #7d8982; font-size: 12px; }.answer-meta span { display: flex; align-items: center; gap: 5px; }.question-list { display: grid; gap: 7px; margin-top: 9px; }.question-card { display: flex; min-height: 54px; align-items: center; gap: 9px; padding: 9px 11px; box-shadow: none; cursor: pointer; text-align: left; }.question-card.active { border-color: #9dc7ac; background: #f5faf6; }.question-number { display: grid; width: 25px; height: 25px; flex: none; place-items: center; border-radius: 50%; background: #f1f4f1; color: #829088; font-size: 12px; }.question-card.active .question-number { background: #e0f0e5; color: var(--green); }.question-info { display: grid; min-width: 0; flex: 1; gap: 4px; }.question-info small { color: #96a09a; font-size: 11px; }.question-info strong { display: -webkit-box; overflow: hidden; color: #57645c; font-size: 12px; font-weight: 500; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.question-duration { flex: none; color: #98a29c; font-size: 12px; }.question-card > .el-icon { flex: none; color: var(--green); font-size: 14px; }
/* 追问排在被追问的那道题后面：缩进一小格 + 琥珀色标签，一眼看出它不是独立的一道新题 */
.question-card.is-follow-up { margin-left: 14px; border-left: 3px solid #f0d9bb; }.question-card.is-follow-up .question-info small { color: #a9722f; font-weight: 600; }
.score-section { display: grid; gap: 10px; }.score-section > .section-title { margin: 0; }.score-panel { padding: 15px; box-shadow: none; }.score-empty { display: flex; align-items: center; flex-direction: column; padding: 10px 8px 13px; text-align: center; }.score-mark { display: grid; width: 39px; height: 39px; place-items: center; margin-bottom: 9px; border-radius: 50%; background: var(--blue-soft); color: var(--blue); font-size: 19px; }.score-empty strong { color: #4e5a53; font-size: 13px; }.score-empty p { margin: 7px 0 0; color: #89948e; font-size: 12px; line-height: 1.75; }.rubric-list { padding-top: 11px; border-top: 1px solid #edf0ed; }.rubric-list > div { display: flex; justify-content: space-between; margin: 11px 0; color: #7c8881; font-size: 12px; }.rubric-list i { color: #b0b8b2; font-style: normal; }.score-footnote { display: flex; gap: 5px; padding-top: 10px; border-top: 1px solid #edf0ed; color: #96a099; font-size: 11px; line-height: 1.6; }.score-footnote .el-icon { flex: none; color: #7298a5; }.evidence-panel { padding: 14px; box-shadow: none; }.evidence-panel .section-title { margin-bottom: 10px; }.evidence-row { display: flex; align-items: center; gap: 8px; }.timeline-play { display: grid; width: 25px; height: 25px; flex: none; place-items: center; border: 0; border-radius: 50%; background: var(--green-soft); color: var(--green); }.evidence-row > div { display: grid; flex: none; gap: 3px; }.evidence-row strong { color: #637067; font-size: 12px; font-weight: 600; }.evidence-row small { color: #99a29d; font-size: 11px; }.timeline-track { position: relative; height: 4px; flex: 1; overflow: hidden; border-radius: 3px; background: #e8eee9; }.timeline-track i { display: block; width: 15%; height: 100%; border-radius: inherit; background: var(--green); }.no-evidence { margin: 4px 0; color: #99a29d; font-size: 12px; line-height: 1.7; }
.tag-ready.el-tag { border-color: #cfe6d8; background: #eef8f1; color: #3f8259; }
.transcript-text { margin: 8px 0 12px; color: #5b6760; font-size: 13px; line-height: 1.85; }
.score-overall { display: flex; align-items: baseline; gap: 7px; padding-bottom: 11px; border-bottom: 1px solid #edf0ed; }
.score-overall strong { color: var(--green); font-size: 25px; font-weight: 650; }
.score-overall span { color: #8b968f; font-size: 12px; }
.rubric-list i.filled { color: #476b56; font-weight: 600; }
.suggestion-panel { padding: 14px; box-shadow: none; }
.suggestion-panel .section-title { margin-bottom: 8px; }
.suggestion-list { margin: 0; padding-left: 14px; color: #6d7972; font-size: 12px; line-height: 1.8; }
.suggestion-list li { margin: 5px 0; }
.delivery-list { padding-top: 11px; border-top: 1px solid #edf0ed; }
.delivery-list > div { display: flex; justify-content: space-between; margin: 8px 0; color: #7c8881; font-size: 12px; }
.delivery-list i { color: #4d6f5b; font-style: normal; }
.sample-panel { padding: 14px; box-shadow: none; }
.sample-panel .section-title { margin-bottom: 8px; }
/* 左：数字人；右：按钮与说明；下面：字幕 */
.sample-row { display: flex; align-items: flex-start; gap: 12px; margin-bottom: 4px; }
.sample-side { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.sample-side small { color: #8a958e; font-size: 11px; line-height: 1.5; }
.sample-play { display: inline-flex; align-items: center; gap: 5px; padding: 5px 10px; border: 1px solid #d8e5dc; border-radius: 6px; background: #fff; color: var(--green-dark); font-family: inherit; font-size: 14px; cursor: pointer; }
.sample-play:hover:not(:disabled) { border-color: var(--green); background: var(--green-soft); }
.sample-play:disabled { color: #9aa6a0; cursor: default; }
.sample-play.playing { border-color: var(--green); background: var(--green-soft); }
.sample-note { margin: 8px 0 0; color: #8a958e; font-size: 14px; line-height: 1.6; }
.sample-note.error { color: #a4483a; }
/* 示范回答旁边的"说话人"：小尺寸数字人，只为让口型动起来，不抢正文 */
.sample-avatar { width: 96px; flex: none; }
/* 面试间里的舞台尺寸由父组件给；这里要自己指定，否则加载中会撑成 160px 高 */
.sample-avatar :deep(.dh-stage) { aspect-ratio: 340 / 260; }
.sample-avatar :deep(.dh-placeholder) { min-height: 0; }
.sample-text { margin: 8px 0 0; color: #6d7972; font-size: 12px; line-height: 1.8; }
/* 朗读中的字幕：给一点左侧强调，让人一眼看出"这段正在被念" */
.sample-text.speaking { padding-left: 10px; border-left: 2px solid var(--green); color: #4a5650; }
/* 还没点「让它回答」时的占位提示：此时不显示示范回答正文 */
.sample-text.sample-hidden { padding: 12px; border: 1px dashed #dfe6e1; border-radius: 6px; background: #fafcfb; color: #94a09a; text-align: center; }
.evidence-list { display: grid; gap: 9px; }
.evidence-list .evidence-row { align-items: flex-start; }
@media(max-width: 900px) { .report-layout { grid-template-columns: minmax(0,1.1fr) minmax(220px,.8fr); gap: 11px; }.player-frame { min-height: 185px; } }
@media(max-width: 720px) { .report-layout { grid-template-columns: 1fr; }.score-section { grid-row: 1; grid-template-columns: 1fr; }.report-heading { align-items: flex-start; flex-direction: column; }.report-status { align-items: flex-start; }.analysis-tag { align-self: flex-start; }.player-frame { min-height: 190px; } }
</style>
<style scoped>
/* 评分口径说明：之前只给数字不给尺度，用户看到「10 分」会以为勉强及格，
   实际上 10 分位于「明显不足」区间。 */
.rubric-legend { margin-top: 11px; padding: 10px 11px; border: 1px solid #e8eeea; border-radius: 6px; background: #fafcfb; }
.rubric-legend .legend-title { color: #59665f; font-size: 13px; font-weight: 600; }
.rubric-legend ul { display: grid; gap: 4px; margin: 7px 0 0; padding: 0; list-style: none; }
.rubric-legend li { color: #66736c; font-size: 13px; line-height: 1.5; }
.rubric-legend li strong { color: #46534c; font-weight: 600; }
.rubric-legend p { margin: 8px 0 0; color: #66736c; font-size: 13px; line-height: 1.5; }
.transcript-edit-bar { display: grid; gap: 9px; margin-top: 9px; padding: 11px 12px; box-shadow: none; }
.transcript-edit-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.transcript-edit-head > div { display: flex; align-items: center; flex-wrap: wrap; gap: 7px; }
.transcript-edit-head strong { color: #536058; font-size: 13px; }
.transcript-edit-head small { color: #98a29d; font-size: 11px; }
.tag-warn.el-tag { border-color: #f0d9bb; background: #fdf6ea; color: #a9722f; }
.transcript-edit-actions { display: flex; justify-content: flex-end; gap: 8px; }
.transcript-edit-bar .text-action { justify-self: start; padding: 0; border: 0; background: transparent; color: var(--green); font-size: 12px; }
.text-action.muted { color: #8d9792; }
.transcript-original { margin: 0; padding: 7px 9px; border-radius: 6px; background: #f7f9f8; color: #7d8982; font-size: 12px; line-height: 1.7; }
</style>
