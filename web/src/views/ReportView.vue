<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getMedia } from '../services/mediaStore';
import {
  generateAnswerRewrite, getAnswerMedia, getPracticeReport, reEvaluateAnswer,
  retryPracticeAnalysis, saveAnswerRewrite, updateAnswerTranscript,
} from '../services/api';
import { buildQuestions, STYLE_LABEL_KEYS, TYPE_LABEL_KEYS } from '../data/questions';
import { DIMENSION_LABEL_KEYS, DIMENSION_LABELS } from '../services/practiceArchive';
import DigitalHuman from '../components/DigitalHuman.vue';
import { createAvatarAudio } from '../services/avatarAudio';
import { revealedSentenceCount, splitSentences } from '../services/mouthAnimation';
import {
  AVATAR_STATE, resetAvatarState, setAvatarError, setAvatarState, useAvatarState,
} from '../services/avatarState';
import { getSampleAnswerAudio, getSampleAnswerNarration } from '../services/api';
import { listCohorts, requestHelp } from '../services/cohorts';
import { useI18n } from 'vue-i18n';

const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();
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
const totalTime = computed(() => t('report.totalTime', {
  minutes: Math.floor(totalSeconds.value / 60),
  seconds: totalSeconds.value % 60,
}));
const interviewTypeLabel = computed(() => (
  TYPE_LABEL_KEYS[session.value?.interviewType]
    ? t(TYPE_LABEL_KEYS[session.value.interviewType])
    : t('report.mockInterview')
));
const interviewStyleLabel = computed(() => (
  STYLE_LABEL_KEYS[session.value?.interviewerStyle]
    ? t(STYLE_LABEL_KEYS[session.value.interviewerStyle])
    : t('report.professionalCalm')
));
const formattedSessionDate = computed(() => new Intl.DateTimeFormat(locale.value, {
  year: 'numeric', month: 'long', day: 'numeric',
}).format(new Date(session.value?.completedAt || session.value?.createdAt || Date.now())));
const currentReport = computed(() => report.value?.questions?.find((item) => item.questionIndex === currentIndex.value) || null);
const currentEvaluation = computed(() => currentReport.value?.evaluation || null);
const transcriptEdited = computed(() => Boolean(
  currentReport.value?.transcriptEditedAt || currentReport.value?.transcriptOriginal));
const editingTranscript = ref(false);
const transcriptDraft = ref('');
const transcriptSaving = ref(false);
const showTranscriptOriginal = ref(false);
const rewriteDraft = ref('');
const rewriteEditing = ref(false);
const rewriting = ref(false);
const savingRewrite = ref(false);
const selectedAttemptId = ref('');
const cohorts = ref([]);
const helpCohortId = ref('');
const helpNote = ref('');
const helpSending = ref(false);
const signedMediaUrl = computed(() => currentReport.value?.mediaSignedUrl || '');
const evidenceList = computed(() => currentEvaluation.value?.evidence || []);
/** 追问在这份报告里算"顺着上一题问的"，不算凭空多出来的一道题。 */
const followUpCount = computed(() => report.value?.summary?.followUpCount || 0);
const learnerCohorts = computed(() => cohorts.value.filter((cohort) => cohort.role === 'learner'));

watch(currentReport, (value) => {
  rewriteDraft.value = value?.rewriteText || '';
  rewriteEditing.value = false;
  selectedAttemptId.value = value?.answerId || '';
}, { immediate: true });

function followUpLabel(index) {
  const item = report.value?.questions?.find((question) => question.questionIndex === index);
  if (!item?.followUp) return '';
  const parent = Number.isInteger(item.parentQuestionIndex) ? item.parentQuestionIndex + 1 : null;
  const round = item.followUpRound > 1
    ? t('report.followUpNth', { round: item.followUpRound })
    : t('session.interviewerFollowUp');
  return parent ? t('report.followUpAbout', { label: round, parent }) : round;
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
      ElMessage.warning(t('report.sampleAutoplayBlocked'));
      return;
    }
    sampleSpeaking.value = true;
  } catch (error) {
    sampleSpeaking.value = false;
    finished.value = true;           // 拿不到语音也要让用户能读到文字
    if (error?.status === 503) ElMessage.warning(t('report.speechUnavailable'));
    else ElMessage.error(error?.message || t('report.samplePlayFailed'));
    setAvatarState(AVATAR_STATE.IDLE);
  } finally {
    sampleLoading.value = false;
  }
}
const dimensions = computed(() => Object.keys(DIMENSION_LABEL_KEYS).map((key) => ({
  key,
  label: t(DIMENSION_LABEL_KEYS[key]),
})));
const dimensionLabels = computed(() => Object.fromEntries(
  Object.keys(DIMENSION_LABELS).map((key) => [key, t(DIMENSION_LABEL_KEYS[key])]),
));
const reportStatusLabel = computed(() => {
  if (reportLoading.value) return t('report.analyzing');
  const status = report.value?.status;
  if (status === 'ready') return t('report.analysisReady');
  if (status === 'partial') return t('report.partialReady');
  if (status === 'analysis_failed') return t('report.analysisFailed');
  if (report.value?.analysis?.status === 'processing') return t('report.analyzing');
  if (status === 'awaiting_analysis') return t('report.awaitingAnalysis');
  return session.value?.answers.some((answer) => answer.mediaId)
    ? t('report.awaitingAnalysis')
    : t('report.videoNotSynced');
});
const reportHeadline = computed(() => (
  report.value?.status === 'ready' ? t('report.readyHeadline') : t('report.completedHeadline')
));
const reportSummaryText = computed(() => {
  const base = t('report.summaryBase', { count: session.value?.answers.length || 0, duration: totalTime.value });
  if (reportLoading.value) return t('report.summaryReading', { base });
  const analysis = report.value?.analysis;
  if (analysis?.status === 'failed') {
    return t('report.summaryAnalysisFailed', {
      base,
      error: analysis.error ? t('report.summaryErrorDetail', { error: analysis.error }) : '',
    });
  }
  const summary = report.value?.summary;
  if (summary?.averageOverallScore != null) {
    const details = [];
    if (summary.speechRateCharactersPerMinute) details.push(t('report.speechRate', { value: Math.round(summary.speechRateCharactersPerMinute) }));
    if (summary.totalPauseCount) details.push(t('report.pauses', { count: summary.totalPauseCount }));
    if (summary.totalFillerWordCount) details.push(t('report.fillers', { count: summary.totalFillerWordCount }));
    const followUps = followUpCount.value ? t('report.followUps', { count: followUpCount.value }) : '';
    return t('report.summaryScore', {
      base,
      score: summary.averageOverallScore.toFixed(1),
      details: details.length ? ` · ${details.join(' · ')}` : '',
      followUps,
    });
  }
  return t('report.summaryPending', { base });
});
let activeUrl;
let mediaRequest = 0;
let reportPoller;
const REPORT_POLL_MS = 10000;

onMounted(() => {
  const requested = Number(route.query.index);
  if (Number.isInteger(requested) && requested >= 0 && requested < questions.value.length) {
    selectQuestion(requested);
  } else if (session.value?.answers.length) {
    selectQuestion(session.value.answers[0].questionIndex);
  }
  loadHelpCohorts();
});
watch(() => route.params.id, () => { if (!session.value) router.replace('/history'); });
// 直接打开或刷新报告页时，练习记录是异步从服务端拉取的。
// 只监听路由参数会在 store 尚未就绪时错过加载，页面会永远停在「尚未生成」。
watch(() => session.value?.id, (id) => { if (id) loadReport(); }, { immediate: true });

async function loadHelpCohorts() {
  try {
    cohorts.value = await listCohorts();
    if (learnerCohorts.value.length) helpCohortId.value = learnerCohorts.value[0].id;
  } catch {
    cohorts.value = [];
  }
}

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
    ElMessage.error(t('report.retryFailed'));
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
  rewriteEditing.value = false;
  selectedAttemptId.value = '';
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
    if (!signedMediaUrl.value) ElMessage.error(t('report.videoReadFailed'));
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
    ElMessage.success(t('report.transcriptUpdated'));
    await loadReport();
  } catch (error) {
    ElMessage.error(error?.message || t('report.transcriptSaveFailed'));
  } finally {
    transcriptSaving.value = false;
  }
}

async function generateRewrite() {
  if (!currentReport.value?.answerId || rewriting.value) return;
  rewriting.value = true;
  try {
    const result = await generateAnswerRewrite(currentReport.value.answerId);
    rewriteDraft.value = result.rewriteText || '';
    rewriteEditing.value = false;
    // 报告是服务端组装的：生成成功后重新拉一次，页面才有 rewriteText / source 标签
    await loadReport();
    ElMessage.success(t('report.rewriteGenerated'));
  } catch (error) {
    ElMessage.error(error?.message || t('report.rewriteGenerateFailed'));
  } finally {
    rewriting.value = false;
  }
}

async function saveRewrite() {
  if (!currentReport.value?.answerId || savingRewrite.value) return;
  const text = rewriteDraft.value.trim();
  if (!text) {
    ElMessage.warning(t('report.rewriteEmpty'));
    return;
  }
  savingRewrite.value = true;
  try {
    await saveAnswerRewrite(currentReport.value.answerId, text);
    rewriteEditing.value = false;
    ElMessage.success(t('report.rewriteSaved'));
    await loadReport();
  } catch (error) {
    ElMessage.error(error?.message || t('report.rewriteSaveFailed'));
  } finally {
    savingRewrite.value = false;
  }
}

async function startRetake() {
  if (!currentReport.value?.answerId) return;
  router.push(`/practice/${route.params.id}/retake/${currentIndex.value}?sourceAnswerId=${currentReport.value.answerId}`);
}

async function playAttempt(attempt) {
  if (!attempt) return;
  selectedAttemptId.value = attempt.answerId;
  if (activeUrl) URL.revokeObjectURL(activeUrl);
  activeUrl = undefined;
  try {
    const blob = await getAnswerMedia(attempt.answerId);
    activeUrl = URL.createObjectURL(blob);
    mediaUrl.value = activeUrl;
  } catch {
    mediaUrl.value = attempt.mediaSignedUrl || '';
    if (!mediaUrl.value) ElMessage.error(t('report.attemptVideoFailed'));
  }
}

async function submitHelp() {
  if (!helpCohortId.value || !currentReport.value?.answerId || helpSending.value) return;
  helpSending.value = true;
  try {
    await requestHelp(helpCohortId.value, {
      sessionId: route.params.id,
      questionIndex: currentIndex.value,
      answerId: currentReport.value.answerId,
      note: helpNote.value.trim() || null,
    });
    helpNote.value = '';
    ElMessage.success(t('report.helpSent'));
  } catch (error) {
    ElMessage.error(error?.message || t('report.sendFailed'));
  } finally {
    helpSending.value = false;
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
  try {
    await ElMessageBox.confirm(
      t('report.deleteConfirmBody'),
      t('report.deleteConfirmTitle'),
      { confirmButtonText: t('report.deleteConfirmAction'), cancelButtonText: t('common.cancel'), type: 'warning' },
    );
  }
  catch { return; }
  try {
    await practice.remove(session.value.id);
    ElMessage.success(t('report.deleted'));
    router.replace('/history');
  } catch (error) {
    ElMessage.error(error.message || t('report.deleteFailed'));
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
    <div class="report-heading"><div><button class="report-back" @click="router.push('/history')"><el-icon><ArrowLeft /></el-icon>{{ t('report.backHistory') }}</button><h1>{{ session.role }} <span>{{ t('report.reportSuffix') }}</span></h1><p>{{ interviewTypeLabel }} · {{ interviewStyleLabel }} · {{ formattedSessionDate }}</p></div><el-button class="delete-button" @click="removeSession"><el-icon><Delete /></el-icon>{{ t('report.deleteData') }}</el-button></div>
    <div class="report-status panel"><div class="status-symbol"><el-icon><VideoCamera /></el-icon></div><div class="status-copy"><strong>{{ reportHeadline }}</strong><span>{{ reportSummaryText }}</span></div><el-tag class="analysis-tag" size="small"><i></i>{{ reportStatusLabel }}</el-tag></div>
    <div class="report-layout">
      <section class="review-section"><div class="section-title">{{ t('report.replayTitle') }} <small>{{ t('report.videoCount', { count: session.answers.length }) }}<template v-if="followUpCount">{{ t('report.followUpsPart', { count: followUpCount }) }}</template>{{ t('report.switchClipHint') }}</small></div><div class="player-panel panel"><div class="player-frame"><video v-if="mediaUrl" ref="video" :src="mediaUrl" controls playsinline></video><div v-else class="player-empty"><el-icon v-if="mediaLoading" class="loading"><Loading /></el-icon><template v-else><el-icon><VideoCamera /></el-icon><strong>{{ currentAnswer ? t('report.unavailableClip') : t('report.notRecorded') }}</strong><span>{{ currentAnswer ? t('report.localCleared') : t('report.incomplete') }}</span></template></div><div class="player-label">{{ String(currentIndex + 1).padStart(2,'0') }} / {{ String(questions.length).padStart(2,'0') }}</div></div><div class="transcript-panel"><div class="transcript-header"><strong>{{ t('report.transcript') }}</strong><el-tag :class="currentReport?.transcriptText ? 'tag-ready' : 'tag-muted'" size="small">{{ currentReport?.transcriptText ? t('report.generated') : t('report.notGenerated') }}</el-tag></div><p v-if="currentReport?.transcriptText" class="transcript-text">{{ currentReport.transcriptText }}</p><p v-else>{{ t('report.transcriptEmpty') }}</p><div v-if="currentAnswer" class="answer-meta"><span><el-icon><Timer /></el-icon>{{ t('report.answerDuration', { duration: formatDuration(currentAnswer.durationSeconds) }) }}</span><span><el-icon><Files /></el-icon>{{ (currentAnswer.bytes / 1024 / 1024).toFixed(1) }} MB</span></div></div></div>
      <div v-if="currentReport?.transcriptText" class="transcript-edit-bar panel">
        <div class="transcript-edit-head">
          <div>
            <strong>{{ t('report.transcriptCorrection') }}</strong>
            <el-tag v-if="transcriptEdited" class="tag-warn" size="small">{{ t('report.manuallyCorrected') }}</el-tag>
            <small>{{ t('report.correctionHint') }}</small>
          </div>
          <button v-if="!editingTranscript" class="text-action" @click="beginTranscriptEdit">
            <el-icon><EditPen /></el-icon>{{ t('report.editTranscript') }}
          </button>
        </div>
        <template v-if="editingTranscript">
          <el-input v-model="transcriptDraft" type="textarea" :rows="4" maxlength="20000" show-word-limit />
          <div class="transcript-edit-actions">
            <el-button class="soft-button" :disabled="transcriptSaving" @click="editingTranscript = false">{{ t('common.cancel') }}</el-button>
            <el-button class="primary-button" :loading="transcriptSaving" @click="saveTranscript">{{ t('report.saveAndRegrade') }}</el-button>
          </div>
        </template>
        <template v-else>
          <button v-if="currentReport?.transcriptOriginal" class="text-action muted"
                  @click="showTranscriptOriginal = !showTranscriptOriginal">
            {{ showTranscriptOriginal ? t('report.collapseOriginal') : t('report.viewOriginal') }}
          </button>
          <p v-if="showTranscriptOriginal && currentReport?.transcriptOriginal" class="transcript-original">
            {{ t('report.original', { text: currentReport.transcriptOriginal }) }}
          </p>
        </template>
      </div>
      <div v-if="currentReport?.transcriptText" class="rewrite-panel panel">
        <div class="transcript-edit-head">
          <div>
            <strong>{{ t('report.rewriteTitle') }}</strong>
            <el-tag v-if="currentReport?.rewriteText" class="tag-warn" size="small">{{ currentReport.rewriteSource === 'user' ? t('report.edited') : t('report.aiRewrite') }}</el-tag>
            <small>{{ t('report.rewriteHint') }}</small>
          </div>
          <div class="rewrite-actions">
            <el-button class="soft-button" :loading="rewriting" @click="generateRewrite">
              {{ currentReport?.rewriteText ? t('report.regenerate') : t('report.askAiRewrite') }}
            </el-button>
            <el-button v-if="currentReport?.rewriteText" class="soft-button" @click="rewriteEditing = !rewriteEditing">
              {{ rewriteEditing ? t('report.cancelEdit') : t('report.edit') }}
            </el-button>
          </div>
        </div>
        <template v-if="rewriteEditing">
          <el-input v-model="rewriteDraft" type="textarea" :rows="4" maxlength="4000" show-word-limit />
          <div class="transcript-edit-actions">
            <el-button class="soft-button" :disabled="savingRewrite" @click="rewriteEditing = false">{{ t('common.cancel') }}</el-button>
            <el-button class="primary-button" :loading="savingRewrite" @click="saveRewrite">{{ t('report.saveRewrite') }}</el-button>
          </div>
        </template>
        <p v-else-if="currentReport?.rewriteText" class="rewrite-text">{{ currentReport.rewriteText }}</p>
        <p v-else class="rewrite-empty">{{ t('report.rewriteEmptyHint') }}</p>
        <div v-if="currentReport?.rewriteText" class="rewrite-footer">
          <button class="text-action" @click="startRetake"><el-icon><Refresh /></el-icon>{{ t('report.retryTake', { attempt: (currentReport?.attempt || 1) + 1 }) }}</button>
          <small>{{ t('report.maxVersions') }}</small>
        </div>
      </div>
      <div v-if="learnerCohorts.length && currentReport?.answerId" class="help-panel panel">
        <div class="transcript-edit-head">
          <div><strong>{{ t('report.helpTitle') }}</strong><small>{{ t('report.helpHint') }}</small></div>
        </div>
        <div class="help-form">
          <el-select v-model="helpCohortId" class="help-select">
            <el-option v-for="cohort in learnerCohorts" :key="cohort.id" :label="cohort.name" :value="cohort.id" />
          </el-select>
          <el-input v-model="helpNote" maxlength="1000" :placeholder="t('report.helpPlaceholder')" />
          <el-button class="soft-button" :loading="helpSending" @click="submitHelp">{{ t('report.sendTeacher') }}</el-button>
        </div>
      </div>
      <div v-if="(currentReport?.attempts?.length || 0) > 1" class="attempt-panel panel">
        <div class="section-title">{{ t('report.versionCompare') }} <small>{{ t('report.oldToNew') }}</small></div>
        <div class="attempt-grid">
          <button v-for="attempt in currentReport.attempts" :key="attempt.answerId" class="attempt-card"
                  :class="{ current: attempt.current, selected: selectedAttemptId === attempt.answerId }"
                  @click="playAttempt(attempt)">
            <span>{{ t('report.attemptNth', { attempt: attempt.attempt }) }}<el-tag v-if="attempt.current" class="tag-ready" size="small">{{ t('report.current') }}</el-tag></span>
            <strong>{{ attempt.evaluation?.overallScore != null ? t('dashboard.points', { score: attempt.evaluation.overallScore.toFixed(1) }) : t('report.pendingScore') }}</strong>
            <p>{{ attempt.transcriptText || t('report.noTranscript') }}</p>
          </button>
        </div>
        <p class="attempt-tip">{{ t('report.attemptTip') }}</p>
      </div>
      <div class="question-list"><button v-for="(question,index) in questions" :key="question.id" class="question-card panel" :class="{ active: currentIndex === index, 'is-follow-up': Boolean(followUpLabel(index)) }" @click="selectQuestion(index)"><span class="question-number">{{ String(index + 1).padStart(2,'0') }}</span><span class="question-info"><small>{{ followUpLabel(index) || question.category }}</small><strong>{{ question.text }}</strong></span><span class="question-duration">{{ session.answers.find((answer) => answer.questionIndex === index) ? formatDuration(session.answers.find((answer) => answer.questionIndex === index).durationSeconds) : t('report.unanswered') }}</span><el-icon v-if="currentIndex === index"><ArrowRight /></el-icon></button></div></section>
      <aside class="score-section">
        <div class="section-title">{{ t('report.answerAnalysis') }} <el-tag :class="currentEvaluation ? 'tag-ready' : 'tag-muted'" size="small">{{ currentEvaluation ? t('report.generated') : (reportLoading ? t('report.analyzing') : t('report.waitingIntegration')) }}</el-tag></div>
        <div class="score-panel panel">
          <template v-if="currentEvaluation">
            <div class="score-overall"><strong>{{ currentEvaluation.overallScore.toFixed(1) }}</strong><span>{{ t('report.overallScore') }}</span></div>
            <div class="rubric-list"><div v-for="dimension in dimensions" :key="dimension.key"><span>{{ dimension.label }}</span><i class="filled">{{ scoreOf(dimension.key) }}</i></div></div>
            <div class="delivery-list">
              <div><span>{{ t('report.speechRateLabel') }}</span><i>{{ t('report.charsPerMinute', { value: Math.round(currentEvaluation.delivery.speechRateCharactersPerMinute) }) }}</i></div>
              <div><span>{{ t('report.pauseLabel') }}</span><i>{{ t('report.pauseValue', { count: currentEvaluation.delivery.pauseCount, seconds: currentEvaluation.delivery.totalPauseSeconds }) }}</i></div>
              <div><span>{{ t('report.fillerLabel') }}</span><i>{{ t('report.countTimes', { count: currentEvaluation.delivery.fillerWordCount }) }}</i></div>
            </div>
          </template>
          <template v-else>
            <div class="score-empty">
              <div class="score-mark"><el-icon><DataAnalysis /></el-icon></div>
              <strong>{{ reportLoading ? t('report.loadingAnalysis') : (report?.analysis?.status === 'failed' ? t('report.analysisFailed') : t('report.noAnalysis')) }}</strong>
              <p>{{ t('report.analysisHint') }}</p>
              <el-button v-if="report?.analysis?.status === 'failed'" class="soft-button" @click="retryAnalysis">{{ t('report.retryAnalysis') }}</el-button>
            </div>
            <div class="rubric-list"><div v-for="dimension in dimensions" :key="dimension.key"><span>{{ dimension.label }}</span><i>—</i></div></div>
          </template>
          <div class="rubric-legend">
            <div class="legend-title">{{ t('report.rubricTitle') }}</div>
            <ul><li><strong>80-100</strong> {{ t('report.rubricGood') }}</li><li><strong>60-79</strong> {{ t('report.rubricPass') }}</li><li><strong>40-59</strong> {{ t('report.rubricWeak') }}</li><li><strong>0-39</strong> {{ t('report.rubricPoor') }}</li></ul>
            <p>{{ t('report.rubricNote') }}</p>
          </div>
          <div class="score-footnote"><el-icon><InfoFilled /></el-icon>{{ t('report.privacyFootnote') }}</div>
        </div>
        <div v-if="currentEvaluation?.improvementSuggestions?.length" class="suggestion-panel panel">
          <div class="section-title">{{ t('report.suggestions') }}</div>
          <ul class="suggestion-list"><li v-for="(tip, index) in currentEvaluation.improvementSuggestions" :key="index">{{ tip }}</li></ul>
        </div>
        <div v-if="currentEvaluation?.sampleAnswer" class="sample-panel panel">
          <div class="section-title">{{ t('report.sampleTitle') }}</div>
          <div class="sample-row">
            <div class="sample-avatar"><DigitalHuman :speaking="sampleSpeaking" :amplitude="avatarAudio.amplitude" /></div>
            <div class="sample-side">
              <button class="sample-play" :class="{ playing: sampleSpeaking }" :disabled="sampleLoading" @click="speakSampleAnswer"><el-icon><VideoPause v-if="sampleSpeaking" /><Microphone v-else /></el-icon><span>{{ sampleSpeaking ? t('report.stop') : (sampleLoading ? t('report.preparing') : t('report.letAnswer')) }}</span></button>
              <small v-if="!sampleSpeaking && !sampleLoading">{{ t('report.sampleTip') }}</small><small v-else-if="sampleSpeaking">{{ t('report.sampleSpeakingHint') }}</small>
            </div>
          </div>
          <p v-if="visibleSampleText" class="sample-text" :class="{ speaking: sampleSpeaking }">{{ visibleSampleText }}</p>
          <p v-else class="sample-text sample-hidden">{{ t('report.sampleHidden') }}</p>
          <p v-if="sampleTruncated" class="sample-note">{{ t('report.sampleTruncated') }}</p>
          <p v-if="avatarError" class="sample-note error">{{ avatarError }}</p>
        </div>
        <div class="evidence-panel panel">
          <div class="section-title">{{ t('report.evidenceTitle') }} <small>{{ t('report.videoTimeline') }}</small></div>
          <div v-if="evidenceList.length" class="evidence-list">
            <div v-for="(item, index) in evidenceList" :key="index" class="evidence-row">
              <button class="timeline-play" :aria-label="t('report.playEvidence', { number: index + 1 })" :title="t('report.playEvidenceAt', { time: formatDuration(item.startSeconds) })" @click="playAt(item.startSeconds)"><el-icon><VideoPlay /></el-icon></button>
              <div><strong>{{ dimensionLabels[item.dimension] || item.dimension }}</strong><small>{{ formatDuration(item.startSeconds) }} – {{ formatDuration(item.endSeconds) }} · {{ item.quote }}</small></div>
              <span class="timeline-track"><i :style="{ width: evidenceWidth(item) }"></i></span>
            </div>
          </div>
          <div v-else-if="currentAnswer" class="evidence-row">
            <button class="timeline-play" :aria-label="t('report.playThisVideo')" :title="t('report.playThisVideo')" @click="playAt(0)"><el-icon><VideoPlay /></el-icon></button>
            <div><strong>{{ t('report.answerClip') }}</strong><small>00:00 – {{ formatDuration(currentAnswer.durationSeconds) }}</small></div>
            <span class="timeline-track"><i></i></span>
          </div>
          <p v-else class="no-evidence">{{ t('report.noEvidence') }}</p>
        </div>
      </aside>
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
.rewrite-panel, .attempt-panel { display: grid; gap: 9px; margin-top: 9px; padding: 11px 12px; box-shadow: none; }
.rewrite-actions { display: flex; gap: 7px; }
.rewrite-text { margin: 0; padding: 9px 10px; border-radius: 6px; background: #f5faf6; color: #46524c; font-size: 13px; line-height: 1.8; white-space: pre-wrap; }
.rewrite-empty { margin: 0; color: #98a29d; font-size: 12px; }
.rewrite-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.rewrite-footer small { color: #98a29d; font-size: 11px; }
.attempt-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 8px; }
.attempt-card { display: grid; gap: 5px; padding: 9px 10px; border: 1px solid #eef1ee; border-radius: 7px; background: white; text-align: left; }
.attempt-card.current { border-color: #b9d3c2; background: #f5faf6; }
.attempt-card.selected { box-shadow: inset 0 0 0 1px var(--green); }
.attempt-card > span { display: flex; align-items: center; gap: 5px; color: #7d8982; font-size: 11px; }
.attempt-card strong { color: #2f3c35; font-size: 15px; }
.attempt-card p { display: -webkit-box; overflow: hidden; margin: 0; color: #7d8982; font-size: 11px; line-height: 1.6; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.attempt-tip { margin: 0; color: #98a29d; font-size: 11px; }
.help-panel { display: grid; gap: 9px; margin-top: 9px; padding: 11px 12px; box-shadow: none; }
.help-form { display: grid; grid-template-columns: 180px minmax(0, 1fr) auto; gap: 8px; align-items: center; }
.help-select { width: 100%; }
@media (max-width: 700px) { .help-form { grid-template-columns: 1fr; }.help-form .el-button { justify-self: start; } }
</style>
