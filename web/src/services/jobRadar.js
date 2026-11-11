import { apiRequest } from './api';

export const ACTIVE_RUN_STATUSES = new Set(['pending', 'running', 'deferred']);
export const JOB_STATUSES = [
  'new', 'evaluating', 'shortlisted', 'applied', 'interviewing', 'offer', 'rejected', 'skipped', 'archived',
];
export const QUESTION_CATEGORIES = [
  'project_depth', 'system_design', 'algorithm', 'behavioral', 'domain', 'other',
];

export function isRunActive(status) {
  return ACTIVE_RUN_STATUSES.has(status);
}

export function buildQuery(params = {}) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : '';
}

export function listJobLeads(params = {}) {
  return apiRequest(`/job-leads${buildQuery(params)}`);
}

export function getJobLead(id) {
  return apiRequest(`/job-leads/${encodeURIComponent(id)}`);
}

export function createJobLead(payload) {
  return apiRequest('/job-leads', { method: 'POST', body: JSON.stringify(payload) });
}

export function patchJobLead(id, payload) {
  return apiRequest(`/job-leads/${encodeURIComponent(id)}`, { method: 'PATCH', body: JSON.stringify(payload) });
}

export function evaluateJobLead(id, force = false) {
  return apiRequest(`/job-leads/${encodeURIComponent(id)}/evaluate`, {
    method: 'POST', body: JSON.stringify({ force }),
  });
}

export async function deleteJobLead(id) {
  await apiRequest(`/job-leads/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function createPracticeFromJob(id, payload) {
  return apiRequest(`/job-leads/${encodeURIComponent(id)}/practice`, {
    method: 'POST', body: JSON.stringify(payload),
  });
}

export function getSkillProfile() {
  return apiRequest('/me/skill-profile');
}

export function updateSkillProfile(payload) {
  return apiRequest('/me/skill-profile', { method: 'PUT', body: JSON.stringify(payload) });
}

export function extractSkillProfile(resumeText) {
  return apiRequest('/me/skill-profile/extract', {
    method: 'POST', body: JSON.stringify({ resumeText }),
  });
}

export function listDebriefs(params = {}) {
  return apiRequest(`/interview-debriefs${buildQuery(params)}`);
}

export function getDebrief(id) {
  return apiRequest(`/interview-debriefs/${encodeURIComponent(id)}`);
}

export function createDebrief(payload) {
  return apiRequest('/interview-debriefs', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateDebriefQuestions(id, questions) {
  return apiRequest(`/interview-debriefs/${encodeURIComponent(id)}/questions`, {
    method: 'PATCH', body: JSON.stringify({ questions }),
  });
}

export async function deleteDebrief(id) {
  await apiRequest(`/interview-debriefs/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function listWeakPoints(params = {}) {
  return apiRequest(`/training/weak-points${buildQuery(params)}`);
}

export function closeWeakPoint(id) {
  return apiRequest(`/training/weak-points/${encodeURIComponent(id)}/close`, { method: 'POST' });
}

export function getWorkReport(period = 'week') {
  return apiRequest(`/agent/work-report${buildQuery({ period })}`);
}

export function getAgentRuns(limit = 20) {
  return apiRequest(`/agent/runs${buildQuery({ limit })}`);
}
