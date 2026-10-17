import { apiRequest } from './api';

/**
 * 求职目标与每日计划（Phase 1）。
 *
 * 所有文案映射放在这里：目标/轮次/计划项的中文只有一处定义，页面与测试都复用它。
 */

export const STAGE_LABELS = {
  oa: '在线笔试',
  phone: '电话/初筛',
  technical: '技术面',
  system_design: '系统设计',
  hr: 'HR 面',
  final: '终面',
};

export const GOAL_STATUS_LABELS = {
  preparing: '准备中',
  applied: '已投递',
  interviewing: '面试中',
  done: '已结束',
  archived: '已归档',
};

export const PLAN_KIND_LABELS = {
  weak_dimension: '薄弱项',
  frequent: '岗位高频',
  mistake: '错题重练',
  english: '英语',
  assignment: '作业',
};

export function listGoals() {
  return apiRequest('/goals');
}

export function createGoal(payload) {
  return apiRequest('/goals', { method: 'POST', body: JSON.stringify(payload) });
}

export function getGoal(id) {
  return apiRequest(`/goals/${encodeURIComponent(id)}`);
}

export function updateGoal(id, payload) {
  return apiRequest(`/goals/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function archiveGoal(id) {
  return apiRequest(`/goals/${encodeURIComponent(id)}/archive`, { method: 'POST' });
}

export function deleteGoal(id) {
  return apiRequest(`/goals/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function listGoalSessions(id) {
  return apiRequest(`/goals/${encodeURIComponent(id)}/sessions`);
}

export function getTodayPlan(goalId = '') {
  const query = goalId ? `?goalId=${encodeURIComponent(goalId)}` : '';
  return apiRequest(`/plans/today${query}`);
}

export function updatePlanItem(id, status) {
  return apiRequest(`/plans/items/${encodeURIComponent(id)}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  });
}

export function startPlanItem(id) {
  return apiRequest(`/plans/items/${encodeURIComponent(id)}/start`, { method: 'POST' });
}

export function getStreak() {
  return apiRequest('/progress/streak');
}

/** "距面试 3 天" / "今天面试" / "已过 2 天" / "未设日期"。 */
export function interviewLabel(daysUntil) {
  if (daysUntil == null) return '未设面试日期';
  if (daysUntil === 0) return '今天面试';
  if (daysUntil > 0) return `距面试 ${daysUntil} 天`;
  return `已过 ${Math.abs(daysUntil)} 天`;
}

/** 目标卡上的进度：已完成题目 / 计划题目；没有计划时显示 0%。 */
export function goalProgressPercent(progress) {
  const total = Number(progress?.planItems) || 0;
  const done = Number(progress?.planCompleted) || 0;
  if (!total) return 0;
  return Math.min(100, Math.round((done / total) * 100));
}

/** datetime-local 值 ↔ ISO；空值保持 null。 */
export function toLocalDateTime(iso) {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function toIsoDateTime(local) {
  if (!local) return null;
  const date = new Date(local);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
