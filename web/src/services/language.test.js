import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  latencyLabel, pickVoice, reportRows, sessionSubtitle, submitAudioTurn, submitTextTurn,
} from './language';

describe('pickVoice（浏览器语音）', () => {
  const voices = [
    { name: 'Zira', lang: 'en-US' },
    { name: 'Huihui', lang: 'zh-CN' },
    { name: 'Nanami', lang: 'ja-JP' },
    { name: 'Kyoko', lang: 'ja-JP' },
  ];

  it('先找完全匹配，再找同语言', () => {
    expect(pickVoice(voices, 'ja-JP').name).toBe('Nanami');
    expect(pickVoice(voices, 'en-GB').name).toBe('Zira');
  });

  it('没有这门语言就不将就：宁可不说话，也不拿中文音色念日语', () => {
    expect(pickVoice(voices, 'ko-KR')).toBeNull();
    expect(pickVoice([], 'ja-JP')).toBeNull();
    expect(pickVoice(undefined, 'ja-JP')).toBeNull();
  });
});

describe('报告与副标题', () => {
  it('四项分数排成固定顺序，缺分显示 —，不拿 0 分冒充', () => {
    const rows = reportRows({ fluency: 78, vocabulary: 64, grammar: null, task: 90 });

    expect(rows.map((row) => row.label)).toEqual(['流利度', '用词', '语法', '任务完成']);
    expect(rows.map((row) => row.value)).toEqual([78, 64, null, 90]);
    expect(rows[2].display).toBe('—');
  });

  it('会话副标题带水平、轮次与状态', () => {
    expect(sessionSubtitle({ level: 'beginner', turnCount: 3, maxTurns: 8, status: 'active' }))
      .toBe('初级 · 3/8 轮 · 进行中');
  });

  it('耗时按段展示，没有的段不显示', () => {
    expect(latencyLabel({ asrMs: 800, llmMs: 1200, ttsMs: 1500 })).toBe('听写 0.8s · 回复 1.2s · 配音 1.5s');
    expect(latencyLabel({ llmMs: 900, ttsMs: 0 })).toBe('回复 0.9s · 配音 0.0s');
    expect(latencyLabel(null)).toBe('');
  });
});

describe('接口调用', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('手打一轮走 turns/text', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;

    await submitTextTurn('s-1', 'I led a migration.');

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/language-sessions/s-1/turns/text');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(fetchMock.mock.calls[0][1].body).toContain('I led a migration.');
  });

  it('一轮录音走 multipart，并带上时长', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true, status: 200, text: async () => '{}', json: async () => ({}),
    });
    globalThis.fetch = fetchMock;
    const blob = new Blob(['audio'], { type: 'audio/webm' });

    await submitAudioTurn('s-2', blob, 4321);

    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/v1/language-sessions/s-2/turns');
    expect(options.body).toBeInstanceOf(FormData);
    expect(options.body.get('durationMs')).toBe('4321');
    expect(options.body.get('audio')).toBeInstanceOf(Blob);
    // FormData 交给浏览器设置 boundary，不能自己写 Content-Type
    expect(options.headers['Content-Type']).toBeUndefined();
  });
});
