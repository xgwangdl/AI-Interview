<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getActiveStream, stopActiveStream, supportedRecorderType } from '../services/mediaCapture';
import { getMedia, saveMedia } from '../services/mediaStore';
import { isPermanentUploadRejection, uploadRejectionMessage } from '../services/mediaErrors';
import { buildQuestions, styleLabels } from '../data/questions';
import {
  getNarrationAudio, getQuestionNarration, requestFollowUp, tryCompleteAnswer, tryCompleteSession, trySaveAnswer,
  tryPauseSession,
} from '../services/api';
import { uploadAnswerMediaWithFallback } from '../services/chunkedUpload';
import DigitalHuman from '../components/DigitalHuman.vue';
import { createAvatarAudio } from '../services/avatarAudio';
import {
  AVATAR_STATE, fromBackendState, resetAvatarState, setAvatarError, setAvatarState, useAvatarState,
} from '../services/avatarState';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const recorderState = ref('idle');
const currentIndex = ref(0);
const elapsed = ref(0);
const savedAnswer = ref(false);
const followUpPending = ref(false);
const followUpNotice = ref('');
const narrationBlobUrl = ref('');
const narration = ref(null);
const narrationLoading = ref(false);
const speaking = ref(false);
const narrationAudioFailed = ref(false);
// 数字人的状态只有 avatarState.js 一个来源，这里只读取
const { state: avatarState, error: avatarError, needsGesture } = useAvatarState();
// 配音播放器：单例，内含唯一的 MediaElementAudioSourceNode（见 services/avatarAudio.js）
const avatarAudio = createAvatarAudio();
const stream = getActiveStream();
const draft = practice.draft?.id === route.params.id ? practice.draft : practice.find(route.params.id);
const questions = computed(() => draft ? (draft.questions || buildQuestions(draft.role, draft.questionCount, draft.interviewType)) : []);
const currentQuestion = computed(() => questions.value[currentIndex.value]);
const answeredQuestions = computed(() => new Set(draft?.answers.map((answer) => answer.questionIndex) || []));
const continuous = computed(() => draft?.mode === 'continuous');
const sessionStarted = ref(false);
const sessionElapsed = ref(0);
const sessionPaused = ref(false);
const pauseUsed = computed(() => (draft?.pausedCount || 0) >= 1);
const finishing = ref(false);
let sessionStartedAt = 0;
let sessionPausedAt = 0;
let sessionPausedMs = 0;
let sessionTimer;

/**
 * 当前这题是第几轮追问（0 = 原题）。
 *
 * 追问是插在它所追问的那道题后面的（前后端一致），所以从当前题往前数连续的追问题目即可。
 * 不引入额外状态：题目列表本身就是唯一事实。
 */
const followUpRound = computed(() => {
  let round = 0;
  for (let index = currentIndex.value; index >= 0; index--) {
    if (questions.value[index]?.category !== 'follow_up') break;
    round += 1;
  }
  return round;
});
const questionKickerLabel = computed(() => (followUpRound.value > 0
  ? (followUpRound.value > 1 ? `追问 ${followUpRound.value}` : '面试官追问')
  : (currentQuestion.value?.category || '')));
const progress = computed(() => draft ? Math.round((answeredQuestions.value.size / questions.value.length) * 100) : 0);
const formattedTime = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`);
const formattedSessionTime = computed(() => `${String(Math.floor(sessionElapsed.value / 60)).padStart(2, '0')}:${String(sessionElapsed.value % 60).padStart(2, '0')}`);
// 数字人字幕只显示「朗读状态」。问题正文统一放在下方的问题卡片里：
// 之前这里绑定的是整段问题文本，长句会溢出画面、压在小人身上被裁掉，
// 用户看到的就是同一句问题出现两遍、而且上面那遍还看不全。
const narrationCaption = computed(() => {
  if (followUpPending.value) return '面试官正在考虑是否追问…';
  if (narrationLoading.value) return '正在准备这道题…';
  if (speaking.value) return '正在朗读这道题…';
  if (narrationAudioFailed.value) return '语音暂不可用，请阅读下方问题';
  if (needsGesture.value) return '浏览器拦了自动播放，点「重播问题」即可';
  if (narration.value?.audioAvailable) return '可点击重播再听一遍';
  return '请阅读下方问题';
});

// 播放结束 → 嘴型必须停回闭嘴，并按当前是否在录制切到对应状态。
// 只在这里改状态，组件与模板都不直接写 avatarState。
avatarAudio.on('ended', () => {
  speaking.value = false;
  setAvatarState(recorderState.value === 'recording' ? AVATAR_STATE.RECORDING : AVATAR_STATE.LISTENING);
  scheduleAutoRecord();
});

// 播放失败（格式不支持、地址失效等）→ 记错误并恢复静止，不让嘴一直动。
avatarAudio.on('error', (error) => {
  speaking.value = false;
  narrationAudioFailed.value = true;
  setAvatarError(error?.message);
});
let recorder;
let chunks = [];
let startedAt;
let pausedAt;
let pausedMilliseconds = 0;
let elapsedTimer;
let stopPromise;

onMounted(async () => {
  if (!draft) {
    ElMessage.warning('未找到这次练习，请重新开始。');
    router.replace('/practice/setup');
    return;
  }
  practice.resumeDraft(draft);
  if (!stream?.active) {
    ElMessage.info('请重新检测摄像头和麦克风。');
    router.replace(`/practice/device-check?id=${draft.id}`);
    return;
  }
  if (answeredQuestions.value.size === questions.value.length) currentIndex.value = 0;
  if (video.value) video.value.srcObject = stream;
  const nextQuestion = questions.value.findIndex((_, index) => !answeredQuestions.value.has(index));
  currentIndex.value = nextQuestion === -1 ? 0 : nextQuestion;
  if (!continuous.value) await loadNarration();
});

watch(currentIndex, () => { loadNarration(); });

async function loadNarration() {
  stopNarration();
  releaseNarrationAudio();
  narration.value = null;
  narrationAudioFailed.value = false;
  if (!draft?.id) return;
  narrationLoading.value = true;
  setAvatarState(AVATAR_STATE.PREPARING);
  try {
    const result = await getQuestionNarration(draft.id, currentIndex.value);
    narration.value = result;
    if (result.audioAvailable && result.audioUrl && await prepareNarrationAudio()) {
      setAvatarState(fromBackendState(result.state, AVATAR_STATE.SPEAKING));
      await playNarration();
    } else {
      setAvatarState(fromBackendState(result.state, AVATAR_STATE.LISTENING));
    }
  } catch {
    narration.value = null;
    setAvatarState(AVATAR_STATE.LISTENING);
  } finally {
    narrationLoading.value = false;
    // 没有配音 / 自动播放被拦时，不能让连续面试停在"准备中"
    if (continuous.value && sessionStarted.value && !speaking.value && !sessionPaused.value) {
      scheduleAutoRecord();
    }
  }
}

/**
 * 取回语音并用 blob: URL 交给 <audio>。
 *
 * 之前直接把受保护的接口地址塞进 audio.src，而 <audio> 发请求时不会带
 * Authorization 头，于是稳定拿到 401 —— 用户看到的就是「语音暂不可用」。
 */
async function prepareNarrationAudio() {
  try {
    const blob = await getNarrationAudio(draft.id, currentIndex.value);
    narrationBlobUrl.value = URL.createObjectURL(blob);
    avatarAudio.load(narrationBlobUrl.value);
    return true;
  } catch {
    narrationAudioFailed.value = true;
    return false;
  }
}

function releaseNarrationAudio() {
  if (narrationBlobUrl.value) {
    URL.revokeObjectURL(narrationBlobUrl.value);
    narrationBlobUrl.value = '';
  }
}

/** 播放配音。speaking 只由这里与播放结束事件改写，避免多处写入不一致。 */
async function playNarration() {
  if (!narrationBlobUrl.value) return;
  try {
    const outcome = await avatarAudio.play();
    if (outcome === 'needs-gesture') {
      // 浏览器拦了自动播放：不无限重试，交给用户点「重播问题」。
      speaking.value = false;
      setAvatarState(AVATAR_STATE.LISTENING, { needsGesture: true });
      return;
    }
    speaking.value = true;
    setAvatarState(AVATAR_STATE.SPEAKING);
  } catch (error) {
    narrationAudioFailed.value = true;
    speaking.value = false;
    setAvatarError(error?.message);
  }
}

/** 用户手势触发：既重播，也顺带解锁 AudioContext（音量分析要靠它）。 */
async function replayNarration() {
  if (!narrationBlobUrl.value) return;
  if (speaking.value) {
    stopNarration();
    return;
  }
  try {
    const outcome = await avatarAudio.playFromGesture();
    speaking.value = true;
    setAvatarState(AVATAR_STATE.SPEAKING, { needsGesture: false });
    if (outcome !== 'analyser-ready') {
      // 能出声但拿不到音量数据（例如跨域读不到音频）：降级为定时嘴型，
      // 组件会自动走 TALK_ROTATION，这里只记一条日志便于排查。
      console.warn('[session] 无法分析音量，嘴型降级为定时切换');
    }
  } catch (error) {
    ElMessage.warning(error?.message || '无法播放面试官语音，请检查浏览器音频权限。');
  }
}

function stopNarration() {
  avatarAudio.stop();
  speaking.value = false;
  if (avatarState.value === AVATAR_STATE.SPEAKING) {
    setAvatarState(recorderState.value === 'recording' ? AVATAR_STATE.RECORDING : AVATAR_STATE.LISTENING);
  }
}

function startRecording() {
  if (!stream?.active || recorderState.value === 'recording') return;
  if (continuous.value && !sessionStarted.value) return;
  if (typeof MediaRecorder === 'undefined') {
    recorderState.value = 'error';
    ElMessage.error('当前浏览器不支持视频录制，请使用桌面版 Chrome 或 Edge。');
    return;
  }
  try {
    chunks = [];
    const mimeType = supportedRecorderType();
    recorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);
    recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
    recorder.onerror = () => { recorderState.value = 'error'; ElMessage.error('录制中断，请重试本题。'); };
    recorder.onstop = persistAnswer;
    recorder.start(1000);
    startedAt = Date.now();
    pausedAt = undefined;
    pausedMilliseconds = 0;
    elapsed.value = 0;
    savedAnswer.value = false;
    recorderState.value = 'recording';
    // 用户开始回答：数字人进入「录制回答」状态（闭嘴、保持安静）
    setAvatarState(AVATAR_STATE.RECORDING);
    elapsedTimer = window.setInterval(() => {
      const currentlyPaused = pausedAt ? Date.now() - pausedAt : 0;
      elapsed.value = Math.max(0,
        Math.floor((Date.now() - startedAt - pausedMilliseconds - currentlyPaused) / 1000));
      // 连续面试单题 3 分钟上限：真实面试不会让人无限讲下去
      if (continuous.value && elapsed.value >= 180 && recorderState.value === 'recording') void stopRecording();
    }, 250);
  } catch {
    recorderState.value = 'error';
    ElMessage.error('此浏览器无法启动录制，请更新 Chrome 或 Edge 后重试。');
  }
}

function pauseRecording() {
  if (recorderState.value === 'recording' && recorder?.state === 'recording') {
    recorder.pause();
    pausedAt = Date.now();
    recorderState.value = 'paused';
  } else if (recorderState.value === 'paused' && recorder?.state === 'paused') {
    recorder.resume();
    pausedMilliseconds += Date.now() - pausedAt;
    pausedAt = undefined;
    recorderState.value = 'recording';
  }
}

function startContinuous() {
  if (!continuous.value || sessionStarted.value) return;
  sessionStarted.value = true;
  sessionPaused.value = false;
  sessionStartedAt = Date.now();
  sessionPausedMs = 0;
  sessionElapsed.value = 0;
  sessionTimer = window.setInterval(() => {
    const paused = sessionPaused.value ? Date.now() - sessionPausedAt : 0;
    sessionElapsed.value = Math.max(0, Math.floor((Date.now() - sessionStartedAt - sessionPausedMs - paused) / 1000));
    if (sessionElapsed.value >= (draft.durationMinutes || 20) * 60 && !finishing.value) {
      ElMessage.info('面试时间到了，正在结束练习。');
      void finishPractice();
    }
  }, 500);
  void loadNarration();
}

/** 题目朗读结束后自动开始录制；只在连续模式且未暂停时执行。 */
function scheduleAutoRecord() {
  if (!continuous.value || !sessionStarted.value || sessionPaused.value) return;
  window.setTimeout(() => {
    if (continuous.value && sessionStarted.value && !sessionPaused.value
        && recorderState.value === 'idle' && !answeredQuestions.value.has(currentIndex.value)) {
      startRecording();
    }
  }, 500);
}

/** 连续面试只允许暂停一次；第一次暂停会同步到后端计数。 */
async function toggleSessionPause() {
  if (!continuous.value || !sessionStarted.value) return;
  if (!sessionPaused.value) {
    if (pauseUsed.value) {
      ElMessage.warning('连续面试只能暂停一次');
      return;
    }
    if (recorderState.value === 'recording' && recorder?.state === 'recording') {
      recorder.pause();
      pausedAt = Date.now();
      recorderState.value = 'paused';
    }
    sessionPaused.value = true;
    sessionPausedAt = Date.now();
    stopNarration();
    try {
      const remote = await tryPauseSession(draft.id);
      draft.pausedCount = remote.pausedCount;
      practice.saveDraft();
    } catch (error) {
      ElMessage.error(error?.message || '暂停失败');
    }
    return;
  }

  sessionPaused.value = false;
  if (sessionPausedAt) {
    sessionPausedMs += Date.now() - sessionPausedAt;
    sessionPausedAt = 0;
  }
  if (recorderState.value === 'paused' && recorder?.state === 'paused') {
    recorder.resume();
    pausedMilliseconds += Date.now() - pausedAt;
    pausedAt = undefined;
    recorderState.value = 'recording';
  } else {
    scheduleAutoRecord();
  }
}

function stopRecording() {
  if (recorderState.value !== 'recording' && recorderState.value !== 'paused') return Promise.resolve();
  window.clearInterval(elapsedTimer);
  recorderState.value = 'saving';
  stopPromise = new Promise((resolve) => {
    const original = persistAnswer;
    recorder.onstop = async (event) => { await original(event); resolve(); };
  });
  recorder.stop();
  return stopPromise;
}

async function persistAnswer() {
  const pauseDuration = pausedAt ? Date.now() - pausedAt : 0;
  const durationSeconds = Math.max(0, Math.round((Date.now() - startedAt - pausedMilliseconds - pauseDuration) / 1000));
  const blob = new Blob(chunks, { type: recorder?.mimeType || 'video/webm' });
  if (blob.size === 0) {
    recorderState.value = 'idle';
    ElMessage.warning('这段录制没有内容，请重新回答。');
    return;
  }
  const mediaId = `${draft.id}-${currentQuestion.value.id}`;
  try {
    await saveMedia(mediaId, blob);
  } catch {
    recorderState.value = 'error';
    ElMessage.error('浏览器本地存储不可用，请检查可用空间后重试。');
    return;
  }
  const answer = { questionIndex: currentIndex.value, questionId: currentQuestion.value.id, localMediaId: mediaId, durationSeconds, startedAtOffset: draft.answers.reduce((total, item) => total + item.durationSeconds, 0), savedAt: new Date().toISOString(), bytes: blob.size, mimeType: blob.type, transcriptStatus: 'awaiting_provider', evaluationStatus: 'awaiting_provider' };
  try {
    await syncAnswer(answer, blob);
  } catch (error) {
    answer.syncError = error.message || '视频同步失败';
    if (isPermanentUploadRejection(error)) {
      practice.setAnswer(answer);
      recorderState.value = 'error';
      ElMessage.error(uploadRejectionMessage(error));
      return;
    }
    ElMessage.error('网络暂时不可用，视频已暂存在本机。结束练习时会自动重试同步。');
  }
  practice.setAnswer(answer);
  recorderState.value = 'saved';
  savedAnswer.value = true;
  if (continuous.value && !sessionPaused.value && !finishing.value) {
    window.setTimeout(() => {
      if (!sessionPaused.value && !finishing.value) void finishQuestion();
    }, 600);
  }
}

async function syncAnswer(answer, blob) {
  const saved = await trySaveAnswer(draft.id, answer);
  // 大文件走分片上传：断点续传 + sha256 完整性校验；小文件仍是单段上传。
  await uploadAnswerMediaWithFallback(saved.id, blob, {
    onProgress: (ratio) => {
      answer.uploadProgress = Math.round(ratio * 100);
    },
  });
  try {
    // 单题上传完就开始分析，不必等整场练习结束；失败也没关系，结束时还会统一入队。
    await tryCompleteAnswer(saved.id);
  } catch {
    // 忽略：整场结束时 complete 会兜底入队。
  }
  answer.mediaId = saved.id;
  answer.uploadProgress = 100;
  answer.syncError = null;
}

async function syncPendingAnswers() {
  const pending = draft.answers.filter((answer) => !answer.mediaId || answer.syncError);
  for (const answer of pending) {
    const blob = await getMedia(answer.localMediaId);
    if (!blob) throw new Error(`第 ${answer.questionIndex + 1} 题的本地录像已不可用，请重新录制。`);
    await syncAnswer(answer, blob);
    practice.setAnswer(answer);
  }
}

async function finishQuestion() {
  if (recorderState.value === 'recording' || recorderState.value === 'paused') await stopRecording();
  if (recorderState.value !== 'saved' && !answeredQuestions.value.has(currentIndex.value)) {
    ElMessage.warning('请先录制本题回答，或选择结束本次练习。');
    return;
  }
  // 面试官追问：答完当场判断，问出来就作为下一题插进来
  const followUp = await maybeFollowUp();
  if (followUp) return;
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value += 1;
    elapsed.value = 0;
    recorderState.value = 'idle';
    savedAnswer.value = false;
    return;
  }
  await finishPractice(continuous.value);
}

/**
 * 追问：把这一题的回答交给后端，由面试官决定要不要接着问一句。
 *
 * 返回 true 表示"插入了追问并已经跳过去"。任何失败都只是少问一句，
 * 绝不能挡住练习 —— 所以这里吞掉异常、由后端给出可读的 reason 文案。
 */
async function maybeFollowUp() {
  if (!draft.followUpEnabled || !draft.id) return false;
  const answer = draft.answers.find((item) => item.questionIndex === currentIndex.value);
  if (!answer?.mediaId) return false;
  followUpPending.value = true;
  try {
    const result = await requestFollowUp(draft.id, answer.mediaId);
    if (!result?.question) {
      if (result?.reason) followUpNotice.value = result.reason;
      return false;
    }
    if (!draft.questions) draft.questions = [];
    draft.questions.splice(currentIndex.value + 1, 0, result.question);
    practice.saveDraft();
    currentIndex.value += 1;
    elapsed.value = 0;
    recorderState.value = 'idle';
    savedAnswer.value = false;
    ElMessage.success(result.round > 1
      ? `面试官又追问了一句（第 ${result.round} 次），请继续回答。`
      : '面试官追问了一句，请继续回答。');
    return true;
  } catch (error) {
    followUpNotice.value = error.message || '这次追问没成功，继续下一题即可';
    return false;
  } finally {
    followUpPending.value = false;
  }
}

async function retake() {
  if (recorderState.value === 'recording' || recorderState.value === 'paused') await stopRecording();
  const old = draft.answers.find((answer) => answer.questionIndex === currentIndex.value);
  if (old) {
    const { deleteMedia } = await import('../services/mediaStore');
    await deleteMedia([old.localMediaId || old.mediaId]);
    draft.answers = draft.answers.filter((answer) => answer.questionIndex !== currentIndex.value);
    practice.saveDraft();
  }
  elapsed.value = 0;
  recorderState.value = 'idle';
  savedAnswer.value = false;
}

async function finishPractice(auto = false) {
  if (finishing.value) return;
  if (continuous.value && !sessionStarted.value) {
    ElMessage.warning('请先开始连续面试。');
    return;
  }
  const done = draft.answers.length;
  if (!done) {
    ElMessage.warning('至少录制一道回答后才能结束练习。');
    return;
  }
  if (auto !== true) {
    try {
      await ElMessageBox.confirm(`已录制 ${done} 道回答。结束后可以查看每题回放，并在设置分析服务后生成转写与评分。`, '结束本次练习？', { confirmButtonText: '结束练习', cancelButtonText: '继续练习', type: 'warning' });
    } catch { return; }
  }
  finishing.value = true;
  await stopRecording();
  try {
    await syncPendingAnswers();
    await tryCompleteSession(draft.id);
  } catch (error) {
    finishing.value = false;
    ElMessage.error(error.message || '无法完成练习，请检查连接后重试。');
    return;
  }
  window.clearInterval(sessionTimer);
  practice.finishDraft();
  stopActiveStream();
  router.replace(`/practice/${route.params.id}/report`);
}

function selectQuestion(index) {
  if (continuous.value) {
    ElMessage.info('连续面试会按顺序自动进入下一题');
    return;
  }
  if (recorderState.value === 'recording' || recorderState.value === 'paused' || recorderState.value === 'saving') {
    ElMessage.warning('请先保存或结束当前录制。');
    return;
  }
  currentIndex.value = index;
  elapsed.value = 0;
  savedAnswer.value = answeredQuestions.value.has(index);
  recorderState.value = answeredQuestions.value.has(index) ? 'saved' : 'idle';
}

onBeforeUnmount(() => {
  stopNarration();
  releaseNarrationAudio();
  avatarAudio.dispose();
  resetAvatarState();
  window.clearInterval(elapsedTimer);
  window.clearInterval(sessionTimer);
  if (recorder && (recorder.state === 'recording' || recorder.state === 'paused')) recorder.stop();
  stopActiveStream();
});
</script>

<template>
  <section v-if="draft" class="session-page">
    <div class="session-top"><button class="back-button" @click="router.push('/history')"><el-icon><ArrowLeft /></el-icon>退出练习</button><div class="session-meta"><span>{{ draft.role }}</span><i></i><span>{{ continuous ? '连续面试' : '逐题练习' }}</span><i></i><span>{{ styleLabels[draft.interviewerStyle] }}</span><i></i><span v-if="continuous && sessionStarted">总计时 {{ formattedSessionTime }} / {{ draft.durationMinutes }}:00</span><span v-else>预计 {{ draft.durationMinutes }} 分钟</span></div><div class="session-top-actions"><el-button v-if="continuous && sessionStarted" class="soft-button" :disabled="pauseUsed && !sessionPaused" @click="toggleSessionPause">{{ sessionPaused ? '继续面试' : '暂停面试' }}</el-button><el-button class="end-button" @click="finishPractice">结束练习</el-button></div></div>
    <div class="session-layout">
      <aside class="question-rail panel"><div class="rail-header"><span>本轮问题</span><strong>{{ answeredQuestions.size }} / {{ questions.length }}</strong></div><div class="overall-progress"><span :style="{ width: `${progress}%` }"></span></div><button v-for="(question, index) in questions" :key="question.id" class="question-nav" :class="{ active: index === currentIndex, answered: answeredQuestions.has(index) }" :disabled="continuous" @click="selectQuestion(index)"><span class="question-index">{{ answeredQuestions.has(index) ? '✓' : String(index + 1).padStart(2, '0') }}</span><span><small>{{ question.category }}</small><strong>{{ question.text }}</strong></span><el-icon v-if="index === currentIndex"><ArrowRight /></el-icon></button><div class="rail-note"><el-icon><Lock /></el-icon>{{ continuous ? '按顺序自动进行，不能跳到其他题' : '每道回答独立录制与保存' }}</div></aside>

      <div class="session-main">
        <div class="interviewer-panel panel"><div class="interviewer-screen"><DigitalHuman :audio-url="narrationBlobUrl" :speaking="speaking" :amplitude="avatarAudio.amplitude" /><div class="interviewer-info"><div class="interviewer-role">AI 面试官 · {{ styleLabels[draft.interviewerStyle] }}</div><div class="interviewer-status"><span class="voice-bars" :class="{ active: speaking }"><i></i><i></i><i></i><i></i></span>{{ narrationCaption }}</div><div class="interviewer-actions"><button v-if="narration?.audioAvailable && !narrationAudioFailed" class="narration-replay" :aria-label="speaking ? '暂停朗读' : '重播问题'" :title="speaking ? '暂停朗读' : '重播问题'" @click="replayNarration"><el-icon><VideoPause v-if="speaking" /><VideoPlay v-else /></el-icon>{{ speaking ? '暂停朗读' : '重播问题' }}</button><span class="interviewer-tip">配音：按音量驱动嘴型，非音素级口型同步</span></div><p v-if="avatarError" class="interviewer-error">{{ avatarError }}</p></div></div><div class="question-content"><div class="question-kicker" :class="{ 'is-follow-up': followUpRound > 0 }">QUESTION {{ String(currentIndex + 1).padStart(2,'0') }} <span>{{ questionKickerLabel }}</span></div><h1>{{ currentQuestion?.text }}</h1><p v-if="followUpNotice" class="follow-up-notice"><el-icon><ChatDotRound /></el-icon>{{ followUpNotice }}</p><p>建议使用具体事例展开：背景与目标、采取的行动、最终结果，以及你的复盘。</p></div></div>

        <div class="recording-panel panel">
          <div class="self-preview">
            <video ref="video" autoplay muted playsinline></video>
            <div v-if="!stream?.active" class="self-placeholder"><el-icon><UserFilled /></el-icon><small>设备连接中</small></div>
            <div class="recording-indicator" :class="recorderState"><i></i>{{ recorderState === 'recording' ? '录制中' : recorderState === 'paused' ? '已暂停' : recorderState === 'saving' ? '保存中' : '摄像头已连接' }}</div>
          </div>
          <div class="record-controls">
            <div class="record-time">
              <span>{{ formattedTime }}</span>
              <small>{{ continuous
                ? (sessionPaused ? '面试已暂停' : recorderState === 'recording' ? '正在自动录制本题' : recorderState === 'saving' ? '正在保存本题' : '面试官读完后自动开始录制')
                : (recorderState === 'recording' ? '正在录制本题回答' : recorderState === 'paused' ? '录制已暂停' : recorderState === 'saved' ? '本题已保存，可重新录制' : '准备好后开始录制') }}</small>
            </div>
            <div class="record-buttons">
              <template v-if="!continuous">
                <el-button v-if="recorderState === 'idle' || recorderState === 'error' || recorderState === 'saved'" class="record-button" @click="startRecording"><span class="record-dot"></span>{{ recorderState === 'saved' ? '重新录制' : '开始回答' }}</el-button>
                <template v-else><el-button class="pause-button" :disabled="recorderState === 'saving'" @click="pauseRecording"><el-icon><VideoPause v-if="recorderState === 'recording'" /><VideoPlay v-else /></el-icon>{{ recorderState === 'recording' ? '暂停' : recorderState === 'paused' ? '继续' : '保存中' }}</el-button><el-button class="save-button" :disabled="recorderState === 'saving'" @click="stopRecording"><el-icon><CircleCheck /></el-icon>结束回答</el-button></template>
              </template>
              <template v-else>
                <span class="continuous-record-hint">{{ recorderState === 'recording' ? '正在自动录制' : sessionPaused ? '已暂停' : '自动进行中' }}</span>
                <el-button v-if="recorderState === 'error'" class="record-button" @click="startRecording">重试录制</el-button>
                <el-button v-if="recorderState === 'recording' || recorderState === 'paused'" class="save-button" :disabled="recorderState === 'saving'" @click="stopRecording">结束本题</el-button>
              </template>
            </div>
          </div>
        </div>
        <div class="session-bottom"><span><el-icon><Lock /></el-icon>视频保留 7 天 · 可随时删除</span><span v-if="continuous" class="auto-next-hint"><el-icon><Right /></el-icon>答完自动进入下一题，不需要手动保存</span><el-button v-else class="primary-button" :disabled="recorderState === 'recording' || recorderState === 'paused' || recorderState === 'saving'" @click="finishQuestion">{{ currentIndex === questions.length - 1 ? '完成并查看报告' : '保存并继续' }} <el-icon><ArrowRight /></el-icon></el-button></div>
      </div>
    </div>
    <div v-if="continuous && !sessionStarted" class="continuous-intro-backdrop">
      <div class="continuous-intro panel">
        <div class="eyebrow">CONTINUOUS INTERVIEW</div>
        <h2>连续面试</h2>
        <p>开始后按顺序自动出题、自动录制、自动进入下一题；不能回看重录，整场只允许暂停一次。</p>
        <ul>
          <li>{{ questions.length }} 道题 · 约 {{ draft.durationMinutes }} 分钟</li>
          <li>单题最长 3 分钟，答完或超时自动进入下一题</li>
          <li>结束后统一转写、评分与复盘</li>
        </ul>
        <el-button class="primary-button" @click="startContinuous">
          <el-icon><VideoPlay /></el-icon> 开始面试
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.session-page { min-height: calc(100vh - 158px); }.session-top { display: flex; align-items: center; justify-content: space-between; gap: 13px; margin: -8px 0 18px; }.session-top .back-button { margin-right: auto; }.session-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; color: #77837c; font-size: 13px; }.session-meta i { width: 3px; height: 3px; border-radius: 50%; background: #b7c0ba; }.end-button.el-button { height: 32px; border-color: #e8d4cc; color: #aa5e49; font-size: 13px; }.session-layout { display: grid; grid-template-columns: 250px minmax(0,1fr); align-items: start; gap: 15px; }.question-rail { position: sticky; top: 82px; padding: 14px 0 8px; box-shadow: none; }.rail-header { display: flex; justify-content: space-between; padding: 0 14px; color: #59655e; font-size: 13px; }.rail-header strong { color: var(--green); font-size: 13px; }.overall-progress { height: 3px; margin: 12px 14px 10px; border-radius: 3px; background: #edf0ed; }.overall-progress span { display: block; height: 100%; border-radius: 3px; background: var(--green); transition: width .25s ease; }.question-nav { display: flex; width: 100%; align-items: center; gap: 9px; padding: 11px 12px; border: 0; border-left: 2px solid transparent; background: white; text-align: left; }.question-nav:hover { background: #fafcfb; }.question-nav.active { border-left-color: var(--green); background: #f1f7f3; }.question-index { display: grid; width: 24px; height: 24px; flex: none; place-items: center; border: 1px solid #e4e9e5; border-radius: 50%; color: #98a39d; font-size: 13px; }.question-nav.answered .question-index { border-color: #d5eadb; background: var(--green-soft); color: var(--green); }.question-nav > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 5px; }.question-nav small { color: #98a19c; font-size: 12px; }.question-nav strong { display: -webkit-box; overflow: hidden; color: #616d66; font-size: 13px; font-weight: 500; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.question-nav.active strong { color: #35433b; font-weight: 600; }.question-nav > .el-icon { flex: none; color: var(--green); }.rail-note { display: flex; gap: 6px; margin: 13px 13px 0; padding-top: 10px; border-top: 1px solid #edf0ed; color: #849189; font-size: 12px; }
.session-main { display: grid; gap: 12px; }.interviewer-panel { overflow: hidden; box-shadow: none; }
.question-content { padding: 17px 19px 18px; }.question-kicker { color: var(--green); font-size: 13px; font-weight: 700; }.question-kicker span { margin-left: 10px; padding-left: 10px; border-left: 1px solid var(--line); color: #909a95; font-weight: 500; }.question-content h1 { max-width: 730px; margin: 10px 0 8px; color: #2f3c35; font-size: 18px; font-weight: 650; line-height: 1.65; }.question-content p { margin: 0; color: #909b95; font-size: 13px; line-height: 1.6; }
.question-kicker.is-follow-up { color: #c2691f; }.question-kicker.is-follow-up span { color: #a9722f; font-weight: 600; }
.question-content p.follow-up-notice { display: flex; align-items: center; gap: 6px; margin: 0 0 8px; color: #a9722f; font-size: 13px; }
.recording-panel { display: flex; min-height: 109px; gap: 14px; align-items: center; padding: 12px 14px; box-shadow: none; }.self-preview { position: relative; width: 126px; height: 80px; flex: none; overflow: hidden; border-radius: 5px; background: #26352e; }.self-preview video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }.self-placeholder { display: grid; position: absolute; inset: 0; place-content: center; justify-items: center; gap: 4px; color: #dbe8df; font-size: 18px; }.self-placeholder small { font-size: 12px; }.recording-indicator { position: absolute; bottom: 5px; left: 5px; display: flex; align-items: center; gap: 4px; padding: 3px 5px; border-radius: 3px; background: rgba(18,29,23,.75); color: white; font-size: 11px; }.recording-indicator i { width: 5px; height: 5px; border-radius: 50%; background: #7bbc8e; }.recording-indicator.recording i { background: #e46758; animation: blink .8s infinite alternate; }.recording-indicator.paused i { background: #e4b451; }.recording-indicator.saving i { background: #7ba0b2; }@keyframes blink { to { opacity: .25; } }
.record-controls { display: flex; min-width: 0; flex: 1; align-items: center; justify-content: space-between; gap: 10px; }.record-time { display: grid; gap: 4px; }.record-time > span { color: #38453e; font-size: 20px; font-variant-numeric: tabular-nums; font-weight: 650; }.record-time small { color: #97a19b; font-size: 12px; }.record-buttons { display: flex; flex-wrap: wrap; gap: 7px; }.record-button.el-button { height: 34px; border-color: #b44b3c; background: #bd5545; color: white; font-size: 13px; }.record-button.el-button:hover { border-color: #a44233; background: #a44233; }.record-dot { width: 7px; height: 7px; border-radius: 50%; background: white; }.pause-button.el-button,.save-button.el-button { height: 32px; padding: 0 10px; font-size: 13px; }.pause-button.el-button { border-color: #e3e8e4; color: #65726a; }.save-button.el-button { border-color: #cfe5d4; background: #eef7f0; color: #4b865e; }
.session-bottom { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.session-bottom > span { display: inline-flex; align-items: center; gap: 5px; color: #8a958e; font-size: 12px; }.session-bottom .primary-button { height: 36px; }
.session-top-actions { display: flex; align-items: center; gap: 8px; }
.continuous-record-hint { display: inline-flex; align-items: center; color: #7d8982; font-size: 12px; }
.auto-next-hint { color: #5d8b6c !important; }
.continuous-intro-backdrop { position: fixed; z-index: 60; inset: 0; display: grid; place-items: center; padding: 20px; background: rgba(22, 31, 27, .42); }
.continuous-intro { display: grid; width: min(460px, 100%); gap: 11px; padding: 25px 26px 23px; box-shadow: 0 20px 60px rgba(20, 35, 27, .22); }
.continuous-intro h2 { margin: 0; color: #2f3c35; font-size: 24px; }
.continuous-intro p { margin: 0; color: #6d7972; font-size: 13px; line-height: 1.8; }
.continuous-intro ul { display: grid; gap: 5px; margin: 0; padding-left: 17px; color: #5d6a64; font-size: 13px; line-height: 1.7; }
.continuous-intro .el-button { justify-self: start; margin-top: 5px; }
.narration-replay { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: #4f7a61; font-size: 12px; cursor: pointer; }
.narration-replay:hover { color: #35624a; }
@media(max-width: 920px) { .session-layout { grid-template-columns: 205px minmax(0,1fr); gap: 10px; }.question-nav { gap: 6px; padding: 10px 8px; }.session-meta { justify-content: center; } }
@media(max-width: 700px) { .session-top { flex-wrap: wrap; }.session-top .back-button { order: 0; }.session-meta { order: 2; width: 100%; justify-content: flex-start; }.end-button { order: 1; }.session-layout { grid-template-columns: 1fr; }.question-rail { position: static; padding-bottom: 5px; }.rail-header { padding: 0 12px; }.question-nav { display: none; }.question-nav.active { display: flex; }.rail-note { display: none; }.overall-progress { margin-bottom: 4px; }.recording-panel { align-items: stretch; flex-direction: column; }.self-preview { width: 100%; height: auto; aspect-ratio: 16/9; }.record-controls { align-items: flex-start; }.session-bottom > span { max-width: 45%; line-height: 1.5; } }
/* 面试官舞台：左侧真人数字人，右侧身份与朗读状态。
   素材是照片切帧（web/public/assets/avatar），组件是 components/DigitalHuman.vue。 */
.interviewer-screen { display: grid; height: auto; grid-template-columns: minmax(150px, 210px) minmax(0, 1fr); align-items: center; gap: 20px; padding: 16px 20px; overflow: visible; border-radius: 8px 8px 0 0; background: linear-gradient(100deg,#e7f1eb 0%,#eef5f1 48%,#e6f0ea 100%); }
/* 与素材画布 340x260 一致，避免 object-fit 留出额外空白 */
.interviewer-screen :deep(.dh-stage) { aspect-ratio: 340 / 260; }
.interviewer-info { display: grid; min-width: 0; gap: 9px; }
.interviewer-role { color: #5d7a68; font-size: 14px; font-weight: 600; }
.interviewer-status { display: flex; align-items: center; gap: 8px; color: #33443b; font-size: 15px; font-weight: 600; }
.interviewer-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.interviewer-tip { color: #8a978f; font-size: 13px; }
.interviewer-error { margin: 0; color: #a4483a; font-size: 14px; }
.narration-replay { font-size: 14px; }
.voice-bars { display: inline-flex; height: 13px; flex: none; align-items: flex-end; gap: 2px; }
.voice-bars i { width: 3px; height: 4px; border-radius: 2px; background: #a9c4b3; }
.voice-bars.active i { animation: voice-bounce .9s ease-in-out infinite; }
.voice-bars.active i:nth-child(2) { animation-delay: .12s; }
.voice-bars.active i:nth-child(3) { animation-delay: .24s; }
.voice-bars.active i:nth-child(4) { animation-delay: .36s; }
@keyframes voice-bounce { 0%,100% { height: 4px; } 50% { height: 13px; } }
@media(max-width: 700px) { .interviewer-screen { grid-template-columns: 128px minmax(0,1fr); gap: 12px; padding: 12px 14px; }.interviewer-status { font-size: 14px; } }
@media (prefers-reduced-motion: reduce) { .voice-bars.active i { animation: none; } }
</style>
