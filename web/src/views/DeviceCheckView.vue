<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue';
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { setActiveStream, stopActiveStream } from '../services/mediaCapture';
import { tryStartSession } from '../services/api';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const video = ref();
const cameras = ref([]);
const microphones = ref([]);
const selectedCamera = ref('');
const selectedMicrophone = ref('');
const stream = shallowRef(null);
const state = ref('idle');
const issue = ref('');
const checking = ref(false);
let keepStreamForSession = false;
const cameraReady = computed(() => Boolean(stream.value?.getVideoTracks().some((track) => track.readyState === 'live')));
const microphoneReady = computed(() => Boolean(stream.value?.getAudioTracks().some((track) => track.readyState === 'live')));
const draftId = String(route.query.id || '');
const draft = computed(() => practice.draft?.id === draftId ? practice.draft : practice.find(draftId));

onMounted(async () => {
  if (!draft.value) {
    ElMessage.warning('练习配置已失效，请重新创建。');
    router.replace('/practice/setup');
    return;
  }
  practice.resumeDraft(draft.value);
  await refreshDevices();
});

watch([selectedCamera, selectedMicrophone], async () => {
  if (stream.value && state.value === 'ready') await startCheck();
});

async function refreshDevices() {
  if (!navigator.mediaDevices?.enumerateDevices) {
    state.value = 'unsupported';
    issue.value = '当前浏览器不支持摄像头与麦克风检测，请使用桌面版 Chrome 或 Edge。';
    return;
  }
  try {
    const devices = await navigator.mediaDevices.enumerateDevices();
    cameras.value = devices.filter((device) => device.kind === 'videoinput').map((device, index) => ({ ...device, label: device.label || `摄像头 ${index + 1}` }));
    microphones.value = devices.filter((device) => device.kind === 'audioinput').map((device, index) => ({ ...device, label: device.label || `麦克风 ${index + 1}` }));
    if (!selectedCamera.value) selectedCamera.value = cameras.value[0]?.deviceId || '';
    if (!selectedMicrophone.value) selectedMicrophone.value = microphones.value[0]?.deviceId || '';
  } catch {
    cameras.value = [];
    microphones.value = [];
  }
}

async function startCheck() {
  checking.value = true;
  issue.value = '';
  if (!navigator.mediaDevices?.getUserMedia) {
    state.value = 'unsupported';
    issue.value = '浏览器不支持媒体设备访问，请使用最新版本的 Chrome 或 Edge。';
    checking.value = false;
    return;
  }
  stopActiveStream();
  stream.value = null;
  try {
    const audio = selectedMicrophone.value ? { deviceId: { exact: selectedMicrophone.value }, echoCancellation: true, noiseSuppression: true } : true;
    const videoConstraints = selectedCamera.value ? { deviceId: { exact: selectedCamera.value }, width: { ideal: 1280 }, height: { ideal: 720 } } : true;
    const nextStream = await navigator.mediaDevices.getUserMedia({ audio, video: videoConstraints });
    stream.value = nextStream;
    setActiveStream(nextStream);
    state.value = 'ready';
    await refreshDevices();
    await nextTick();
    if (video.value) video.value.srcObject = nextStream;
  } catch (error) {
    state.value = 'error';
    if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') issue.value = '未找到可用的摄像头或麦克风，请连接设备后重试。';
    else if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') issue.value = '设备权限被拒绝。请在浏览器地址栏的权限设置中允许摄像头和麦克风，再重新检测。';
    else if (error.name === 'OverconstrainedError') issue.value = '所选设备当前不可用，请更换设备后重试。';
    else issue.value = '无法访问媒体设备，请检查设备连接和浏览器权限。';
  } finally {
    checking.value = false;
  }
}

async function continueToSession() {
  if (!cameraReady.value || !microphoneReady.value) return;
  try {
    await tryStartSession(draftId);
  } catch (error) {
    ElMessage.error(error.message || '无法开始练习，请检查网络后重试。');
    return;
  }
  keepStreamForSession = true;
  router.push(`/practice/${draftId}/session`);
}

onBeforeRouteLeave((to) => {
  if (to.name !== 'session') stopActiveStream();
});
onBeforeUnmount(() => {
  if (!keepStreamForSession) stopActiveStream();
});
</script>

<template>
  <section class="device-page">
    <div class="page-heading"><div><div class="eyebrow">STEP 02 / 02</div><h1>检查设备与录制画面</h1><p>允许访问摄像头和麦克风后，确认画面清晰、声音正常。</p></div><el-tag class="tag-muted">{{ draft?.role || '模拟面试' }}</el-tag></div>
    <div class="device-layout">
      <div class="device-main panel">
        <div class="preview-frame" :class="{ 'preview-ready': cameraReady }">
          <video ref="video" autoplay muted playsinline></video>
          <div v-if="!cameraReady" class="preview-placeholder"><div class="preview-icon"><el-icon><VideoCamera /></el-icon></div><strong>{{ state === 'error' ? '设备检测未完成' : state === 'unsupported' ? '浏览器不受支持' : '准备好后开启摄像头' }}</strong><span>{{ state === 'idle' ? '点击下方按钮，授权本次练习所需的设备权限' : issue }}</span></div>
          <div v-if="cameraReady" class="preview-live"><i></i>画面预览</div>
          <div class="preview-corner">16:9 预览</div>
        </div>
        <div class="preview-controls"><div class="preview-status"><span class="status-indicator" :class="cameraReady ? 'ok' : 'wait'"></span><strong>{{ cameraReady ? '摄像头画面正常' : '等待设备授权' }}</strong><small>{{ cameraReady ? '录制仅在每题开始后进行' : '视频不会在检测时保存' }}</small></div><el-button v-if="!cameraReady" class="primary-button" :loading="checking" @click="startCheck"><el-icon><VideoCamera /></el-icon>{{ state === 'error' ? '重新检测' : '开启设备检测' }}</el-button><el-button v-else class="soft-button" @click="startCheck"><el-icon><Refresh /></el-icon>重新检测</el-button></div>
      </div>

      <aside class="device-aside">
        <div class="device-status-panel panel"><div class="section-title">设备状态 <small>需要两项均可用</small></div>
          <div class="device-state-row"><div class="device-state-icon camera"><el-icon><VideoCamera /></el-icon></div><div class="device-state-copy"><strong>摄像头</strong><small>{{ cameraReady ? '权限已允许，可实时预览' : '尚未检测' }}</small></div><el-icon class="state-check" :class="{ good: cameraReady }"><CircleCheckFilled v-if="cameraReady" /><MoreFilled v-else /></el-icon></div>
          <el-select v-if="cameras.length > 1" v-model="selectedCamera" class="device-select" aria-label="选择摄像头"><el-option v-for="camera in cameras" :key="camera.deviceId" :label="camera.label" :value="camera.deviceId" /></el-select>
          <div class="device-state-row"><div class="device-state-icon mic"><el-icon><Microphone /></el-icon></div><div class="device-state-copy"><strong>麦克风</strong><small>{{ microphoneReady ? '权限已允许，录音已就绪' : '尚未检测' }}</small></div><el-icon class="state-check" :class="{ good: microphoneReady }"><CircleCheckFilled v-if="microphoneReady" /><MoreFilled v-else /></el-icon></div>
          <el-select v-if="microphones.length > 1" v-model="selectedMicrophone" class="device-select" aria-label="选择麦克风"><el-option v-for="mic in microphones" :key="mic.deviceId" :label="mic.label" :value="mic.deviceId" /></el-select>
          <div class="quality-row"><span>环境音量</span><div class="volume-meter" :class="{ active: microphoneReady }"><i v-for="n in 16" :key="n" :style="{ height: `${7 + (n * 13 % 17)}px`, animationDelay: `${n * -75}ms` }"></i></div><span>{{ microphoneReady ? '正常' : '—' }}</span></div>
        </div>
        <div class="privacy-detail"><el-icon><Lock /></el-icon><div><strong>授权仅用于本次练习</strong><p>离开检测或练习页面后会关闭设备。你可以随时在浏览器中撤销权限。</p></div></div>
        <el-button class="primary-button continue-button" :disabled="!cameraReady || !microphoneReady" @click="continueToSession">进入面试 <el-icon><ArrowRight /></el-icon></el-button>
        <button class="back-button" @click="router.push('/practice/setup')"><el-icon><ArrowLeft /></el-icon> 返回修改配置</button>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.device-layout { display: grid; grid-template-columns: minmax(0,1fr) 296px; align-items: start; gap: 17px; }.device-main { overflow: hidden; padding: 15px; box-shadow: none; }.preview-frame { position: relative; display: grid; min-height: 330px; aspect-ratio: 16 / 9; place-items: center; overflow: hidden; border-radius: 6px; background: #1b2723; color: white; }.preview-frame video { position: absolute; width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }.preview-placeholder { position: relative; display: flex; max-width: 340px; align-items: center; flex-direction: column; padding: 20px; text-align: center; }.preview-icon { display: grid; width: 43px; height: 43px; place-items: center; margin-bottom: 12px; border: 1px solid rgba(255,255,255,.17); border-radius: 50%; color: #a6c6b7; font-size: 18px; }.preview-placeholder strong { margin-bottom: 7px; font-size: 13px; font-weight: 600; }.preview-placeholder span { color: rgba(238,246,241,.62); font-size: 10px; line-height: 1.7; }.preview-corner { position: absolute; right: 13px; bottom: 12px; color: rgba(255,255,255,.63); font-size: 9px; }.preview-live { position: absolute; top: 13px; left: 13px; display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 4px; background: rgba(14,25,20,.7); font-size: 9px; }.preview-live i { width: 6px; height: 6px; border-radius: 50%; background: #75c795; }
.preview-controls { display: flex; min-height: 69px; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 3px 0; }.preview-status { display: grid; grid-template-columns: 8px 1fr; align-items: center; column-gap: 7px; }.preview-status strong { color: #49564f; font-size: 10px; font-weight: 600; }.preview-status small { grid-column: 2; margin-top: 4px; color: #929c97; font-size: 9px; }.status-indicator { grid-row: span 2; width: 7px; height: 7px; border-radius: 50%; }.status-indicator.ok { background: #5ca57b; }.status-indicator.wait { background: #d4aa55; }
.device-aside { display: grid; gap: 11px; }.device-status-panel { padding: 16px; box-shadow: none; }.device-status-panel .section-title { margin-bottom: 18px; }.device-state-row { display: flex; align-items: center; gap: 9px; }.device-state-icon { display: grid; width: 31px; height: 31px; flex: none; place-items: center; border-radius: 6px; }.device-state-icon.camera { background: var(--blue-soft); color: var(--blue); }.device-state-icon.mic { background: var(--coral-soft); color: #c97259; }.device-state-copy { display: grid; flex: 1; gap: 4px; }.device-state-copy strong { color: #46534c; font-size: 10px; }.device-state-copy small { color: #929c97; font-size: 9px; }.state-check { color: #c0c8c3; font-size: 16px; }.state-check.good { color: #5ca57b; }.device-select { width: 100%; margin: 9px 0 15px; }.device-select + .device-state-row { margin-top: 0; }.quality-row { display: flex; align-items: center; justify-content: space-between; gap: 7px; margin-top: 18px; padding-top: 13px; border-top: 1px solid #edf0ed; color: #929c97; font-size: 9px; }.volume-meter { display: flex; height: 23px; flex: 1; align-items: center; justify-content: center; gap: 2px; overflow: hidden; }.volume-meter i { width: 3px; min-height: 5px; border-radius: 3px; background: #dde4df; }.volume-meter.active i { background: #7fbb94; animation: sound 680ms ease-in-out infinite alternate; }.volume-meter.active i:nth-child(4n) { background: #e4b76c; }@keyframes sound { from { transform: scaleY(.45); } to { transform: scaleY(1); } }
.privacy-detail { display: flex; gap: 9px; padding: 13px 12px; border: 1px solid #dcece2; border-radius: 6px; background: #f0f7f2; color: var(--green); }.privacy-detail > .el-icon { flex: none; margin-top: 1px; }.privacy-detail strong { color: #46765c; font-size: 9px; }.privacy-detail p { margin: 5px 0 0; color: #738c7a; font-size: 9px; line-height: 1.6; }.continue-button { width: 100%; }.back-button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; padding: 5px; border: 0; background: transparent; color: #839089; font-size: 10px; }
@media(max-width: 900px) { .device-layout { grid-template-columns: 1fr; }.device-aside { grid-template-columns: 1fr 1fr; }.device-status-panel { grid-row: span 3; }.privacy-detail { align-self: start; }.continue-button { align-self: end; }.back-button { grid-column: 2; } }
@media(max-width: 580px) { .device-aside { grid-template-columns: 1fr; }.device-status-panel { grid-row: auto; }.back-button { grid-column: auto; }.preview-frame { min-height: 200px; }.preview-controls { align-items: flex-start; flex-direction: column; padding: 11px 3px 2px; }.preview-controls .el-button { align-self: flex-end; } }
</style>
