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

export function requestHelp(cohortId, payload) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/help`, {
    method: 'POST', body: JSON.stringify(payload),
  });
}

export function listHelp(cohortId, status = '') {
  const query = status ? `?status=${encodeURIComponent(status)}` : '';
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/help${query}`);
}

export function sendHelpFeedback(cohortId, helpId, text) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/help/${encodeURIComponent(helpId)}/feedback`, {
    method: 'POST', body: JSON.stringify({ text }),
  });
}

export function optInPeerReview(cohortId, enabled) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/members/me/peer-review`, {
    method: 'PUT', body: JSON.stringify({ enabled }),
  });
}

export function listPeerReviewTasks(cohortId, limit = 5) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/peer-reviews/tasks?limit=${encodeURIComponent(limit)}`);
}

export function submitPeerReview(cohortId, payload) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/peer-reviews`, {
    method: 'POST', body: JSON.stringify(payload),
  });
}

export function listReceivedPeerReviews(cohortId) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/peer-reviews/received`);
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

export function updateAssignment(cohortId, assignmentId, payload) {
  return apiRequest(`/cohorts/${encodeURIComponent(cohortId)}/assignments/${encodeURIComponent(assignmentId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function completionPercent(item) {
  if (!item?.myTotal) return 0;
  return Math.min(100, Math.round((item.myCompleted / item.myTotal) * 100));
}

export function dueLabel(value, t = null, locale = 'zh-CN') {
  if (!value) return t ? t('cohorts.noDue') : '未设截止时间';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return t ? t('cohorts.noDue') : '未设截止时间';
  return new Intl.DateTimeFormat(locale, { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
    .format(date);
}

export function roleLabel(role, t = null) {
  if (t) return role === 'coach' ? t('cohorts.coach') : t('cohorts.learner');
  return role === 'coach' ? '助教' : '学员';
}
