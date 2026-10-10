import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    // 这些用例只覆盖纯逻辑（API 客户端、Pinia store），不需要 DOM；
    // localStorage 由 setup 文件用内存实现替代。
    environment: 'node',
    include: ['src/**/*.test.js'],
    setupFiles: ['./vitest.setup.js'],
  },
});
