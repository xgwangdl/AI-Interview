<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  LANGUAGE_LABELS, LEVEL_LABEL_KEYS, createSession, deleteSession, listSessions, loadOptions, pickVoice, sessionSubtitle,
} from '../services/language';
import { useI18n } from 'vue-i18n';

/**
 * 外语口语的入口：选语言 → 选场景 → 选水平 → 开始。
 *
 * 这里不做向导：口语练习的门槛在"敢开口"，配置每多一步就多一层劝退。
 */
const router = useRouter();
const { t, locale } = useI18n();
const options = ref(null);
const sessions = ref([]);
const loading = ref(true);
const starting = ref(false);
const language = ref('en');
const scenario = ref('');
const level = ref('intermediate');
const maxTurns = ref(8);
const voiceHint = ref('');
// 声音偏好：系统语音（浏览器，快）默认；服务端音色更统一，但要多等约 2 秒。
const voiceMode = ref(localStorage.getItem('language-voice-mode') || 'server');

onMounted(load);

const currentLanguage = computed(() => options.value?.languages?.find((item) => item.code === language.value) || null);
const scenarios = computed(() => currentLanguage.value?.scenarios || []);
const selectedScenario = computed(() => scenarios.value.find((item) => item.id === scenario.value) || null);

async function load() {
  loading.value = true;
  try {
    options.value = await loadOptions();
    maxTurns.value = options.value?.defaultMaxTurns || 8;
    chooseLanguage(language.value);
  } catch (error) {
    ElMessage.error(error?.message || t('language.loadFailed'));
  } finally {
    loading.value = false;
  }
  // 历史读不到不该挡住"开始新练习"：语言下线后旧会话会被后端跳过，
  // 但网络抖动等情况也不应该把整个配置页变成错误页。
  try {
    sessions.value = await listSessions();
  } catch {
    sessions.value = [];
  }
}

function chooseLanguage(code) {
  language.value = code;
  const list = options.value?.languages?.find((item) => item.code === code)?.scenarios || [];
  scenario.value = list[0]?.id || '';
  checkVoice();
}

function changeVoiceMode(value) {
  voiceMode.value = value;
  localStorage.setItem('language-voice-mode', value);
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
    voiceHint.value = t('language.noSpeech');
    return;
  }
  const voices = window.speechSynthesis.getVoices();
  if (voices.length && !pickVoice(voices, spec.browserVoice)) {
    voiceHint.value = t('language.noJapaneseVoice');
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
    ElMessage.error(error?.message || t('language.startFailed'));
  } finally {
    starting.value = false;
  }
}

function open(session) {
  router.push(`/language/${session.id}`);
}

async function remove(session) {
  try {
    await ElMessageBox.confirm(t('language.deleteConfirmBody'), t('language.deleteConfirmTitle'), {
      confirmButtonText: t('language.deleteAction'), cancelButtonText: t('common.cancel'), type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await deleteSession(session.id);
    sessions.value = sessions.value.filter((item) => item.id !== session.id);
  } catch (error) {
    ElMessage.error(error?.message || t('language.deleteFailed'));
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
        <div class="eyebrow">{{ t('language.eyebrow') }}</div>
        <h1>{{ t('language.title') }}</h1>
        <p>{{ t('language.subtitle') }}</p>
      </div>
      <el-tag class="tag-muted">{{ t('language.modeTag') }}</el-tag>
    </div>

    <section v-if="options" class="panel setup-panel">
      <div class="field-row">
        <span class="field-label">{{ t('language.language') }}</span>
        <el-radio-group v-if="options.languages.length > 1" :model-value="language" class="language-tabs" @change="chooseLanguage">
          <el-radio-button v-for="item in options.languages" :key="item.code" :value="item.code">
            {{ item.displayName }}<small>{{ LANGUAGE_LABELS[item.code] }}</small>
          </el-radio-button>
        </el-radio-group>
        <strong v-else class="single-language">{{ currentLanguage?.displayName }}</strong>
      </div>

      <div class="field-row">
        <span class="field-label">{{ t('language.scenario') }}</span>
        <div class="scenario-grid">
          <button v-for="item in scenarios" :key="item.id" class="scenario-card"
                  :class="{ active: scenario === item.id }" @click="scenario = item.id">
            <strong>{{ item.title }}</strong>
            <span>{{ item.hint }}</span>
          </button>
        </div>
      </div>

      <div class="field-row compact">
        <span class="field-label">{{ t('language.levelLabel') }}</span>
        <el-select v-model="level" class="small-select">
          <el-option v-for="item in options.levels" :key="item.key" :label="LEVEL_LABEL_KEYS[item.key] ? t(LEVEL_LABEL_KEYS[item.key]) : item.label" :value="item.key" />
        </el-select>
        <span class="field-label second">{{ t('language.turns') }}</span>
        <el-select v-model="maxTurns" class="small-select">
          <el-option v-for="count in [4, 8, 12]" :key="count" :label="t('language.turnCount', { count })" :value="count" />
        </el-select>
        <span class="field-label second">{{ t('language.voice') }}</span>
        <el-select :model-value="voiceMode" class="small-select wide" @change="changeVoiceMode">
          <el-option :label="t('language.browserVoice')" value="browser" />
          <el-option :label="t('language.serverVoice')" value="server" />
        </el-select>
      </div>

      <p v-if="selectedScenario" class="goal-line">{{ t('language.scenarioGoal', { goal: selectedScenario.goal }) }}</p>
      <p v-if="voiceHint" class="voice-hint"><el-icon><WarningFilled /></el-icon>{{ voiceHint }}</p>

      <el-button class="primary-button start-button" :loading="starting" @click="start">
        <el-icon><Microphone /></el-icon> {{ t('language.start') }}
      </el-button>
    </section>

    <section class="history-section">
      <div class="section-title"><span>{{ t('language.history') }}</span><small>{{ t('language.retention') }}</small></div>
      <div v-if="sessions.length" class="panel history-list">
        <button v-for="item in sessions" :key="item.id" class="history-row" @click="open(item)">
          <span class="history-lang">{{ LANGUAGE_LABELS[item.language] || item.language }}</span>
          <span class="history-main">
            <strong>{{ scenarioTitle(item) }}</strong>
            <small>{{ sessionSubtitle(item, t) }}</small>
          </span>
          <span class="history-date">{{ new Date(item.createdAt).toLocaleDateString(locale) }}</span>
          <el-icon class="history-arrow"><ArrowRight /></el-icon>
        </button>
      </div>
      <div v-else class="panel history-empty">
        <strong>{{ t('language.empty') }}</strong>
        <p>{{ t('language.emptyHint') }}</p>
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
.small-select.wide { width: 190px; }
.single-language { padding-top: 9px; color: #34403a; font-size: 15px; }
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
