import { describe, expect, it } from 'vitest';
import { isPermanentUploadRejection, uploadRejectionMessage } from './mediaErrors';

describe('media upload errors', () => {
  it('把 400/415 视为需要重新录制的永久拒绝', () => {
    expect(isPermanentUploadRejection({ status: 400 })).toBe(true);
    expect(isPermanentUploadRejection({ status: 415 })).toBe(true);
  });

  it('超时、限流和网络错误仍允许本地补传', () => {
    expect(isPermanentUploadRejection({ status: 408 })).toBe(false);
    expect(isPermanentUploadRejection({ status: 429 })).toBe(false);
    expect(isPermanentUploadRejection(new Error('offline'))).toBe(false);
  });

  it('保留后端给用户的具体原因', () => {
    expect(uploadRejectionMessage({ message: '录制文件过小或不完整' }))
      .toBe('视频无法保存：录制文件过小或不完整');
  });
});
