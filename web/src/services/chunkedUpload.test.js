import { createHash } from 'node:crypto';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  DEFAULT_CHUNK_SIZE,
  planChunks,
  uploadAnswerMediaWithFallback,
  uploadMediaInChunks,
} from './chunkedUpload';
import {
  completeMediaUpload,
  getMediaUploadStatus,
  tryUploadAnswerMedia,
  uploadMediaChunk,
} from './api';

vi.mock('./api', () => ({
  getMediaUploadStatus: vi.fn(),
  uploadMediaChunk: vi.fn(),
  completeMediaUpload: vi.fn(),
  abortMediaUpload: vi.fn(),
  tryUploadAnswerMedia: vi.fn(),
}));

const ANSWER_ID = 'answer-1';

function blobOf(text) {
  return new Blob([text], { type: 'video/webm' });
}

function sha256Of(text) {
  return createHash('sha256').update(text).digest('hex');
}

describe('planChunks', () => {
  it('按分片大小向上取整', () => {
    expect(planChunks(10, 4).totalChunks).toBe(3);
    expect(planChunks(8, 4).totalChunks).toBe(2);
    expect(planChunks(1, DEFAULT_CHUNK_SIZE).totalChunks).toBe(1);
  });

  it('拒绝空内容', () => {
    expect(() => planChunks(0)).toThrow('录像内容为空');
  });
});

describe('uploadMediaInChunks', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    uploadMediaChunk.mockResolvedValue({ receivedChunks: [] });
    completeMediaUpload.mockResolvedValue({ id: ANSWER_ID });
  });

  it('只补传缺失的分片，并用整段 sha256 触发合并', async () => {
    getMediaUploadStatus.mockResolvedValue({ totalChunks: 3, receivedChunks: [1], readyToMerge: false });
    const blob = blobOf('0123456789');
    const progress = [];

    await uploadMediaInChunks(ANSWER_ID, blob, { chunkSize: 4, onProgress: (p) => progress.push(p) });

    // 第 1 片服务端已有，只补 0 与 2
    expect(uploadMediaChunk).toHaveBeenCalledTimes(2);
    expect(uploadMediaChunk.mock.calls.map((call) => call[1])).toEqual([0, 2]);
    expect(completeMediaUpload).toHaveBeenCalledWith(ANSWER_ID, {
      totalChunks: 3,
      totalBytes: 10,
      contentType: 'video/webm',
      checksumSha256: sha256Of('0123456789'),
    });
    expect(progress.at(-1)).toBe(1);
  });

  it('服务端已收齐时不重复上传任何分片', async () => {
    getMediaUploadStatus.mockResolvedValue({ totalChunks: 3, receivedChunks: [0, 1, 2], readyToMerge: true });

    await uploadMediaInChunks(ANSWER_ID, blobOf('0123456789'), { chunkSize: 4 });

    expect(uploadMediaChunk).not.toHaveBeenCalled();
    expect(completeMediaUpload).toHaveBeenCalledTimes(1);
  });

  it('上传输出的分片内容与原始切片一致', async () => {
    getMediaUploadStatus.mockResolvedValue({ totalChunks: 3, receivedChunks: [], readyToMerge: false });

    await uploadMediaInChunks(ANSWER_ID, blobOf('0123456789'), { chunkSize: 4 });

    const uploaded = await Promise.all(uploadMediaChunk.mock.calls.map((call) => call[3].text()));
    expect(uploaded).toEqual(['0123', '4567', '89']);
  });
});

describe('uploadAnswerMediaWithFallback', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    tryUploadAnswerMedia.mockResolvedValue({ id: ANSWER_ID });
  });

  it('小文件直接走单段上传', async () => {
    await uploadAnswerMediaWithFallback(ANSWER_ID, blobOf('small'), { chunkSize: 1024 });

    expect(tryUploadAnswerMedia).toHaveBeenCalledTimes(1);
    expect(getMediaUploadStatus).not.toHaveBeenCalled();
  });

  it('后端没有分片接口时退回单段上传（向后兼容）', async () => {
    getMediaUploadStatus.mockRejectedValue(Object.assign(new Error('Not Found'), { status: 404 }));

    await uploadAnswerMediaWithFallback(ANSWER_ID, blobOf('0123456789'), { chunkSize: 4 });

    expect(tryUploadAnswerMedia).toHaveBeenCalledTimes(1);
  });

  it('其它错误照常抛出，不掩盖真实失败', async () => {
    getMediaUploadStatus.mockRejectedValue(Object.assign(new Error('boom'), { status: 500 }));

    await expect(uploadAnswerMediaWithFallback(ANSWER_ID, blobOf('0123456789'), { chunkSize: 4 }))
      .rejects.toThrow('boom');
    expect(tryUploadAnswerMedia).not.toHaveBeenCalled();
  });
});
