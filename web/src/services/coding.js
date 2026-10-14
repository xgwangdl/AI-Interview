import { apiRequest } from './api';

/** 笔试模块接口（后端 /api/v1/coding-sessions）。 */

export function getCodingCapabilities() {
  return apiRequest('/coding-sessions/capabilities');
}

export function listCodingSessions() {
  return apiRequest('/coding-sessions');
}

export function getCodingSession(id) {
  return apiRequest(`/coding-sessions/${encodeURIComponent(id)}`);
}

export function createCodingSession(payload) {
  return apiRequest('/coding-sessions', { method: 'POST', body: JSON.stringify(payload) });
}

/** 只跑可见示例，不写入历史。 */
export function runCodingAnswer(sessionId, questionId, answer) {
  return apiRequest(
    `/coding-sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(questionId)}/run`,
    { method: 'POST', body: JSON.stringify({ answer }) },
  );
}

/** 跑全部用例（含隐藏）并落库。 */
export function submitCodingAnswer(sessionId, questionId, answer) {
  return apiRequest(
    `/coding-sessions/${encodeURIComponent(sessionId)}/questions/${encodeURIComponent(questionId)}/submit`,
    { method: 'POST', body: JSON.stringify({ answer }) },
  );
}

export function getCodingReport(id) {
  return apiRequest(`/coding-sessions/${encodeURIComponent(id)}/report`);
}

export function deleteCodingSession(id) {
  return apiRequest(`/coding-sessions/${encodeURIComponent(id)}`, { method: 'DELETE' });
}
