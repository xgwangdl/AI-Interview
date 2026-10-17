<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import {
  createAnswerAttempt, discardAnswerAttempt, getAnswerRewrite, tryCompleteAnswer,
} from '../services/api';
import { uploadAnswerMediaWithFallback } from '../services/chunkedUpload';
import { supportedRecorderType } from '../services/mediaCapture';

/**
 * 照着改写重录一题（Phase 3）。
 *
 * 录制完才创建新 attempt：如果用户中途退出，旧版本仍然是当前版本，不会留下一条空回答。
 */
const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const stream = ref(null);
const recording = ref(false);
const saving = ref(false);
const elapsed = ref(0);
const rewriteText = ref('');
const error = ref('');
const index = Number(route.params.questionIndex);
const session = computed(() => practice.find(route.params.id));
const question = computed(() => session.value?.questions?.[index] || null);
let recorder;
let chunks = [];
let startedAt;
let timer;
let createdAttemptId = null;
let saved = false;

onMounted(async () => {
  if (!session.value) {
    try {
      await practice.loadFromServer();
    } catch {
      // 下面统一提示
    }
  }
  if (!session.value) {
    ElMessage.error('找不到这次练习');
    router.replace('/history');
    return;
  }
  try {
    const rewrite = await getAnswerRewrite(route.query.sourceAnswerId);
    rewriteText.value = rewrite?.rewriteText || '';
  } catch {
    rewriteText.value = '';
  }
  await openCamera();
});

onBeforeUnmount(() => {
  window.clearInterval(timer);
  if (recorder && recorder.state !== 'inactive') recorder.stop();
  stream.value?.getTracks?.().forEach((track) => track.stop());
  if (createdAttemptId && !saved) {
    discardAnswerAttempt(createdAttemptId).catch(() => {});
  }
});

async function openCamera() {
  try {
    stream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
    if (video.value) video.value.srcObject = stream.value;
    error.value = '';
  } catch {
    error.value = '无法打开摄像头和麦克风，请检查浏览器权限。';
  }
}

function startRecording() {
  if (!stream.value || recording.value) return;
  try {
    chunks = [];
    recorder = new MediaRecorder(stream.value, { mimeType: supportedRecorderType() });
    recorder.ondataavailable = (event) => { if (event.data.size) chunks.push(event.data); };
    recorder.onstop = upload;
    recorder.start(1000);
    startedAt = Date.now();
    elapsed.value = 0;
    recording.value = true;
    timer = window.setInterval(() => {
      elapsed.value = Math.floor((Date.now() - startedAt) / 1000);
      if (elapsed.value >= 180) stopRecording();
    }, 250);
  } catch {
    ElMessage.error('当前浏览器无法启动录制');
  }
}

function stopRecording() {
  if (!recording.value) return;
  window.clearInterval(timer);
  recording.value = false;
  recorder.stop();
}

async function upload() {
  const blob = new Blob(chunks, { type: recorder?.mimeType || 'video/webm' });
  if (!blob.size) {
    ElMessage.warning('这一段没有录到内容，请重试');
    return;
  }
  saving.value = true;
  try {
    const attempt = await createAnswerAttempt(route.params.id, index);
    createdAttemptId = attempt.id;
    await uploadAnswerMediaWithFallback(attempt.id, blob);
    await tryCompleteAnswer(attempt.id);
    saved = true;
    await practice.loadFromServer();
    ElMessage.success('新版本已保存，正在重新分析这一题');
    router.replace(`/practice/${route.params.id}/report?index=${index}`);
  } catch (err) {
    ElMessage.error(err?.message || '上传失败，旧版本仍然保留');
  } finally {
    saving.value = false;
  }
}

function back() {
  router.push(`/practice/${route.params.id}/report?index=${index}`);
}

function timeLabel() {
  return `${String(Math.floor(elapsed.value / 60)).padStart(2, '0')}:${String(elapsed.value % 60).padStart(2, '0')}`;
}
</script>

<template>
  <section class="retake-page">
    <div class="page-heading">
      <div>
        <button class="back-link" @click="back"><el-icon><ArrowLeft /></el-icon>返回报告</button>
        <h1>照着改写重录</h1>
        <p>第 {{ index + 1 }} 题 · 新版上传后才会替换当前答案，中途退出不会丢旧版本。</p>
      </div>
    </div>

    <div class="retake-grid">
      <section class="panel retake-copy">
        <div class="section-title">题目</div>
        <h2>{{ question?.text || '题目加载中' }}</h2>
        <div class="rewrite-box">
          <div class="section-title">改写参考</div>
          <p v-if="rewriteText">{{ rewriteText }}</p>
          <p v-else class="muted">这一题还没有改写。先回报告页生成改写，再回来重录。</p>
        </div>
        <p class="retake-tip">照着说即可，不需要逐字背诵；重点是结构、动词和结果。</p>
      </section>

      <section class="panel retake-record">
        <div class="preview">
          <video ref="video" autoplay muted playsinline></video>
          <span v-if="recording" class="recording-dot">录制中 {{ timeLabel() }}</span>
          <span v-else-if="saving" class="recording-dot saving">上传中…</span>
        </div>
        <p v-if="error" class="retake-error">{{ error }}</p>
        <div class="retake-actions">
          <el-button v-if="!recording" class="primary-button" :disabled="saving || !stream" @click="startRecording">
            <el-icon><VideoCamera /></el-icon>开始重录
          </el-button>
          <el-button v-else class="primary-button" @click="stopRecording">
            <el-icon><CircleCheck /></el-icon>结束并保存
          </el-button>
          <el-button class="soft-button" :disabled="recording || saving" @click="back">取消</el-button>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.retake-page { max-width: 1040px; }
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 7px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.back-link:hover { color: var(--green); }
.retake-grid { display: grid; grid-template-columns: minmax(0, .9fr) minmax(0, 1.1fr); gap: 16px; }
.retake-copy, .retake-record { display: grid; align-content: start; gap: 13px; padding: 18px; box-shadow: none; }
.retake-copy h2 { margin: 0; color: #2f3c35; font-size: 19px; line-height: 1.6; }
.rewrite-box { padding: 12px; border-radius: 8px; background: #f5faf6; }
.rewrite-box p { margin: 7px 0 0; color: #46524c; font-size: 13px; line-height: 1.8; white-space: pre-wrap; }
.rewrite-box p.muted { color: #98a29d; }
.retake-tip { margin: 0; color: #98a29d; font-size: 12px; line-height: 1.7; }
.preview { position: relative; overflow: hidden; aspect-ratio: 16/9; border-radius: 8px; background: #26352e; }
.preview video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }
.recording-dot { position: absolute; bottom: 10px; left: 10px; padding: 4px 8px; border-radius: 4px; background: rgba(18,29,23,.78); color: white; font-size: 12px; }
.recording-dot:not(.saving)::before { display: inline-block; width: 6px; height: 6px; margin-right: 5px; border-radius: 50%; background: #e46758; content: ''; animation: blink .8s infinite alternate; }
@keyframes blink { to { opacity: .25; } }
.retake-error { margin: 0; color: #a4483a; font-size: 13px; }
.retake-actions { display: flex; gap: 8px; }
@media (max-width: 820px) { .retake-grid { grid-template-columns: 1fr; } }
</style>
