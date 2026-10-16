import { beforeEach, describe, expect, it, vi } from 'vitest';
import { draftResume, downloadResume, refineResume, RESUME_EXPORT_FORMATS } from './resume';

function fakeDownload(status, body, contentType) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => JSON.parse(body),
    blob: async () => new Blob(['doc'], { type: contentType }),
  };
}

/** 测试跑在 node 环境（没有 DOM），这里用替身记录下载链接，断言文件名。 */
function stubBrowserDownload() {
  const clicked = [];
  globalThis.URL.createObjectURL = vi.fn(() => 'blob:resume');
  globalThis.URL.revokeObjectURL = vi.fn();
  globalThis.document = {
    createElement: () => {
      const anchor = { href: '', download: '', click: () => clicked.push(anchor), remove: () => {} };
      return anchor;
    },
    body: { appendChild: () => {} },
  };
  return clicked;
}

describe('downloadResume', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('三种格式都能导出，并把扩展名带进下载文件名', async () => {
    localStorage.setItem('token', 'tok-1');

    for (const format of RESUME_EXPORT_FORMATS) {
      const fetchMock = vi.fn().mockResolvedValue(fakeDownload(200, '{}', format.value));
      globalThis.fetch = fetchMock;
      const clicked = stubBrowserDownload();

      await downloadResume('resume-1', '张伟的简历', format.value);

      expect(fetchMock.mock.calls[0][0]).toContain(`/resumes/resume-1/export?format=${format.value}`);
      expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer tok-1');
      expect(clicked[0].download).toBe(`张伟的简历.${format.suffix}`);
    }
  });

  it('标题里的非法文件名字符被替换掉', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(fakeDownload(200, '{}', 'docx'));
    const clicked = stubBrowserDownload();

    await downloadResume('resume-1', '张伟/后端:2026', 'docx');

    expect(clicked[0].download).toBe('张伟-后端-2026.docx');
  });

  it('失败时把后端的 message 带出来，而不是只报状态码', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      fakeDownload(500, JSON.stringify({ code: 500, message: '服务器上没有可用于 PDF 的中文字体' })),
    );

    await expect(downloadResume('resume-1', '简历', 'pdf')).rejects.toThrow(
      '导出失败：服务器上没有可用于 PDF 的中文字体',
    );
  });
});

function jsonResponse(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    text: async () => JSON.stringify(body),
    json: async () => body,
  };
}

describe('从零创建的两个接口', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('生成草稿：把素材 POST 到 /resumes/draft', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {
      rawText: '张三\n教育背景\n- 【请补充：学校】', questions: ['补一条量化结果'], placeholders: [], model: 'qwen-max',
    }));
    globalThis.fetch = fetchMock;
    const materials = { targetRole: 'Java 后端', basics: { name: '张三' }, education: [], work: [], projects: [] };

    const result = await draftResume(materials);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/resumes/draft');
    expect(fetchMock.mock.calls[0][1].method).toBe('POST');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual(materials);
    expect(result.questions).toEqual(['补一条量化结果']);
  });

  it('补全：正文、补充内容与追问一起 POST 到 /resumes/refine', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, {
      rawText: '张三\n工作经历\n- 结果：800ms → 120ms', questions: [], placeholders: [], model: 'qwen-max',
    }));
    globalThis.fetch = fetchMock;

    await refineResume('张三\n工作经历\n- 结果：【请补充：量化结果】', '接口耗时 800ms → 120ms', ['补一条量化结果']);

    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/resumes/refine');
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      rawText: '张三\n工作经历\n- 结果：【请补充：量化结果】',
      supplement: '接口耗时 800ms → 120ms',
      questions: ['补一条量化结果'],
    });
  });

  it('模型这次没按约定返回时，错误消息要能直接显示给用户', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue(
      jsonResponse(502, { code: 502, message: '模型这次没按约定返回，请重试：resumeText 不能为空', data: null }),
    );

    await expect(draftResume({})).rejects.toThrow('模型这次没按约定返回，请重试');
  });
});
