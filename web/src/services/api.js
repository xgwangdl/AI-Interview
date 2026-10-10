const apiBase = import.meta.env.VITE_API_BASE_URL || '';

export async function apiRequest(path, options = {}) {
  const token = localStorage.getItem('token');
  const response = await fetch(`${apiBase}/api/v1${path}`, {
    ...options,
    headers: { ...(options.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers },
  });
  if (!response.ok) {
    const body = await response.text();
    // 后端统一返回 {code, message, data}：取出 message，避免把整段 JSON 当作错误消息显示给用户。
    let message = body;
    try {
      const parsed = JSON.parse(body);
      if (parsed && typeof parsed.message === 'string' && parsed.message) message = parsed.message;
    } catch {
      // 非 JSON 响应（例如代理层报错），保留原文。
    }
    const error = new Error(message || `请求失败 (${response.status})`);
    error.status = response.status;
    throw error;
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

export function tryCompleteAnswer(answerId) {
  return apiRequest(`/answers/${encodeURIComponent(answerId)}/complete`, { method: 'POST' });
}

export function tryCompleteSession(id) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(id)}/complete`, { method: 'POST' });
}

export function getPracticeReport(sessionId) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(sessionId)}/report`);
}

export function retryPracticeAnalysis(sessionId) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(sessionId)}/analysis/retry`, { method: 'POST' });
}

export function getQuestionNarration(sessionId, questionIndex) {
  return apiRequest(`/practice-sessions/${encodeURIComponent(sessionId)}/questions/${questionIndex}/narration`);
}

export function planQuestions(payload) {
  return apiRequest('/practice-sessions/question-plan', { method: 'POST', body: JSON.stringify(payload) });
}

export function extractResume(file) {
  const form = new FormData();
  form.append('file', file, file.name);
  return apiRequest('/resume/extract', { method: 'POST', body: form });
}

export async function getAnswerMedia(answerId) {
  const token = localStorage.getItem('token');
  const response = await fetch(`${apiBase}/api/v1/answers/${encodeURIComponent(answerId)}/media`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw new Error('无法读取这段视频');
  return response.blob();
}
