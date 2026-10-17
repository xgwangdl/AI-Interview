import { apiRequest } from './api';

export function listCohorts() {
  return apiRequest('/cohorts');
}

export function createCohort(name) {
  return apiRequest('/cohorts', { method: 'POST', body: JSON.stringify({ name }) });
}

export function joinCohort(inviteCode) {
  return apiRequest('/cohorts/join', { method: 'POST', body: JSON.stringify({ inviteCode }) });
}

export function getCohort(id) {
  return apiRequest(`/cohorts/${encodeURIComponent(id)}`);
}

export function setScoreSharing(id, shareScores) {
  return apiRequest(`/cohorts/${encodeURIComponent(id)}/members/me/score-sharing`, {
    method: 'PUT',
    body: JSON.stringify({ shareScores }),
  });
}

export function archiveCohort(id) {
  return apiRequest(`/cohorts/${encodeURIComponent(id)}/archive`, { method: 'POST' });
}

export function listAssignments(id) {
  return apiRequest(`/cohorts/${encodeURIComponent(id)}/assignments`);
}

export function createAssignment(id, payload) {
  return apiRequest(`/cohorts/${encodeURIComponent(id)}/assignments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function completionPercent(item) {
  if (!item?.myTotal) return 0;
  return Math.min(100, Math.round((item.myCompleted / item.myTotal) * 100));
}

export function dueLabel(value) {
  if (!value) return '未设截止时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '未设截止时间';
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    .format(date);
}

export function roleLabel(role) {
  return role === 'coach' ? '助教' : '学员';
}
