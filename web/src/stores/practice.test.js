import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('../services/api', () => ({
  getSessions: vi.fn(),
  tryDeleteSession: vi.fn(),
}));
vi.mock('../services/mediaStore', () => ({
  deleteMedia: vi.fn(),
}));

import { getSessions, tryDeleteSession } from '../services/api';
import { deleteMedia } from '../services/mediaStore';
import { usePracticeStore } from './practice';

const STORAGE_KEY = 'ai-interview-practice-v1:u1';

describe('practice store', () => {
  beforeEach(() => {
    localStorage.clear();
    localStorage.setItem('userId', 'u1');
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('把本地缓存的媒体 id 合并进服务端返回的回答，回放优先用本地文件', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([
      { id: 's1', answers: [{ questionIndex: 0, localMediaId: 'local-0', savedAt: '2026-01-01T00:00:00.000Z' }] },
    ]));
    getSessions.mockResolvedValue([
      {
        id: 's1',
        status: 'completed',
        answers: [{
          id: 'a1',
          questionIndex: 0,
          mediaSizeBytes: 17,
          mediaContentType: 'video/webm',
          createdAt: '2026-01-02T00:00:00.000Z',
        }],
      },
    ]);

    const store = usePracticeStore();
    await store.loadFromServer();

    const [answer] = store.sessions[0].answers;
    expect(answer.mediaId).toBe('a1');
    expect(answer.localMediaId).toBe('local-0');
    expect(answer.bytes).toBe(17);
    expect(answer.mimeType).toBe('video/webm');
  });

  it('清理超过保留期的会话，并删除对应的本地媒体', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([
      { id: 'old', retentionUntil: '2020-01-01T00:00:00.000Z', answers: [{ questionIndex: 0, localMediaId: 'm-old' }] },
      { id: 'fresh', retentionUntil: '2999-01-01T00:00:00.000Z', answers: [] },
    ]));
    tryDeleteSession.mockResolvedValue(null);
    deleteMedia.mockResolvedValue(undefined);

    const store = usePracticeStore();
    await store.pruneExpired();

    expect(tryDeleteSession).toHaveBeenCalledWith('old');
    expect(deleteMedia).toHaveBeenCalledWith(['m-old']);
    expect(store.sessions.map((item) => item.id)).toEqual(['fresh']);
  });

  it('删除会话时同步删掉服务端记录与本地媒体', async () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([
      { id: 's1', status: 'completed', answers: [{ questionIndex: 0, localMediaId: 'm-1', mediaId: 'a-1' }] },
    ]));
    tryDeleteSession.mockResolvedValue(null);
    deleteMedia.mockResolvedValue(undefined);

    const store = usePracticeStore();
    await store.remove('s1');

    expect(tryDeleteSession).toHaveBeenCalledWith('s1');
    expect(deleteMedia).toHaveBeenCalledWith(['m-1']);
    expect(store.sessions).toHaveLength(0);
  });
});
