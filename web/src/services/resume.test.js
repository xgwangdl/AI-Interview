import { beforeEach, describe, expect, it, vi } from 'vitest';
import { downloadResume, RESUME_EXPORT_FORMATS } from './resume';

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
