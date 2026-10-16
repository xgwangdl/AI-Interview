import { apiRequest } from './api';

/** 简历库接口（后端 /api/v1/resumes）。 */

export function listResumes() {
  return apiRequest('/resumes');
}

export function getResume(id) {
  return apiRequest(`/resumes/${encodeURIComponent(id)}`);
}

export function createResume(payload) {
  return apiRequest('/resumes', { method: 'POST', body: JSON.stringify(payload) });
}

export function updateResume(id, payload) {
  return apiRequest(`/resumes/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(payload) });
}

export function deleteResume(id) {
  return apiRequest(`/resumes/${encodeURIComponent(id)}`, { method: 'DELETE' });
}

/** 上传文件解析出正文，不落库；先给用户过目再保存。 */
export function parseResumeFile(file) {
  const form = new FormData();
  form.append('file', file);
  return apiRequest('/resumes/parse', { method: 'POST', body: form });
}

export function optimizeResume(id, role, jobDescription) {
  return apiRequest(`/resumes/${encodeURIComponent(id)}/optimizations`, {
    method: 'POST',
    body: JSON.stringify({ role, jobDescription }),
  });
}

export function listOptimizations(id) {
  return apiRequest(`/resumes/${encodeURIComponent(id)}/optimizations`);
}

/** sections 为空数组表示全部采用。 */
export function applyOptimization(id, optimizationId, sections) {
  return apiRequest(`/resumes/${encodeURIComponent(id)}/optimizations/${encodeURIComponent(optimizationId)}/apply`, {
    method: 'POST',
    body: JSON.stringify({ sections }),
  });
}

/**
 * 从零创建：把采集到的素材交给模型整理成简历正文。
 *
 * 后端**不落库**（见 docs/RESUME-REQUIREMENTS.md §4.8）：返回的正文先给用户看和改，
 * 确认后再走 createResume 保存。什么素材都不给也能调，回来的是一份带占位符的骨架。
 */
export function draftResume(materials) {
  return apiRequest('/resumes/draft', { method: 'POST', body: JSON.stringify(materials) });
}

/** 追问补全：现有正文 + 补充信息 → 新的完整正文，同样不落库。 */
export function refineResume(rawText, supplement, questions = []) {
  return apiRequest('/resumes/refine', {
    method: 'POST',
    body: JSON.stringify({ rawText, supplement, questions }),
  });
}

/** 导出格式：后端的 `format` 参数与下载文件名后缀。 */
export const RESUME_EXPORT_FORMATS = [
  { value: 'md', label: 'Markdown (.md)', suffix: 'md' },
  { value: 'docx', label: 'Word (.docx)', suffix: 'docx' },
  { value: 'pdf', label: 'PDF (.pdf)', suffix: 'pdf' },
];

/**
 * 导出简历。
 *
 * 走 fetch 带 token 取回 Blob 再触发下载：导出接口要鉴权，
 * 直接把地址塞给 <a download> 是拿不到的（和音频播放是同一个坑）。
 */
export async function downloadResume(id, title, format = 'md') {
  const target = RESUME_EXPORT_FORMATS.find((item) => item.value === format) || RESUME_EXPORT_FORMATS[0];
  const token = localStorage.getItem('token');
  const response = await fetch(
    `/api/v1/resumes/${encodeURIComponent(id)}/export?format=${encodeURIComponent(target.value)}`,
    { headers: token ? { Authorization: `Bearer ${token}` } : {} },
  );
  if (!response.ok) {
    throw new Error(await exportError(response));
  }
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `${safeFileName(title) || 'resume'}.${target.suffix}`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

/** 后端出错时返回的是统一 JSON，尽量把 message 显示出来，而不是只给一个状态码。 */
async function exportError(response) {
  try {
    const body = await response.json();
    if (body && body.message) return `导出失败：${body.message}`;
  } catch {
    // 非 JSON 响应：退回状态码
  }
  return `导出失败（${response.status}）`;
}

function safeFileName(title) {
  return String(title || '').replace(/[\\/:*?"<>|]/g, '-').trim().slice(0, 60);
}
