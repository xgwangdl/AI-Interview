<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  LANGUAGE_LABELS, createSession, deleteSession, listSessions, loadOptions, pickVoice, sessionSubtitle,
} from '../services/language';

/**
 * 外语口语的入口：选语言 → 选场景 → 选水平 → 开始。
 *
 * 这里不做向导：口语练习的门槛在"敢开口"，配置每多一步就多一层劝退。
 */
const router = useRouter();
const options = ref(null);
const sessions = ref([]);
const loading = ref(true);
const starting = ref(false);
const language = ref('en');
const scenario = ref('');
const level = ref('intermediate');
const maxTurns = ref(8);
const voiceHint = ref('');

onMounted(load);

const currentLanguage = computed(() => options.value?.languages?.find((item) => item.code === language.value) || null);
const scenarios = computed(() => currentLanguage.value?.scenarios || []);
const selectedScenario = computed(() => scenarios.value.find((item) => item.id === scenario.value) || null);

async function load() {
  loading.value = true;
  try {
    options.value = await loadOptions();
    sessions.value = await listSessions();
    maxTurns.value = options.value?.defaultMaxTurns || 8;
    chooseLanguage(language.value);
  } catch (error) {
    ElMessage.error(error?.message || '无法读取外语练习配置');
  } finally {
    loading.value = false;
  }
}

function chooseLanguage(code) {
  language.value = code;
  const list = options.value?.languages?.find((item) => item.code === code)?.scenarios || [];
  scenario.value = list[0]?.id || '';
  checkVoice();
}

/**
 * 日语没有服务端音色（DashScope 现有音色会把日语按中文念），只能靠浏览器语音。
 * 先告诉用户本机有没有，别让人点进去才发现听不到。
 */
function checkVoice() {
  voiceHint.value = '';
  const spec = currentLanguage.value;
  if (!spec || spec.serverVoice) return;
  if (typeof window === 'undefined' || !window.speechSynthesis) {
    voiceHint.value = '这台设备没有语音合成能力：AI 的话只能看文字。';
    return;
  }
  const voices = window.speechSynthesis.getVoices();
  if (voices.length && !pickVoice(voices, spec.browserVoice)) {
    voiceHint.value = '本机没有日语语音：AI 的话会显示成文字。用 Edge 或在系统中安装日语语音包就能听到。';
  }
}

async function start() {
  if (!scenario.value || starting.value) return;
  starting.value = true;
  try {
    const session = await createSession({
      language: language.value,
      scenario: scenario.value,
      level: level.value,
      maxTurns: maxTurns.value,
    });
    router.push(`/language/${session.id}`);
  } catch (error) {
    ElMessage.error(error?.message || '无法开始这次练习');
  } finally {
    starting.value = false;
  }
}

function open(session) {
  router.push(`/language/${session.id}`);
}

async function remove(session) {
  try {
    await ElMessageBox.confirm('删除后这段对话的录音与复盘都会清除。', '删除这次练习？', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await deleteSession(session.id);
    sessions.value = sessions.value.filter((item) => item.id !== session.id);
  } catch (error) {
    ElMessage.error(error?.message || '删除失败');
  }
}

function scenarioTitle(session) {
  return options.value?.languages
    ?.find((item) => item.code === session.language)?.scenarios
    ?.find((item) => item.id === session.scenario)?.title || session.scenario;
}
</script>

<template>
  <section v-loading="loading" class="language-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">SPEAKING PRACTICE</div>
        <h1>外语口语练习</h1>
        <p>选一个场景，和 AI 一句一句聊。说完先给回复，再指出这一句怎么说得更好。</p>
      </div>
      <el-tag class="tag-muted">L1 轮次对话 · 每轮 2-4 秒</el-tag>
    </div>

    <section v-if="options" class="panel setup-panel">
      <div class="field-row">
        <span class="field-label">语言</span>
        <el-radio-group :model-value="language" class="language-tabs" @change="chooseLanguage">
          <el-radio-button v-for="item in options.languages" :key="item.code" :value="item.code">
            {{ item.displayName }}<small>{{ LANGUAGE_LABELS[item.code] }}</small>
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="field-row">
        <span class="field-label">场景</span>
        <div class="scenario-grid">
          <button v-for="item in scenarios" :key="item.id" class="scenario-card"
                  :class="{ active: scenario === item.id }" @click="scenario = item.id">
            <strong>{{ item.title }}</strong>
            <span>{{ item.hint }}</span>
          </button>
        </div>
      </div>

      <div class="field-row compact">
        <span class="field-label">水平</span>
        <el-select v-model="level" class="small-select">
          <el-option v-for="item in options.levels" :key="item.key" :label="item.label" :value="item.key" />
        </el-select>
        <span class="field-label second">轮数</span>
        <el-select v-model="maxTurns" class="small-select">
          <el-option v-for="count in [4, 8, 12]" :key="count" :label="`${count} 轮`" :value="count" />
        </el-select>
      </div>

      <p v-if="selectedScenario" class="goal-line">这一场的目标：{{ selectedScenario.goal }}</p>
      <p v-if="voiceHint" class="voice-hint"><el-icon><WarningFilled /></el-icon>{{ voiceHint }}</p>

      <el-button class="primary-button start-button" :loading="starting" @click="start">
        <el-icon><Microphone /></el-icon> 开始对话
      </el-button>
    </section>

    <section class="history-section">
      <div class="section-title"><span>练习记录</span><small>录音与文字保留 7 天</small></div>
      <div v-if="sessions.length" class="panel history-list">
        <button v-for="item in sessions" :key="item.id" class="history-row" @click="open(item)">
          <span class="history-lang">{{ LANGUAGE_LABELS[item.language] || item.language }}</span>
          <span class="history-main">
            <strong>{{ scenarioTitle(item) }}</strong>
            <small>{{ sessionSubtitle(item) }}</small>
          </span>
          <span class="history-date">{{ new Date(item.createdAt).toLocaleDateString('zh-CN') }}</span>
          <el-icon class="history-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
      <div v-else class="panel history-empty">
        <strong>还没有外语练习</strong>
        <p>选一个场景，先说第一句。每轮都会给出回复和一句"更自然的说法"。</p>
      </div>
    </section>
  </section>
</template>

<style scoped>
.language-page { max-width: 1080px; }
.setup-panel { display: grid; gap: 18px; padding: 20px 22px 22px; box-shadow: none; }
.field-row { display: flex; align-items: flex-start; gap: 14px; }
.field-row.compact { align-items: center; }
.field-label { width: 44px; flex: none; padding-top: 9px; color: #7c8782; font-size: 13px; }
.field-label.second { width: auto; padding-top: 0; margin-left: 10px; }
.language-tabs :deep(.el-radio-button__inner) { display: grid; gap: 2px; padding: 9px 18px; }
.language-tabs small { color: #96a09a; font-size: 11px; }
.scenario-grid { display: grid; flex: 1; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.scenario-card { display: grid; gap: 6px; padding: 12px 13px; border: 1px solid var(--line); border-radius: 8px; background: white; text-align: left; }
.scenario-card strong { color: #34403a; font-size: 14px; }
.scenario-card span { color: #8d9792; font-size: 12px; line-height: 1.7; }
.scenario-card.active { border-color: #9dc7ac; background: #f5faf6; }
.small-select { width: 130px; }
.goal-line { margin: 0; padding: 10px 12px; border-radius: 6px; background: #f4f7f5; color: #5d6a64; font-size: 13px; }
.voice-hint { display: flex; align-items: center; gap: 6px; margin: 0; color: #a9722f; font-size: 12px; line-height: 1.7; }
.start-button { justify-self: start; }
.history-section { margin-top: 26px; }
.history-list { overflow: hidden; box-shadow: none; }
.history-row { display: flex; width: 100%; align-items: center; gap: 14px; padding: 12px 16px; border: 0; border-bottom: 1px solid #eef1ee; background: white; text-align: left; }
.history-row:last-child { border-bottom: 0; }
.history-row:hover { background: #fafcfb; }
.history-lang { width: 70px; flex: none; color: var(--green); font-size: 13px; font-weight: 600; }
.history-main { display: grid; min-width: 0; flex: 1; gap: 4px; }
.history-main strong { color: #34403a; font-size: 14px; }
.history-main small { color: #98a29d; font-size: 12px; }
.history-date { color: #98a19d; font-size: 12px; }
.history-arrow { color: #abb4af; }
.history-empty { padding: 28px; text-align: center; box-shadow: none; }
.history-empty strong { color: #46524c; font-size: 14px; }
.history-empty p { margin: 8px 0 0; color: #8d9792; font-size: 13px; }
@media (max-width: 760px) {
  .field-row { flex-direction: column; }
  .scenario-grid { grid-template-columns: 1fr; }
  .start-button { justify-self: stretch; }
}
</style>
