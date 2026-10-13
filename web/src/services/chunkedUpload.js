import {
  abortMediaUpload,
  completeMediaUpload,
  getMediaUploadStatus,
  tryUploadAnswerMedia,
  uploadMediaChunk,
} from './api';

/** 默认分片大小：4MB。整段录像超过这个尺寸就走分片上传。 */
export const DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024;

/** 把总字节数换算成分片计划。 */
export function planChunks(size, chunkSize = DEFAULT_CHUNK_SIZE) {
  if (!Number.isFinite(size) || size <= 0) throw new Error('录像内容为空，无法上传');
  if (chunkSize <= 0) throw new Error('分片大小必须大于 0');
  return { totalChunks: Math.max(1, Math.ceil(size / chunkSize)), chunkSize };
}

/** 计算整段媒体的 SHA-256（十六进制），服务端合并时用它校验完整性。 */
export async function sha256Hex(blob) {
  const buffer = await blob.arrayBuffer();
  const digest = await crypto.subtle.digest('SHA-256', buffer);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

/**
 * 分片上传 + 断点续传。
 *
 * 先问服务端「已经收到哪些分片」，只补传缺失的部分，因此页面刷新或网络中断后
 * 再次调用不会从零开始。全部分片到位后计算整段 sha256 并请求合并，
 * 合并成功由服务端负责落库并入队分析。
 */
export async function uploadMediaInChunks(answerId, blob, options = {}) {
  const { chunkSize = DEFAULT_CHUNK_SIZE, onProgress } = options;
  const { totalChunks } = planChunks(blob.size, chunkSize);

  const status = await getMediaUploadStatus(answerId, totalChunks);
  const received = new Set(Array.isArray(status?.receivedChunks) ? status.receivedChunks : []);
  if (onProgress) onProgress(received.size / totalChunks);

  for (let index = 0; index < totalChunks; index += 1) {
    if (received.has(index)) continue;
    const start = index * chunkSize;
    const chunk = blob.slice(start, Math.min(start + chunkSize, blob.size));
    await uploadMediaChunk(answerId, index, totalChunks, chunk);
    received.add(index);
    if (onProgress) onProgress(received.size / totalChunks);
  }

  const checksumSha256 = await sha256Hex(blob);
  return completeMediaUpload(answerId, {
    totalChunks,
    totalBytes: blob.size,
    contentType: blob.type || 'video/webm',
    checksumSha256,
  });
}

/**
 * 上传入口：小文件走原来的单段上传，大文件走分片。
 * 若后端尚未提供分片接口（404/405），退回单段上传以保持向后兼容。
 */
export async function uploadAnswerMediaWithFallback(answerId, blob, options = {}) {
  const { chunkSize = DEFAULT_CHUNK_SIZE, onProgress } = options;
  if (blob.size <= chunkSize) {
    const answer = await tryUploadAnswerMedia(answerId, blob);
    if (onProgress) onProgress(1);
    return answer;
  }
  try {
    return await uploadMediaInChunks(answerId, blob, { chunkSize, onProgress });
  } catch (error) {
    const unsupported = error?.status === 404 || error?.status === 405;
    if (!unsupported) throw error;
    const answer = await tryUploadAnswerMedia(answerId, blob);
    if (onProgress) onProgress(1);
    return answer;
  }
}

export function cancelMediaUpload(answerId, totalChunks) {
  return abortMediaUpload(answerId, totalChunks);
}
