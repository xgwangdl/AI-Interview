import { apiRequest } from './api';

/**
 * 今日 AI 额度。
 *
 * 后端按账号统计"会花钱的调用"（出题 / 追问 / 评分 / 判分 / 简历优化 / 语音合成），
 * 前端把剩余额度显示出来：额度是共享资源，用户至少该知道自己还剩多少，
 * 而不是练到一半突然被 429 拦住。
 */

export function loadModelUsage() {
  return apiRequest('/me/model-usage');
}

export function buildUsage(usage) {
  if (!usage || usage.enabled === false) {
    return { enabled: false, label: '', note: '', ratio: 0, exhausted: false, tight: false };
  }
  const limit = Number(usage.dailyLimit) || 0;
  const used = Number(usage.used) || 0;
  const remaining = Number.isFinite(Number(usage.remaining)) ? Number(usage.remaining) : Math.max(0, limit - used);
  const ratio = limit > 0 ? Math.min(1, Math.max(0, used / limit)) : 0;

  return {
    enabled: true,
    label: `今日 AI 额度 ${remaining}/${limit}`,
    note: usage.note || '',
    resetLabel: resetLabel(usage.resetsAt),
    ratio,
    used,
    limit,
    remaining,
    /** 用完 / 快用完：顶栏换成警示色，用户不用点开就知道该省着用了 */
    exhausted: remaining <= 0,
    tight: remaining > 0 && remaining <= Math.max(1, Math.ceil(limit * 0.1)),
  };
}

function resetLabel(resetsAt) {
  if (!resetsAt) return '';
  const date = new Date(resetsAt);
  if (Number.isNaN(date.getTime())) return '';
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  return `${hours}:${minutes} 重置`;
}
