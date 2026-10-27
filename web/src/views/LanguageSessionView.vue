<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import DigitalHuman from '../components/DigitalHuman.vue';
import { AudioRecorder } from '../services/audioRecorder';
import { LanguageStream } from '../services/languageStream';
import { PcmCapture, PcmPlayer } from '../services/pcmStream';
import {
  fetchAudio, finishSession, getSession, latencyLabel, openingSpeechUrl, pickVoice, reportRows,
  resolveSpeechMode, sessionSubtitle, submitAudioTurn, submitTextTurn,
} from '../services/language';
import { useI18n } from 'vue-i18n';

/*
 * 回放优先用服务端合成的音频（英语）；没有服务端音色（日语）时用浏览器语音，
 * 本机也没有日语语音就只显示文字 —— 绝不拿中文音色硬念日语。
 */
const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const session = ref(null);
const loading = ref(true);
const busy = ref(false);
const recording = ref(false);
const elapsed = ref(0);
const textMode = ref(false);
const draft = ref('');
const speaking = ref(false);
const amplitude = ref(-1);
const voices = ref([]);
// L2：实时通道默认开；连不上自动退回逐轮（L1）
// L2 通道已接通，但实时识别输入仍被上游拒绝（NO_VALID_AUDIO_ERROR），先默认关闭，避免半成品影响练习
const liveMode = ref(localStorage.getItem('language-live-mode') === 'on');
const partialText = ref('');
const liveReply = ref('');
const player = new PcmPlayer(24000);
let stream = null;
let capture = null;

const recorder = new AudioRecorder();
let audio = null;
let audioUrl = '';
let ticker = null;
let amplifier = null;
let activeStream = null;

const lastTurn = computed(() => {
  const turns = session.value?.turns || [];
  return turns.length ? turns[turns.length - 1] : null;
});
/** 开场白放在数字人下面；一旦有对话，文字就只留在右边的轮次卡里（避免同一句显示两遍）。 */
const openingLine = computed(() => (lastTurn.value ? '' : (session.value?.opening || '')));
const statusLine = computed(() => (speaking.value
  ? t('languageSession.speaking')
  : (recording.value ? t('languageSession.listeningStatus') : (busy.value ? t('languageSession.thinking') : t('languageSession.turnPrompt')))));
const full = computed(() => Boolean(session.value && session.value.turnCount >= session.value.maxTurns));
const finished = computed(() => session.value?.status === 'finished');
const rows = computed(() => reportRows(session.value?.report, t));
const canSpeak = computed(() => Boolean(
  session.value?.serverVoice || pickVoice(voices.value, session.value?.browserVoice),
));

onMounted(async () => {
  await load();
  refreshVoices();
  if (liveMode.value) void connectStream();
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.addEventListener?.('voiceschanged', refreshVoices);
  }
  if (session.value && !finished.value) void speakOpeningWhenReady();
});

onBeforeUnmount(() => {
  capture?.stop();
  player.stop();
  stream?.close();
  stopSpeaking();
  recorder.cancel();
  stopStream();
  clearInterval(ticker);
  if (audioUrl) URL.revokeObjectURL(audioUrl);
  if (typeof window !== 'undefined' && window.speechSynthesis) {
    window.speechSynthesis.removeEventListener?.('voiceschanged', refreshVoices);
  }
});

watch(recording, (value) => {
  clearInterval(ticker);
  if (!value) return;
  ticker = setInterval(() => {
    elapsed.value += 1;
    if (elapsed.value >= 60) void stopAndSend();
  }, 1000);
});

async function load() {
  loading.value = true;
  try {
    session.value = await getSession(route.params.id);
  } catch (error) {
    ElMessage.error(error?.message || t('languageSession.loadFailed'));
    router.replace('/language');
  } finally {
    loading.value = false;
  }
}

function refreshVoices() {
  if (typeof window === 'undefined' || !window.speechSynthesis) return;
  voices.value = window.speechSynthesis.getVoices();
}

async function toggleRecording() {
  if (busy.value || full.value || finished.value) return;
  if (recording.value) {
    await stopAndSend();
    return;
  }
  try {
    stopSpeaking();
    // 打断：上一轮还在播/还在说，直接停
    player.stop();
    stream?.cancel();
    activeStream = activeStream || await navigator.mediaDevices.getUserMedia({ audio: true });
    if (liveMode.value && await beginLiveTurn()) {
      elapsed.value = 0;
      recording.value = true;
      return;
    }
    activeStream = await recorder.start(activeStream);
    elapsed.value = 0;
    recording.value = true;
  } catch {
    ElMessage.error(t('languageSession.micFailed'));
    textMode.value = true;
  }
}

async function stopAndSend() {
  if (!recording.value) return;
  recording.value = false;
  if (capture) {
    capture.stop();
    capture = null;
    busy.value = true;
    stopSpeaking();
    stream.commit();
    return;
  }
  const captured = await recorder.stop();
  if (!captured) {
    ElMessage.warning(t('languageSession.emptyAudio'));
    return;
  }
  await send(() => submitAudioTurn(session.value.id, captured.blob, captured.durationMs));
}

// ---------- L2 实时通道 ----------

async function connectStream() {
  if (stream?.connected) return true;
  const candidate = new LanguageStream({
    onMessage: handleStreamMessage,
    onClose: () => {
      if (recording.value || busy.value) {
        capture?.stop();
        capture = null;
        recording.value = false;
        busy.value = false;
        ElMessage.error(t('languageSession.liveDisconnected'));
      }
    },
  });
  try {
    await candidate.connect();
    stream = candidate;
    return true;
  } catch {
    stream = null;
    liveMode.value = false;
    localStorage.setItem('language-live-mode', 'off');
    ElMessage.warning(t('languageSession.liveFallback'));
    return false;
  }
}

async function beginLiveTurn() {
  if (!(await connectStream())) return false;
  stream.start(session.value.id);
  capture = new PcmCapture((frame) => stream.sendFrame(frame));
  await capture.start(activeStream);
  return true;
}

function changeLiveMode(value) {
  localStorage.setItem('language-live-mode', value ? 'on' : 'off');
  if (value) {
    void connectStream();
  } else {
    capture?.stop();
    capture = null;
    player.stop();
    stream?.close();
    stream = null;
    partialText.value = '';
    liveReply.value = '';
  }
}

/**
 * 实时通道的消息：partial → 转写边出；reply → 文字边出；audio → PCM 排队播放；
 * feedback 比 turn 晚一点到（先接话，再纠错）。
 */
function handleStreamMessage(message) {
  switch (message.type) {
    case 'waiting':
    case 'listening':
      break;
    case 'partial':
      partialText.value = message.text || '';
      break;
    case 'transcript':
      partialText.value = message.text || '';
      break;
    case 'reply':
      liveReply.value += message.delta || '';
      speaking.value = true;
      break;
    case 'audio':
      player.push(message.data);
      break;
    case 'turn': {
      const turns = session.value.turns || [];
      session.value.turns = [...turns, {
        turnIndex: message.turnIndex,
        transcript: partialText.value,
        replyText: message.replyText,
        feedback: [],
        latency: message.latency,
        status: 'answered',
      }];
      session.value.turnCount = message.turnIndex;
      partialText.value = '';
      liveReply.value = '';
      busy.value = false;
      speaking.value = false;
      break;
    }
    case 'feedback': {
      const turns = session.value.turns || [];
      if (turns.length) turns[turns.length - 1].feedback = message.items || [];
      break;
    }
    case 'error':
      partialText.value = '';
      liveReply.value = '';
      busy.value = false;
      speaking.value = false;
      ElMessage.error(message.message || t('languageSession.liveError'));
      break;
    default:
      break;
  }
}

async function sendText() {
  const text = draft.value.trim();
  if (!text || busy.value) return;
  await send(() => submitTextTurn(session.value.id, text));
  draft.value = '';
}

async function send(call) {
  busy.value = true;
  try {
    const turn = await call();
    session.value.turns = [...(session.value.turns || []), turn];
    session.value.turnCount = turn.turnIndex;
    textMode.value = false;
    await speak(turn.replyText, turn);
  } catch (error) {
    // 识别/模型失败只影响这一轮：说清原因，并提示可以手打继续
    ElMessage.error(error?.message || t('languageSession.turnFailed'));
    textMode.value = true;
  } finally {
    busy.value = false;
  }
}

async function finish() {
  if (busy.value) return;
  busy.value = true;
  try {
    stopSpeaking();
    session.value = await finishSession(session.value.id);
  } catch (error) {
    ElMessage.error(error?.message || t('languageSession.reviewFailed'));
  } finally {
    busy.value = false;
  }
}

/** 播放一句话：有服务端音频就用它，否则用浏览器语音。 */
async function speak(text, turn = null) {
  stopSpeaking();
  if (!text) return;
  const preferred = localStorage.getItem('language-voice-mode') || 'server';
  const voice = pickVoice(voices.value, session.value?.browserVoice);
  const mode = resolveSpeechMode(preferred, Boolean(voice), Boolean(session.value?.serverVoice));
  const serverUrl = turn ? turn.replyAudioUrl : openingSpeechUrl(session.value.id);
  // 本机有对应语言的系统语音时才优先用浏览器；没有英文音色就自动使用服务端音色。
  if (mode === 'browser') {
    speakBrowser(text, voice);
    return;
  }
  if (mode === 'server' && serverUrl) {
    try {
      await playServerAudio(serverUrl, text, voice);
      return;
    } catch {
      // 服务端音频取不到就退回浏览器语音，不让用户干等
    }
  }
  speakBrowser(text, voice);
}

/** 等待浏览器语音列表加载；超时后仍然继续，speak() 会自动回退到服务端音色。 */
async function speakOpeningWhenReady() {
  const preferred = localStorage.getItem('language-voice-mode') || 'server';
  if (preferred !== 'server') await waitForVoices(1200);
  if (session.value && !finished.value) await speak(session.value.opening);
}

function waitForVoices(timeoutMs) {
  if (voices.value.length || typeof window === 'undefined' || !window.speechSynthesis) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    let settled = false;
    let timer = null;
    const done = () => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      window.speechSynthesis.removeEventListener?.('voiceschanged', done);
      refreshVoices();
      resolve();
    };
    timer = setTimeout(done, timeoutMs);
    window.speechSynthesis.addEventListener?.('voiceschanged', done);
    refreshVoices();
    if (voices.value.length) done();
  });
}

async function playServerAudio(url, text, fallbackVoice) {
  const blob = await fetchAudio(url);
  if (audioUrl) URL.revokeObjectURL(audioUrl);
  audioUrl = URL.createObjectURL(blob);
  audio = new Audio(audioUrl);
  audio.onended = stopSpeaking;
  audio.onerror = stopSpeaking;
  speaking.value = true;
  try {
    await audio.play();
  } catch {
    stopSpeaking();
    if (fallbackVoice) {
      speakBrowser(text, fallbackVoice);
      return;
    }
    ElMessage.warning(t('languageSession.audioBlocked'));
  }
}

function speakBrowser(text, picked = null) {
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    ElMessage.warning(t('languageSession.noSpeechSynthesis'));
    return;
  }
  const voice = picked || pickVoice(voices.value, session.value?.browserVoice);
  if (!voice) {
    ElMessage.warning(t('languageSession.noLanguageVoice'));
    return;
  }
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.voice = voice;
  utterance.lang = voice.lang;
  utterance.onend = stopSpeaking;
  utterance.onerror = stopSpeaking;
  speaking.value = true;
  amplifier = setInterval(() => { amplitude.value = 0.25 + Math.random() * 0.55; }, 120);
  window.speechSynthesis.speak(utterance);
}

function stopSpeaking() {
  speaking.value = false;
  amplitude.value = -1;
  clearInterval(amplifier);
  amplifier = null;
  if (audio) {
    audio.pause();
    audio = null;
  }
  if (typeof window !== 'undefined' && window.speechSynthesis) window.speechSynthesis.cancel();
}

function stopStream() {
  activeStream?.getTracks?.().forEach((track) => track.stop());
  activeStream = null;
}

function backToList() {
  router.push('/language');
}
</script>

<template>
  <section v-loading="loading" class="language-session">
    <template v-if="session">
      <div class="page-heading session-heading">
        <div>
          <button class="back-link" @click="backToList"><el-icon><ArrowLeft /></el-icon>{{ t('languageSession.back') }}</button>
          <h1>{{ session.languageName }} · {{ t('languageSession.roundCount', { count: session.turns.length, total: session.maxTurns }) }}</h1>
          <p>{{ session.target }} · {{ sessionSubtitle(session, t) }}</p>
        </div>
        <div v-if="!finished" class="heading-actions">
          <!-- L2 实时对话：通道已通，实时识别输入待修（见 docs/STATUS.md），修好后再把开关放出来 -->
          <el-switch v-if="false" v-model="liveMode" :active-text="t('languageSession.liveMode')" @change="changeLiveMode" />
          <el-button class="soft-button" :loading="busy" @click="finish">
            <el-icon><Finished /></el-icon> {{ t('languageSession.finishReview') }}
          </el-button>
        </div>
      </div>

      <div v-if="!finished" class="session-layout">
        <section class="interviewer-panel panel">
          <div class="interviewer-screen">
            <DigitalHuman avatar-variant="english-coach" :use-idle-video="false" :speaking="speaking" :amplitude="() => amplitude" />
            <div class="interviewer-info">
              <div class="interviewer-role">{{ t('languageSession.aiPartner', { language: session.languageName }) }}</div>
              <p v-if="liveReply" class="ai-line live">{{ liveReply }}</p>
              <p v-else-if="openingLine" class="ai-line">{{ openingLine }}</p>
              <p v-else class="ai-line status">{{ statusLine }}</p>
              <button v-if="lastTurn && canSpeak" class="replay" @click="speak(lastTurn.replyText, lastTurn)">
                <el-icon><VideoPlay /></el-icon> {{ t('languageSession.replay') }}
              </button>
              <button v-else-if="openingLine && canSpeak" class="replay" @click="speak(openingLine)">
                <el-icon><VideoPlay /></el-icon> {{ t('languageSession.replay') }}
              </button>
            </div>
          </div>
          <p class="scene-hint">{{ t('languageSession.sceneHint', { scenario: session.scenario }) }}</p>
        </section>

        <section class="talk-panel panel">
          <div class="turn-list">
            <p v-if="!session.turns.length" class="turn-empty">{{ t('languageSession.emptyConversation') }}</p>
            <div v-for="turn in session.turns" :key="turn.turnIndex" class="turn-card">
              <div class="turn-you"><span>{{ t('languageSession.youRound', { round: turn.turnIndex }) }}</span><strong>{{ turn.transcript }}</strong></div>
              <div class="turn-ai"><span>{{ session.languageName }}</span><strong>{{ turn.replyText }}</strong></div>
              <div v-if="turn.feedback?.length" class="turn-feedback">
                <div v-for="(item, index) in turn.feedback" :key="index">
                  <s>{{ item.original }}</s> → <b>{{ item.better }}</b>
                  <small>{{ item.why }}</small>
                </div>
              </div>
              <div class="turn-foot">
                <button class="mini-link" @click="speak(turn.replyText, turn)">{{ t('languageSession.listenReply') }}</button>
                <span>{{ latencyLabel(turn.latency, t) }}</span>
              </div>
            </div>
          </div>

          <div class="talk-controls">
            <button class="talk-button" :class="{ recording }" :disabled="busy || full"
                    @click="toggleRecording">
              <el-icon><Microphone v-if="!recording" /><VideoPause v-else /></el-icon>
              <span v-if="recording">{{ t('languageSession.listening', { seconds: elapsed }) }}</span>
              <span v-else-if="busy">{{ t('languageSession.thinking') }}</span>
              <span v-else-if="full">{{ t('languageSession.full', { count: session.maxTurns }) }}</span>
              <span v-else>{{ liveMode ? t('languageSession.startLive') : t('languageSession.startSpeaking') }}</span>
            </button>
            <p v-if="partialText" class="partial-line">{{ t('languageSession.recognizing', { text: partialText }) }}</p>
            <button class="mini-link text-toggle" @click="textMode = !textMode">
              {{ textMode ? t('languageSession.closeTextMode') : t('languageSession.openTextMode') }}
            </button>
            <div v-if="textMode" class="text-input">
              <el-input v-model="draft" type="textarea" :rows="2" maxlength="1000"
                        :placeholder="t('languageSession.writeInLanguage', { language: session.languageName })" @keyup.ctrl.enter="sendText" />
              <el-button class="soft-button" :loading="busy" @click="sendText">{{ t('languageSession.send') }}</el-button>
            </div>
          </div>
        </section>
      </div>

      <section v-else class="report-panel panel">
        <div class="report-head">
          <div>
            <div class="eyebrow">{{ t('languageSession.reviewEyebrow') }}</div>
            <h2>{{ t('languageSession.reviewTitle') }}</h2>
            <p>{{ session.report?.summary || t('languageSession.summaryFallback') }}</p>
          </div>
          <el-button class="soft-button" @click="backToList">{{ t('languageSession.practiceAgain') }}</el-button>
        </div>
        <div class="score-grid">
          <div v-for="row in rows" :key="row.key" class="score-cell">
            <span>{{ row.label }}</span>
            <strong>{{ row.display }}</strong>
            <i><b :style="{ width: `${row.value || 0}%` }"></b></i>
          </div>
        </div>
        <div v-if="session.report?.improvements?.length" class="improvements">
          <h3>{{ t('languageSession.nextSteps') }}</h3>
          <ul>
            <li v-for="(item, index) in session.report.improvements" :key="index">{{ item }}</li>
          </ul>
        </div>
        <div class="transcript">
          <h3>{{ t('languageSession.fullConversation') }}</h3>
          <div v-for="turn in session.turns" :key="turn.turnIndex" class="transcript-row">
            <span>{{ turn.turnIndex }}</span>
            <div><strong>{{ turn.transcript }}</strong><p>{{ turn.replyText }}</p></div>
          </div>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.language-session { max-width: 1180px; }
.session-heading { align-items: flex-end; }
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 6px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.back-link:hover { color: var(--green); }
.session-heading h1 { margin: 0 0 6px; color: #2f3c35; font-size: 23px; font-weight: 650; }
.session-heading p { margin: 0; color: #8d9792; font-size: 13px; }
.session-layout { display: grid; grid-template-columns: minmax(0, 0.85fr) minmax(0, 1.15fr); gap: 18px; }
.interviewer-panel { padding: 16px 16px 14px; box-shadow: none; align-self: start; }
.interviewer-screen { display: grid; gap: 12px; }
.interviewer-screen :deep(.dh-stage) { aspect-ratio: 340 / 300; }
.interviewer-role { color: var(--green); font-size: 12px; font-weight: 700; letter-spacing: .04em; }
.ai-line { margin: 8px 0 0; color: #2f3c35; font-size: 16px; font-weight: 600; line-height: 1.6; }
.ai-line.status { color: #7d8982; font-size: 14px; font-weight: 500; }
.ai-line.live { color: #2f3c35; }
.heading-actions { display: flex; align-items: center; gap: 14px; }
.partial-line { margin: 2px 0 0; padding: 6px 9px; border-radius: 6px; background: #f5faf6; color: #55635b; font-size: 13px; line-height: 1.6; }
.replay { display: inline-flex; align-items: center; gap: 5px; margin-top: 8px; padding: 0; border: 0; background: transparent; color: #6c7a73; font-size: 12px; }
.scene-hint { margin: 12px 0 0; padding-top: 10px; border-top: 1px solid #eef1ee; color: #8d9792; font-size: 12px; line-height: 1.7; }
.talk-panel { display: grid; gap: 14px; padding: 16px; box-shadow: none; }
.turn-list { display: grid; gap: 10px; max-height: 460px; overflow: auto; }
.turn-empty { margin: 0; padding: 24px 0; color: #98a29d; font-size: 13px; text-align: center; }
.turn-card { display: grid; gap: 6px; padding: 10px 12px; border: 1px solid #eef1ee; border-radius: 8px; background: #fcfdfc; }
.turn-you span, .turn-ai span { display: block; margin-bottom: 3px; color: #98a29d; font-size: 11px; }
.turn-you strong { color: #46524c; font-size: 13px; font-weight: 500; line-height: 1.6; }
.turn-ai strong { color: #2f3c35; font-size: 14px; line-height: 1.6; }
.turn-feedback { display: grid; gap: 5px; padding: 7px 9px; border-radius: 6px; background: #fdf6ea; }
.turn-feedback div { color: #7c6a52; font-size: 12px; line-height: 1.6; }
.turn-feedback s { color: #b08a63; }
.turn-feedback b { color: #8a5a22; }
.turn-feedback small { display: block; color: #9a8b78; }
.turn-foot { display: flex; align-items: center; justify-content: space-between; color: #a4ada8; font-size: 11px; }
.mini-link { padding: 0; border: 0; background: transparent; color: var(--green); font-size: 12px; }
.talk-controls { display: grid; gap: 8px; padding-top: 10px; border-top: 1px solid #eef1ee; }
.talk-button { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 46px; border: 1px solid #9dc7ac; border-radius: 8px; background: #f5faf6; color: var(--green); font-size: 14px; font-weight: 600; }
.talk-button.recording { border-color: #e0b48c; background: #fdf6ea; color: #b26a22; }
.talk-button:disabled { border-color: #e6ebe8; background: #f7f9f8; color: #9aa39f; }
.text-toggle { justify-self: start; color: #7d8982; }
.text-input { display: grid; gap: 8px; }
.text-input .el-button { justify-self: end; }
.report-panel { display: grid; gap: 20px; padding: 22px; box-shadow: none; }
.report-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.report-head h2 { margin: 4px 0 6px; color: #2f3c35; font-size: 20px; }
.report-head p { margin: 0; color: #7d8982; font-size: 13px; line-height: 1.7; }
.score-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.score-cell { display: grid; gap: 6px; padding: 12px 14px; border: 1px solid #eef1ee; border-radius: 8px; }
.score-cell span { color: #8d9792; font-size: 12px; }
.score-cell strong { color: #2f3c35; font-size: 24px; font-weight: 650; }
.score-cell i { display: block; height: 6px; overflow: hidden; border-radius: 3px; background: #eef1ee; }
.score-cell i b { display: block; height: 100%; background: var(--green); }
.improvements h3, .transcript h3 { margin: 0 0 8px; color: #46524c; font-size: 14px; }
.improvements ul { margin: 0; padding-left: 18px; color: #5d6a64; font-size: 13px; line-height: 1.9; }
.transcript { display: grid; gap: 8px; }
.transcript-row { display: grid; grid-template-columns: 24px minmax(0, 1fr); gap: 10px; padding: 8px 0; border-top: 1px dashed #eef1ee; }
.transcript-row > span { color: #a4ada8; font-size: 12px; }
.transcript-row strong { color: #46524c; font-size: 13px; }
.transcript-row p { margin: 3px 0 0; color: #7d8982; font-size: 13px; line-height: 1.6; }
@media (max-width: 900px) {
  .session-layout { grid-template-columns: 1fr; }
  .score-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .report-head { flex-direction: column; }
}
</style>
