import { apiRequest } from './api';

/**
 * 外语口语练习（L1 轮次对话）。
 *
 * 与练习/简历一样，能单独测的逻辑都放这里：语言与状态的文案、浏览器语音怎么选、
 * 一轮请求的 FormData、报告怎么排。组件只负责画。
 */

export const LEVEL_LABELS = { beginner: '初级', intermediate: '中级', advanced: '高级' };
export const STATUS_LABELS = { active: '进行中', finished: '已完成', abandoned: '已放弃' };
export const LANGUAGE_LABELS = { en: 'English', ja: '日本語' };
export const LEVEL_LABEL_KEYS = Object.fromEntries(
  Object.keys(LEVEL_LABELS).map((key) => [key, `language.level.${key}`]),
);
export const STATUS_LABEL_KEYS = Object.fromEntries(
  Object.keys(STATUS_LABELS).map((key) => [key, `language.status.${key}`]),
);

export const REPORT_DIMENSIONS = [
  { key: 'fluency', label: '流利度' },
  { key: 'vocabulary', label: '用词' },
  { key: 'grammar', label: '语法' },
  { key: 'task', label: '任务完成' },
];
export const REPORT_DIMENSION_KEYS = Object.fromEntries(
  REPORT_DIMENSIONS.map((item) => [item.key, `language.dimensions.${item.key}`]),
);

export function loadOptions() {
  return apiRequest('/language-options');
}

export function createSession(payload) {
  return apiRequest('/language-sessions', { method: 'POST', body: JSON.stringify(payload) });
}

export function listSessions() {
  return apiRequest('/language-sessions');
}

export function getSession(id) {
  return apiRequest(`/language-sessions/${encodeURIComponent(id)}`);
}

export function deleteSession(id) {
  return apiRequest(`/language-sessions/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function finishSession(id) {
  return apiRequest(`/language-sessions/${encodeURIComponent(id)}/finish`, { method: 'POST' });
}

/** 手打一轮：识别不可用时的降级入口，走同一套回复与反馈。 */
export function submitTextTurn(id, text) {
  return apiRequest(`/language-sessions/${encodeURIComponent(id)}/turns/text`, {
    method: 'POST',
    body: JSON.stringify({ text }),
  });
}

/**
 * 一轮录音。
 *
 * 音频原样上传（webm/ogg），由后端 ffmpeg 转 16k wav 再识别 —— 前端不做转码，
 * 免得为了一个格式问题去引入 wasm 解码器。
 */
export function submitAudioTurn(id, blob, durationMs) {
  const form = new FormData();
  form.append('audio', blob, `turn-${Date.now()}.webm`);
  if (Number.isFinite(durationMs)) form.append('durationMs', String(Math.round(durationMs)));
  return apiRequest(`/language-sessions/${encodeURIComponent(id)}/turns`, { method: 'POST', body: form });
}

/** 回放音频要带 token：audio 标签自己发请求不会带 Authorization（与面试官配音同一个坑）。 */
export async function fetchAudio(url) {
  const token = localStorage.getItem('token');
  const response = await fetch(url, { headers: token ? { Authorization: `Bearer ${token}` } : {} });
  if (!response.ok) throw new Error(`无法读取这一段音频（${response.status}）`);
  return response.blob();
}

/**
 * 从浏览器的语音列表里挑一个目标语言的音色。
 *
 * 先找完全匹配（ja-JP），再找同语言（ja-*），最后不将就 —— 拿英语音色念日语比没有声音更糟。
 */
export function pickVoice(voices, lang) {
  const list = Array.isArray(voices) ? voices : [];
  if (!lang) return null;
  const lower = lang.toLowerCase();
  const base = lower.split('-')[0];
  return list.find((voice) => (voice.lang || '').toLowerCase() === lower)
    || list.find((voice) => (voice.lang || '').toLowerCase().startsWith(`${base}-`))
    || list.find((voice) => (voice.lang || '').toLowerCase() === base)
    || null;
}

/** 报告 → 页面用的 4 行分数（缺分显示 —，不拿 0 分冒充）。 */
export function reportRows(report, t = null) {
  return REPORT_DIMENSIONS.map((dimension) => ({
    key: dimension.key,
    label: t ? t(REPORT_DIMENSION_KEYS[dimension.key]) : dimension.label,
    value: typeof report?.[dimension.key] === 'number' ? report[dimension.key] : null,
    display: typeof report?.[dimension.key] === 'number' ? String(report[dimension.key]) : '—',
  }));
}

/** 每轮的耗时，给"为什么这一轮等了这么久"一个说法。 */
export function latencyLabel(latency, t = null) {
  if (!latency) return '';
  const parts = [];
  if (latency.asrMs != null) parts.push(t ? t('languageSession.latencyAsr', { time: seconds(latency.asrMs) }) : `听写 ${seconds(latency.asrMs)}`);
  // L2 的关键是"多快看到字、多快听到第一句"，总时长反而次要
  if (latency.llmFirstMs != null) parts.push(t ? t('languageSession.latencyFirstToken', { time: seconds(latency.llmFirstMs) }) : `首字 ${seconds(latency.llmFirstMs)}`);
  if (latency.audioFirstMs != null) parts.push(t ? t('languageSession.latencyAudio', { time: seconds(latency.audioFirstMs) }) : `出声 ${seconds(latency.audioFirstMs)}`);
  if (latency.llmMs != null) parts.push(t ? t('languageSession.latencyReply', { time: seconds(latency.llmMs) }) : `回复 ${seconds(latency.llmMs)}`);
  if (latency.ttsMs != null) parts.push(t ? t('languageSession.latencyTts', { time: seconds(latency.ttsMs) }) : `配音 ${seconds(latency.ttsMs)}`);
  return parts.join(' · ');
}

function seconds(ms) {
  return `${(Math.max(0, Number(ms)) / 1000).toFixed(1)}s`;
}

/** 会话列表 / 详情都会用到的中文副标题。 */
export function sessionSubtitle(session, t = null) {
  const level = t && LEVEL_LABEL_KEYS[session?.level]
    ? t(LEVEL_LABEL_KEYS[session.level])
    : LEVEL_LABELS[session?.level] || '中级';
  const status = t && STATUS_LABEL_KEYS[session?.status]
    ? t(STATUS_LABEL_KEYS[session.status])
    : STATUS_LABELS[session?.status] || session?.status || '';
  return t
    ? t('language.sessionSubtitle', { level, current: session?.turnCount || 0, total: session?.maxTurns || 0, status })
    : `${level} · ${session?.turnCount || 0}/${session?.maxTurns || 0} 轮 · ${status}`;
}
