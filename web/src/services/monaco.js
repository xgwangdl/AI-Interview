// Monaco 体积不小，按需懒加载：只有真正打开笔试页面时才把这些 chunk 拉下来。
import EditorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

let monacoPromise;

/** 语言 id 映射到 Monaco 的语言标识。 */
export function monacoLanguageId(language) {
  return {
    java: 'java',
    python: 'python',
    cpp: 'cpp',
    javascript: 'javascript',
    go: 'go',
    sql: 'sql',
  }[language] || 'plaintext';
}

export function loadMonaco() {
  if (!monacoPromise) {
    monacoPromise = (async () => {
      // 这些语言用的是 Monarch 词法高亮，跑在主线程，只需要核心 worker。
      self.MonacoEnvironment = { getWorker: () => new EditorWorker() };
      const monaco = await import('monaco-editor');
      await Promise.all([
        import('monaco-editor/esm/vs/basic-languages/java/java.contribution'),
        import('monaco-editor/esm/vs/basic-languages/python/python.contribution'),
        import('monaco-editor/esm/vs/basic-languages/cpp/cpp.contribution'),
        import('monaco-editor/esm/vs/basic-languages/javascript/javascript.contribution'),
        import('monaco-editor/esm/vs/basic-languages/go/go.contribution'),
        import('monaco-editor/esm/vs/basic-languages/sql/sql.contribution'),
      ]);
      return monaco;
    })();
  }
  return monacoPromise;
}
