<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getActiveStream, stopActiveStream, supportedRecorderType } from '../services/mediaCapture';
import { getMedia, saveMedia } from '../services/mediaStore';
import { buildQuestions, styleLabels } from '../data/questions';
import { getNarrationAudio, getQuestionNarration, tryCompleteAnswer, tryCompleteSession, trySaveAnswer } from '../services/api';
import { uploadAnswerMediaWithFallback } from '../services/chunkedUpload';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const recorderState = ref('idle');
const currentIndex = ref(0);
const elapsed = ref(0);
const savedAnswer = ref(false);
const audio = ref();
const narrationBlobUrl = ref('');
const narration = ref(null);
const narrationLoading = ref(false);
const avatarState = ref('asking');
const speaking = ref(false);
const mouthLevel = ref(0);
const amplitudeActive = ref(false);
const narrationAudioFailed = ref(false);
const stream = getActiveStream();
const draft = practice.draft?.id === route.params.id ? practice.draft : practice.find(route.params.id);
const questions = computed(() => draft ? (draft.questions || buildQuestions(draft.role, draft.questionCount, draft.interviewType)) : []);
const currentQuestion = computed(() => questions.value[currentIndex.value]);
const answeredQuestions = computed(() => new Set(draft?.answers.map((answer) => answer.questionIndex) || []));
const progress = computed(() => draft ? Math.round((answeredQuestions.value.size / questions.value.length) * 100) : 0);
const formattedTime = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`);
// 数字人字幕只显示「朗读状态」。问题正文统一放在下方的问题卡片里：
// 之前这里绑定的是整段问题文本，长句会溢出画面、压在小人身上被裁掉，
// 用户看到的就是同一句问题出现两遍、而且上面那遍还看不全。
const narrationCaption = computed(() => {
  if (narrationLoading.value) return '正在准备这道题…';
  if (speaking.value) return '正在朗读这道题…';
  if (narrationAudioFailed.value) return '语音暂不可用，请阅读下方问题';
  if (narration.value?.audioAvailable) return '可点击重播再听一遍';
  return '请阅读下方问题';
});
const mouthStyle = computed(() => (amplitudeActive.value
  ? { transform: `scaleY(${(1 + mouthLevel.value * 2.4).toFixed(2)})` }
  : null));
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
  await loadNarration();
});

watch(currentIndex, () => { loadNarration(); });

async function loadNarration() {
  stopNarration();
  releaseNarrationAudio();
  narration.value = null;
  narrationAudioFailed.value = false;
  if (!draft?.id) return;
  narrationLoading.value = true;
  avatarState.value = 'thinking';
  try {
    const result = await getQuestionNarration(draft.id, currentIndex.value);
    narration.value = result;
    avatarState.value = result.state || 'asking';
    if (result.audioAvailable && result.audioUrl && await prepareNarrationAudio()) {
      await playNarration();
    }
  } catch {
    narration.value = null;
    avatarState.value = 'asking';
  } finally {
    narrationLoading.value = false;
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
    if (audio.value) audio.value.src = narrationBlobUrl.value;
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

async function playNarration() {
  if (!audio.value || !narrationBlobUrl.value) return;
  try {
    audio.value.currentTime = 0;
    await audio.value.play();
  } catch {
    // Autoplay is often blocked until the user interacts; the replay control covers that.
    speaking.value = false;
  }
}

async function replayNarration() {
  if (!narrationBlobUrl.value || !audio.value) return;
  if (speaking.value) { stopNarration(); return; }
  try {
    audio.value.currentTime = 0;
    // 用户手势里才接分析器：AudioContext 在这里一定允许 resume，
    // 避免出现「接上了分析器却因为挂起而没声音」的情况。
    await resumeAudioContext();
    startAmplitude();
    await audio.value.play();
  } catch {
    ElMessage.warning('无法播放面试官语音，请检查浏览器音频权限。');
  }
}

function stopNarration() {
  if (audio.value) audio.value.pause();
  stopAmplitude();
  speaking.value = false;
}

function onNarrationPause() {
  speaking.value = false;
  stopAmplitude();
}

// 合成服务不可用（例如未配置 TTS Key）时隐藏播放控件，只保留数字人字幕。
function onNarrationError() {
  narrationAudioFailed.value = true;
  speaking.value = false;
  stopAmplitude();
}

let audioContext;
let analyser;
let amplitudeFrame;

async function resumeAudioContext() {
  if (audioContext && audioContext.state === 'suspended') await audioContext.resume();
}

function startAmplitude() {
  if (!analyser && !attachAnalyser()) return;
  const samples = new Uint8Array(analyser.frequencyBinCount);
  const tick = () => {
    analyser.getByteTimeDomainData(samples);
    let energy = 0;
    for (const sample of samples) {
      const centered = (sample - 128) / 128;
      energy += centered * centered;
    }
    mouthLevel.value = Math.min(1, Math.sqrt(energy / samples.length) * 4);
    amplitudeFrame = requestAnimationFrame(tick);
  };
  cancelAnimationFrame(amplitudeFrame);
  tick();
}

function attachAnalyser() {
  try {
    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    if (!AudioCtx || !audio.value) return false;
    audioContext = new AudioCtx();
    const source = audioContext.createMediaElementSource(audio.value);
    analyser = audioContext.createAnalyser();
    analyser.fftSize = 256;
    analyser.smoothingTimeConstant = 0.6;
    source.connect(analyser);
    analyser.connect(audioContext.destination);
    amplitudeActive.value = true;
    return true;
  } catch {
    // 拿不到分析器就退回状态驱动的口型动画，保证声音不受影响。
    audioContext = undefined;
    analyser = undefined;
    amplitudeActive.value = false;
    return false;
  }
}

function stopAmplitude() {
  if (amplitudeFrame) cancelAnimationFrame(amplitudeFrame);
  amplitudeFrame = undefined;
  mouthLevel.value = 0;
}

function startRecording() {
  if (!stream?.active || recorderState.value === 'recording') return;
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
    avatarState.value = 'listening';
    elapsedTimer = window.setInterval(() => { elapsed.value = Math.floor((Date.now() - startedAt - pausedMilliseconds) / 1000); }, 250);
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
    ElMessage.error('视频已暂存在本机。结束练习时会自动重试同步。');
  }
  practice.setAnswer(answer);
  recorderState.value = 'saved';
  savedAnswer.value = true;
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
  if (currentIndex.value < questions.value.length - 1) {
    currentIndex.value += 1;
    elapsed.value = 0;
    recorderState.value = 'idle';
    savedAnswer.value = false;
    return;
  }
  await finishPractice();
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

async function finishPractice() {
  const done = draft.answers.length;
  if (!done) {
    ElMessage.warning('至少录制一道回答后才能结束练习。');
    return;
  }
  try {
    await ElMessageBox.confirm(`已录制 ${done} 道回答。结束后可以查看每题回放，并在设置分析服务后生成转写与评分。`, '结束本次练习？', { confirmButtonText: '结束练习', cancelButtonText: '继续练习', type: 'warning' });
  } catch { return; }
  await stopRecording();
  try {
    await syncPendingAnswers();
    await tryCompleteSession(draft.id);
  } catch (error) {
    ElMessage.error(error.message || '无法完成练习，请检查连接后重试。');
    return;
  }
  practice.finishDraft();
  stopActiveStream();
  router.replace(`/practice/${route.params.id}/report`);
}

function selectQuestion(index) {
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
  if (audioContext) audioContext.close();
  window.clearInterval(elapsedTimer);
  if (recorder && (recorder.state === 'recording' || recorder.state === 'paused')) recorder.stop();
  stopActiveStream();
});
</script>

<template>
  <section v-if="draft" class="session-page">
    <div class="session-top"><button class="back-button" @click="router.push('/history')"><el-icon><ArrowLeft /></el-icon>退出练习</button><div class="session-meta"><span>{{ draft.role }}</span><i></i><span>{{ styleLabels[draft.interviewerStyle] }}</span><i></i><span>预计 {{ draft.durationMinutes }} 分钟</span></div><el-button class="end-button" @click="finishPractice">结束练习</el-button></div>
    <div class="session-layout">
      <aside class="question-rail panel"><div class="rail-header"><span>本轮问题</span><strong>{{ answeredQuestions.size }} / {{ questions.length }}</strong></div><div class="overall-progress"><span :style="{ width: `${progress}%` }"></span></div><button v-for="(question, index) in questions" :key="question.id" class="question-nav" :class="{ active: index === currentIndex, answered: answeredQuestions.has(index) }" @click="selectQuestion(index)"><span class="question-index">{{ answeredQuestions.has(index) ? '✓' : String(index + 1).padStart(2, '0') }}</span><span><small>{{ question.category }}</small><strong>{{ question.text }}</strong></span><el-icon v-if="index === currentIndex"><ArrowRight /></el-icon></button><div class="rail-note"><el-icon><Lock /></el-icon>每道回答独立录制与保存</div></aside>

      <div class="session-main">
        <div class="interviewer-panel panel"><div class="interviewer-screen"><div class="interviewer-scene" :class="[avatarState, { speaking }]"><div class="portrait-frame"><svg class="portrait-svg" viewBox="0 0 200 250" aria-hidden="true"><path d="M14 250 C16 196 42 166 76 156 L100 168 L124 156 C158 166 184 196 186 250 Z" fill="#2f4f45"/><path d="M72 158 L100 206 L128 158 C118 152 82 152 72 158 Z" fill="#f7f8f5"/><path d="M72 158 L100 206 L74 250 L46 250 C48 206 56 176 72 158 Z" fill="#274239"/><path d="M128 158 L100 206 L126 250 L154 250 C152 206 144 176 128 158 Z" fill="#274239"/><path d="M94 200 h12 l-3 8 -3 34 -3-34 z" fill="#8ba7b5"/><rect x="126" y="200" width="30" height="22" rx="3" fill="#f4f6f2"/><rect x="126" y="200" width="30" height="5" rx="2.5" fill="#3f7f63"/><rect x="131" y="210" width="20" height="2.6" rx="1.3" fill="#c9d4cd"/><rect x="131" y="215" width="14" height="2.6" rx="1.3" fill="#dde5e0"/><path d="M86 128 h28 v30 c0 7 -6 11 -14 11 s-14 -4 -14 -11 z" fill="#d9a184"/><ellipse cx="100" cy="96" rx="40" ry="46" fill="#efc7aa"/><ellipse cx="61" cy="102" rx="7" ry="9" fill="#e3b697"/><ellipse cx="139" cy="102" rx="7" ry="9" fill="#e3b697"/><path d="M59 98 C55 58 76 44 100 44 C124 44 145 58 141 98 C141 84 134 74 120 70 C110 67 90 67 80 70 C66 74 59 84 59 98 Z" fill="#3c322c"/><rect x="76" y="80" width="18" height="3.4" rx="1.7" fill="#4a3c34"/><rect x="106" y="80" width="18" height="3.4" rx="1.7" fill="#4a3c34"/><ellipse cx="85" cy="94" rx="4.6" ry="3.4" fill="#3a322e"/><ellipse cx="115" cy="94" rx="4.6" ry="3.4" fill="#3a322e"/><circle cx="86.4" cy="92.8" r="1.1" fill="#ffffff"/><circle cx="116.4" cy="92.8" r="1.1" fill="#ffffff"/><g fill="none" stroke="#6f7f78" stroke-width="1.6" stroke-linecap="round"><rect x="72" y="85" width="27" height="19" rx="7"/><rect x="101" y="85" width="27" height="19" rx="7"/><path d="M99 93 h2"/><path d="M72 91 l-11 -2"/><path d="M128 91 l11 -2"/></g><path d="M100 96 v12" stroke="#c98e73" stroke-width="1.6" fill="none" stroke-linecap="round"/><ellipse class="portrait-mouth" cx="100" cy="121" rx="8.5" ry="3.2" fill="#b4685c"/></svg></div><div class="interviewer-info"><div class="interviewer-role">AI 面试官 · {{ styleLabels[draft.interviewerStyle] }}</div><div class="interviewer-status"><span class="voice-bars" :class="{ active: speaking }"><i></i><i></i><i></i><i></i></span>{{ narrationCaption }}</div><div class="interviewer-actions"><button v-if="narration?.audioAvailable && !narrationAudioFailed" class="narration-replay" :aria-label="speaking ? '暂停朗读' : '重播问题'" :title="speaking ? '暂停朗读' : '重播问题'" @click="replayNarration"><el-icon><VideoPause v-if="speaking" /><VideoPlay v-else /></el-icon>{{ speaking ? '暂停朗读' : '重播问题' }}</button><span class="interviewer-tip">问题原文见下方</span></div></div><audio ref="audio" class="narration-audio" @play="speaking = true" @pause="onNarrationPause" @ended="onNarrationPause" @error="onNarrationError"></audio></div></div><div class="question-content"><div class="question-kicker">QUESTION {{ String(currentIndex + 1).padStart(2,'0') }} <span>{{ currentQuestion?.category }}</span></div><h1>{{ currentQuestion?.text }}</h1><p>建议使用具体事例展开：背景与目标、采取的行动、最终结果，以及你的复盘。</p></div></div>

        <div class="recording-panel panel"><div class="self-preview"><video ref="video" autoplay muted playsinline></video><div v-if="!stream?.active" class="self-placeholder"><el-icon><UserFilled /></el-icon><small>设备连接中</small></div><div class="recording-indicator" :class="recorderState"><i></i>{{ recorderState === 'recording' ? '录制中' : recorderState === 'paused' ? '已暂停' : recorderState === 'saving' ? '保存中' : '摄像头已连接' }}</div></div><div class="record-controls"><div class="record-time"><span>{{ formattedTime }}</span><small>{{ recorderState === 'recording' ? '正在录制本题回答' : recorderState === 'paused' ? '录制已暂停' : recorderState === 'saved' ? '本题已保存，可重新录制' : '准备好后开始录制' }}</small></div><div class="record-buttons"><el-button v-if="recorderState === 'idle' || recorderState === 'error' || recorderState === 'saved'" class="record-button" @click="startRecording"><span class="record-dot"></span>{{ recorderState === 'saved' ? '重新录制' : '开始回答' }}</el-button><template v-else><el-button class="pause-button" :disabled="recorderState === 'saving'" @click="pauseRecording"><el-icon><VideoPause v-if="recorderState === 'recording'" /><VideoPlay v-else /></el-icon>{{ recorderState === 'recording' ? '暂停' : recorderState === 'paused' ? '继续' : '保存中' }}</el-button><el-button class="save-button" :disabled="recorderState === 'saving'" @click="stopRecording"><el-icon><CircleCheck /></el-icon>结束回答</el-button></template></div></div></div>
        <div class="session-bottom"><span><el-icon><Lock /></el-icon>视频保留 7 天 · 可随时删除</span><el-button class="primary-button" :disabled="recorderState === 'recording' || recorderState === 'paused' || recorderState === 'saving'" @click="finishQuestion">{{ currentIndex === questions.length - 1 ? '完成并查看报告' : '保存并继续' }} <el-icon><ArrowRight /></el-icon></el-button></div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.session-page { min-height: calc(100vh - 158px); }.session-top { display: flex; align-items: center; justify-content: space-between; gap: 13px; margin: -8px 0 18px; }.session-top .back-button { margin-right: auto; }.session-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 9px; color: #77837c; font-size: 10px; }.session-meta i { width: 3px; height: 3px; border-radius: 50%; background: #b7c0ba; }.end-button.el-button { height: 32px; border-color: #e8d4cc; color: #aa5e49; font-size: 10px; }.session-layout { display: grid; grid-template-columns: 250px minmax(0,1fr); align-items: start; gap: 15px; }.question-rail { position: sticky; top: 82px; padding: 14px 0 8px; box-shadow: none; }.rail-header { display: flex; justify-content: space-between; padding: 0 14px; color: #59655e; font-size: 10px; }.rail-header strong { color: var(--green); font-size: 10px; }.overall-progress { height: 3px; margin: 12px 14px 10px; border-radius: 3px; background: #edf0ed; }.overall-progress span { display: block; height: 100%; border-radius: 3px; background: var(--green); transition: width .25s ease; }.question-nav { display: flex; width: 100%; align-items: center; gap: 9px; padding: 11px 12px; border: 0; border-left: 2px solid transparent; background: white; text-align: left; }.question-nav:hover { background: #fafcfb; }.question-nav.active { border-left-color: var(--green); background: #f1f7f3; }.question-index { display: grid; width: 24px; height: 24px; flex: none; place-items: center; border: 1px solid #e4e9e5; border-radius: 50%; color: #98a39d; font-size: 9px; }.question-nav.answered .question-index { border-color: #d5eadb; background: var(--green-soft); color: var(--green); }.question-nav > span:nth-child(2) { display: grid; min-width: 0; flex: 1; gap: 5px; }.question-nav small { color: #98a19c; font-size: 8px; }.question-nav strong { display: -webkit-box; overflow: hidden; color: #616d66; font-size: 9px; font-weight: 500; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.question-nav.active strong { color: #35433b; font-weight: 600; }.question-nav > .el-icon { flex: none; color: var(--green); }.rail-note { display: flex; gap: 6px; margin: 13px 13px 0; padding-top: 10px; border-top: 1px solid #edf0ed; color: #849189; font-size: 8px; }
.session-main { display: grid; gap: 12px; }.interviewer-panel { overflow: hidden; box-shadow: none; }.interviewer-screen { height: 195px; overflow: hidden; background: #e8f0ec; }.interviewer-scene { position: relative; display: flex; height: 100%; align-items: center; overflow: hidden; background: linear-gradient(90deg,#e4eee8 0,#ecf3ef 55%,#e6efea 100%); }.interviewer-scene::before { position: absolute; top: 17px; right: 8%; width: 130px; height: 150px; border: 1px solid rgba(255,255,255,.65); border-radius: 3px; background: rgba(255,255,255,.25); content: ''; }.interviewer-glow { position: absolute; bottom: -90px; left: 14%; width: 240px; height: 200px; border-radius: 50%; background: rgba(255,255,255,.45); filter: blur(8px); }.avatar-frame { position: absolute; top: 17px; left: 22%; width: 126px; height: 190px; }.avatar-hair { position: absolute; top: 9px; left: 34px; z-index: 3; width: 59px; height: 67px; border-radius: 48% 52% 35% 40%; background: #493d36; }.avatar-face { position: absolute; top: 19px; left: 40px; z-index: 4; width: 47px; height: 56px; border-radius: 44% 44% 48% 48%; background: linear-gradient(110deg,#eec5a9,#dca78b); }.eye { position: absolute; top: 25px; width: 3px; height: 2px; border-radius: 50%; background: #463d38; }.eye-left { left: 11px; }.eye-right { right: 11px; }.nose { position: absolute; top: 28px; left: 22px; width: 4px; height: 8px; border-right: 1px solid rgba(132,84,66,.5); }.mouth { position: absolute; bottom: 9px; left: 18px; width: 11px; height: 4px; border-bottom: 1px solid #9c6055; border-radius: 50%; }.avatar-neck { position: absolute; top: 67px; left: 57px; z-index: 2; width: 16px; height: 23px; background: #d9a587; }.avatar-jacket { position: absolute; bottom: -10px; left: 5px; z-index: 1; width: 118px; height: 115px; border-radius: 49px 49px 0 0; background: linear-gradient(120deg,#4a7162,#284e41); }.avatar-shirt { position: absolute; bottom: 56px; left: 51px; z-index: 2; width: 30px; height: 50px; clip-path: polygon(0 0,100% 0,78% 100%,50% 80%,21% 100%); background: #f5f4ee; }.speech-lines { position: absolute; top: 53px; left: 48%; display: grid; gap: 7px; }.speech-lines i { width: 95px; height: 4px; border-radius: 4px; background: rgba(92,135,111,.23); }.speech-lines i:nth-child(2) { width: 74px; }.speech-lines i:nth-child(3) { width: 57px; }.interviewer-caption { position: absolute; right: 8%; bottom: 27px; display: grid; gap: 6px; }.interviewer-caption small { color: #698171; font-size: 8px; }.interviewer-caption strong { color: #3f584b; font-size: 11px; font-weight: 600; }.scene-dots { position: absolute; top: 12px; right: 13px; display: flex; gap: 4px; }.scene-dots i { width: 5px; height: 5px; border-radius: 50%; background: rgba(71,112,89,.25); }.scene-dots i.active { background: #568b69; }
.question-content { padding: 17px 19px 18px; }.question-kicker { color: var(--green); font-size: 9px; font-weight: 700; }.question-kicker span { margin-left: 10px; padding-left: 10px; border-left: 1px solid var(--line); color: #909a95; font-weight: 500; }.question-content h1 { max-width: 730px; margin: 10px 0 8px; color: #2f3c35; font-size: 17px; font-weight: 650; line-height: 1.65; }.question-content p { margin: 0; color: #909b95; font-size: 9px; line-height: 1.6; }
.recording-panel { display: flex; min-height: 109px; gap: 14px; align-items: center; padding: 12px 14px; box-shadow: none; }.self-preview { position: relative; width: 126px; height: 80px; flex: none; overflow: hidden; border-radius: 5px; background: #26352e; }.self-preview video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }.self-placeholder { display: grid; position: absolute; inset: 0; place-content: center; justify-items: center; gap: 4px; color: #dbe8df; font-size: 17px; }.self-placeholder small { font-size: 8px; }.recording-indicator { position: absolute; bottom: 5px; left: 5px; display: flex; align-items: center; gap: 4px; padding: 3px 5px; border-radius: 3px; background: rgba(18,29,23,.75); color: white; font-size: 7px; }.recording-indicator i { width: 5px; height: 5px; border-radius: 50%; background: #7bbc8e; }.recording-indicator.recording i { background: #e46758; animation: blink .8s infinite alternate; }.recording-indicator.paused i { background: #e4b451; }.recording-indicator.saving i { background: #7ba0b2; }@keyframes blink { to { opacity: .25; } }
.record-controls { display: flex; min-width: 0; flex: 1; align-items: center; justify-content: space-between; gap: 10px; }.record-time { display: grid; gap: 4px; }.record-time > span { color: #38453e; font-size: 19px; font-variant-numeric: tabular-nums; font-weight: 650; }.record-time small { color: #97a19b; font-size: 8px; }.record-buttons { display: flex; flex-wrap: wrap; gap: 7px; }.record-button.el-button { height: 34px; border-color: #b44b3c; background: #bd5545; color: white; font-size: 9px; }.record-button.el-button:hover { border-color: #a44233; background: #a44233; }.record-dot { width: 7px; height: 7px; border-radius: 50%; background: white; }.pause-button.el-button,.save-button.el-button { height: 32px; padding: 0 10px; font-size: 9px; }.pause-button.el-button { border-color: #e3e8e4; color: #65726a; }.save-button.el-button { border-color: #cfe5d4; background: #eef7f0; color: #4b865e; }
.session-bottom { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.session-bottom > span { display: inline-flex; align-items: center; gap: 5px; color: #8a958e; font-size: 8px; }.session-bottom .primary-button { height: 36px; }
.narration-audio { display: none; }
.narration-replay { display: inline-flex; align-items: center; gap: 4px; padding: 0; border: 0; background: transparent; color: #4f7a61; font-size: 8px; cursor: pointer; }
.narration-replay:hover { color: #35624a; }
.avatar-face .mouth.speaking { animation: avatar-talk .26s ease-in-out infinite; }
.interviewer-scene.thinking .speech-lines i { animation: avatar-pulse 1.4s ease-in-out infinite; }
.interviewer-scene.asking .speech-lines i { opacity: .9; }
@keyframes avatar-talk { 0%,100% { transform: scaleY(1); } 50% { transform: scaleY(2.8); } }
@keyframes avatar-pulse { 0%,100% { opacity: .3; } 50% { opacity: 1; } }
@media(max-width: 920px) { .session-layout { grid-template-columns: 205px minmax(0,1fr); gap: 10px; }.question-nav { gap: 6px; padding: 10px 8px; }.session-meta { justify-content: center; }.interviewer-screen { height: 170px; }.avatar-frame { left: 15%; transform: scale(.85); transform-origin: bottom center; }.interviewer-caption { right: 5%; }.interviewer-caption strong { font-size: 9px; } }
@media(max-width: 700px) { .session-top { flex-wrap: wrap; }.session-top .back-button { order: 0; }.session-meta { order: 2; width: 100%; justify-content: flex-start; }.end-button { order: 1; }.session-layout { grid-template-columns: 1fr; }.question-rail { position: static; padding-bottom: 5px; }.rail-header { padding: 0 12px; }.question-nav { display: none; }.question-nav.active { display: flex; }.rail-note { display: none; }.overall-progress { margin-bottom: 4px; }.interviewer-screen { height: 145px; }.avatar-frame { left: 8%; transform: scale(.72); }.interviewer-caption { right: 5%; bottom: 23px; max-width: 55%; }.interviewer-caption strong { font-size: 8px; }.speech-lines { left: 42%; }.recording-panel { align-items: stretch; flex-direction: column; }.self-preview { width: 100%; height: auto; aspect-ratio: 16/9; }.record-controls { align-items: flex-start; }.session-bottom > span { max-width: 45%; line-height: 1.5; } }
/* 面试官舞台：改成「左侧头像 + 右侧身份与朗读状态」两栏，填满整块区域，
   并去掉原来那个没有内容的装饰方框。头像换成矢量职员形象（西装/衬衫/领带/工牌/眼镜）。 */
.interviewer-screen { height: auto; overflow: visible; background: transparent; }
.interviewer-scene { display: grid; height: auto; min-height: 178px; grid-template-columns: minmax(140px,178px) minmax(0,1fr); align-items: center; gap: 20px; padding: 16px 22px; overflow: visible; border-radius: 8px 8px 0 0; background: linear-gradient(100deg,#e7f1eb 0%,#eef5f1 48%,#e6f0ea 100%); }
.interviewer-scene::before { display: none; }
.interviewer-glow { display: none; }
.portrait-frame { display: grid; height: 146px; place-items: center; overflow: hidden; border-radius: 8px; background: radial-gradient(circle at 50% 26%,#ffffff,#dbe9e2); box-shadow: inset 0 0 0 1px rgba(255,255,255,.65); }
.portrait-svg { width: 100%; height: 100%; }
.interviewer-info { display: grid; min-width: 0; gap: 9px; }
.interviewer-role { color: #5d7a68; font-size: 11px; font-weight: 600; }
.interviewer-status { display: flex; align-items: center; gap: 8px; color: #33443b; font-size: 13px; font-weight: 600; }
.interviewer-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.interviewer-tip { color: #8a978f; font-size: 10px; }
.narration-replay { font-size: 11px; }
.voice-bars { display: inline-flex; height: 13px; flex: none; align-items: flex-end; gap: 2px; }
.voice-bars i { width: 3px; height: 4px; border-radius: 2px; background: #a9c4b3; }
.voice-bars.active i { animation: voice-bounce .9s ease-in-out infinite; }
.voice-bars.active i:nth-child(2) { animation-delay: .12s; }
.voice-bars.active i:nth-child(3) { animation-delay: .24s; }
.voice-bars.active i:nth-child(4) { animation-delay: .36s; }
@keyframes voice-bounce { 0%,100% { height: 4px; } 50% { height: 13px; } }
/* 朗读时嘴型与轻微点头同步，待机时缓慢呼吸 */
.portrait-mouth { transform-box: fill-box; transform-origin: center; }
.interviewer-scene.speaking .portrait-mouth { animation: portrait-talk .3s ease-in-out infinite; }
.interviewer-scene.speaking .portrait-svg { animation: portrait-bob 1.9s ease-in-out infinite; }
.interviewer-scene.thinking .portrait-svg { animation: portrait-bob 3.4s ease-in-out infinite; }
@keyframes portrait-talk { 0%,100% { transform: scaleY(1); } 50% { transform: scaleY(2.1); } }
@keyframes portrait-bob { 0%,100% { translate: 0 0; } 50% { translate: 0 -3px; } }
@media(max-width: 700px) { .interviewer-scene { grid-template-columns: 116px minmax(0,1fr); gap: 12px; padding: 12px 14px; }.portrait-frame { height: 120px; }.interviewer-status { font-size: 12px; } }
@media (prefers-reduced-motion: reduce) { .portrait-svg, .portrait-mouth, .voice-bars.active i { animation: none; } }
</style>
