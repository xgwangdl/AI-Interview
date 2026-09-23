<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { getActiveStream, stopActiveStream, supportedRecorderType } from '../services/mediaCapture';
import { getMedia, saveMedia } from '../services/mediaStore';
import { buildQuestions, styleLabels } from '../data/questions';
import { tryCompleteSession, trySaveAnswer, tryUploadAnswerMedia } from '../services/api';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const recorderState = ref('idle');
const currentIndex = ref(0);
const elapsed = ref(0);
const savedAnswer = ref(false);
const stream = getActiveStream();
const draft = practice.draft?.id === route.params.id ? practice.draft : practice.find(route.params.id);
const questions = computed(() => draft ? (draft.questions || buildQuestions(draft.role, draft.questionCount, draft.interviewType)) : []);
const currentQuestion = computed(() => questions.value[currentIndex.value]);
const answeredQuestions = computed(() => new Set(draft?.answers.map((answer) => answer.questionIndex) || []));
const progress = computed(() => draft ? Math.round((answeredQuestions.value.size / questions.value.length) * 100) : 0);
const formattedTime = computed(() => `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`);
let recorder;
let chunks = [];
let startedAt;
let pausedAt;
let pausedMilliseconds = 0;
let elapsedTimer;
let stopPromise;

onMounted(() => {
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
});

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
  await tryUploadAnswerMedia(saved.id, blob);
  answer.mediaId = saved.id;
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
        <div class="interviewer-panel panel"><div class="interviewer-screen"><div class="interviewer-scene"><div class="interviewer-glow"></div><div class="avatar-frame"><div class="avatar-hair"></div><div class="avatar-face"><i class="eye eye-left"></i><i class="eye eye-right"></i><i class="nose"></i><i class="mouth"></i></div><div class="avatar-neck"></div><div class="avatar-jacket"></div><div class="avatar-shirt"></div></div><div class="speech-lines"><i></i><i></i><i></i></div><div class="interviewer-caption"><small>AI 面试官 · {{ styleLabels[draft.interviewerStyle] }}</small><strong>请仔细阅读问题，准备好后开始回答</strong></div></div><div class="scene-dots"><i class="active"></i><i></i><i></i></div></div><div class="question-content"><div class="question-kicker">QUESTION {{ String(currentIndex + 1).padStart(2,'0') }} <span>{{ currentQuestion?.category }}</span></div><h1>{{ currentQuestion?.text }}</h1><p>建议使用具体事例展开：背景与目标、采取的行动、最终结果，以及你的复盘。</p></div></div>

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
@media(max-width: 920px) { .session-layout { grid-template-columns: 205px minmax(0,1fr); gap: 10px; }.question-nav { gap: 6px; padding: 10px 8px; }.session-meta { justify-content: center; }.interviewer-screen { height: 170px; }.avatar-frame { left: 15%; transform: scale(.85); transform-origin: bottom center; }.interviewer-caption { right: 5%; }.interviewer-caption strong { font-size: 9px; } }
@media(max-width: 700px) { .session-top { flex-wrap: wrap; }.session-top .back-button { order: 0; }.session-meta { order: 2; width: 100%; justify-content: flex-start; }.end-button { order: 1; }.session-layout { grid-template-columns: 1fr; }.question-rail { position: static; padding-bottom: 5px; }.rail-header { padding: 0 12px; }.question-nav { display: none; }.question-nav.active { display: flex; }.rail-note { display: none; }.overall-progress { margin-bottom: 4px; }.interviewer-screen { height: 145px; }.avatar-frame { left: 8%; transform: scale(.72); }.interviewer-caption { right: 5%; bottom: 23px; max-width: 55%; }.interviewer-caption strong { font-size: 8px; }.speech-lines { left: 42%; }.recording-panel { align-items: stretch; flex-direction: column; }.self-preview { width: 100%; height: auto; aspect-ratio: 16/9; }.record-controls { align-items: flex-start; }.session-bottom > span { max-width: 45%; line-height: 1.5; } }
</style>
