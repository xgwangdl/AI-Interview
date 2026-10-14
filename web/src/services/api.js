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

/**
 * 登录与注册走根路径（不是 /api/v1），所以单独封装：
 * 两者都返回 { token, userId, userName }，失败时抛出后端给的 message。
 */
async function postCredentials(path, payload, fallbackMessage) {
  const response = await fetch(path, { method: 'POST', body: new URLSearchParams(payload) });
  const result = await response.json().catch(() => ({}));
  if (!response.ok || result.code !== 200) {
    throw new Error(result.message || fallbackMessage);
  }
  // 接口成功但没给 token 属于异常响应，此时 result.message 通常是 "ok"，
  // 直接用它会误导用户，所以退回本地的兜底文案。
  if (!result.data?.token) throw new Error(fallbackMessage);
  return result.data;
}

export function tryLogin(name, code) {
  return postCredentials('/login', { name, code }, '用户名或密码不正确。');
}

export function tryRegister(name, code, email) {
  const payload = email ? { name, code, email } : { name, code };
  return postCredentials('/register', payload, '注册失败，请换一个用户名。');
}

/** 个人偏好（目前只有主题色）。 */
export function getPreferences() {
  return apiRequest('/me/preferences');
}

export function savePreferences(payload) {
  return apiRequest('/me/preferences', { method: 'PUT', body: JSON.stringify(payload) });
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

// --- 分片上传（#12）：大文件断点续传，见 services/chunkedUpload.js ---

export function getMediaUploadStatus(answerId, totalChunks) {
  return apiRequest(`/answers/${encodeURIComponent(answerId)}/media/upload?totalChunks=${totalChunks}`);
}

export function uploadMediaChunk(answerId, index, totalChunks, chunk) {
  return apiRequest(`/answers/${encodeURIComponent(answerId)}/media/chunks/${index}?totalChunks=${totalChunks}`, {
    method: 'PUT',
    body: chunk,
    headers: { 'Content-Type': 'application/octet-stream' },
  });
}

export function completeMediaUpload(answerId, payload) {
  return apiRequest(`/answers/${encodeURIComponent(answerId)}/media/merge`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function abortMediaUpload(answerId, totalChunks) {
  return apiRequest(`/answers/${encodeURIComponent(answerId)}/media/upload?totalChunks=${totalChunks}`, {
    method: 'DELETE',
  });
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

/**
 * 拉取面试官语音。
 *
 * 不能用 <audio src="/..."> 直接播：浏览器给 audio/video 发请求时**不会带 Authorization 头**，
 * 这个接口要求 Bearer Token，结果是 401 → audio 触发 error → 前端只能显示「语音暂不可用」。
 * 所以这里用 fetch 带 token 取回 Blob，再交给 audio 播放。
 */
export async function getNarrationAudio(sessionId, questionIndex) {
  const token = localStorage.getItem('token');
  const response = await fetch(
    `${apiBase}/api/v1/practice-sessions/${encodeURIComponent(sessionId)}/questions/${questionIndex}/narration/audio`,
    { headers: token ? { Authorization: `Bearer ${token}` } : {} },
  );
  if (!response.ok) {
    const error = new Error(response.status === 503 ? '语音合成暂不可用' : `无法获取面试官语音（${response.status}）`);
    error.status = response.status;
    throw error;
  }
  return response.blob();
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
