const apiBase = import.meta.env.VITE_API_BASE_URL || '';

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem('token');
  const response = await fetch(`${apiBase}/api/v1${path}`, {
    ...options,
    headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers },
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `请求失败 (${response.status})`);
  }
  if (response.status === 204) return null;
  return response.json();
}

export function trySyncSession(session) {
  return apiRequest('/practice-sessions', {
    method: 'POST',
    body: JSON.stringify({
      role: session.role,
      company: session.company,
      jobDescription: session.jobDescription,
      resumeName: session.resumeName,
      interviewType: session.interviewType,
      difficulty: session.difficulty,
      interviewerStyle: session.interviewerStyle,
      questionCount: session.questionCount,
      durationMinutes: session.durationMinutes,
      questions: session.questions,
    }),
  });
}

export function tryDeleteSession(id) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

export function getSessions() {
  return apiRequest('/practice-sessions');
}

export function tryStartSession(id) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(id)}/start`, { method: 'POST' });
}

export function trySaveAnswer(id, answer) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(id)}/answers`, {
    method: 'POST',
    body: JSON.stringify({
      questionIndex: answer.questionIndex,
      questionId: answer.questionId,
      durationSeconds: answer.durationSeconds,
      startedAtOffsetSeconds: answer.startedAtOffset,
    }),
  });
}

export function tryUploadAnswerMedia(answerId, blob) {
  const form = new FormData();
  form.append('file', blob, `${answerId}.webm`);
  return apiRequest(`/answers/${encodeURIComponent(answerId)}/media`, { method: 'POST', body: form });
}

export function tryCompleteSession(id) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(id)}/complete`, { method: 'POST' });
}

export async function getAnswerMedia(answerId) {
  const token = localStorage.getItem('token');
  const response = await fetch(`${apiBase}/api/v1/answers/${encodeURIComponent(answerId)}/media`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw new Error('无法读取这段视频');
  return response.blob();
}
