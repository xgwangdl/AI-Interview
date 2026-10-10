import { describe, expect, it } from 'vitest';
import { buildQuestions, styleLabels, typeLabels } from './questions';

describe('buildQuestions', () => {
  it('按题量返回题目，并生成唯一且连续的 id', () => {
    const questions = buildQuestions('Java 后端', 5, 'technical');

    expect(questions).toHaveLength(5);
    expect(questions.map((question) => question.id))
      .toEqual(['q-1', 'q-2', 'q-3', 'q-4', 'q-5']);
    expect(new Set(questions.map((question) => question.text)).size).toBe(5);
  });

  it('题目里带上目标岗位', () => {
    expect(buildQuestions('数据工程师', 1, 'technical')[0].text).toContain('数据工程师');
  });

  it('不同面试类型给出不同题库', () => {
    const behavioral = buildQuestions('Java 后端', 3, 'behavioral').map((question) => question.text);
    const manager = buildQuestions('Java 后端', 3, 'manager').map((question) => question.text);

    expect(behavioral).not.toEqual(manager);
  });

  it('题量超过题库时返回题库里的全部题目，而不是抛错', () => {
    expect(buildQuestions('Java 后端', 99, 'technical')).toHaveLength(8);
  });

  it('未知面试类型回退到综合行为题库，而不是抛错', () => {
    const fallback = buildQuestions('Java 后端', 3, 'unknown-type');

    expect(fallback).toHaveLength(3);
    expect(fallback).toEqual(buildQuestions('Java 后端', 3, 'behavioral'));
  });

  it('每一题都带分类标签', () => {
    for (const question of buildQuestions('Java 后端', 8, 'behavioral')) {
      expect(question.category).toBeTruthy();
    }
  });
});

describe('标签表', () => {
  it('覆盖三种面试类型与三种面试官风格', () => {
    expect(Object.keys(typeLabels).sort()).toEqual(['behavioral', 'manager', 'technical']);
    expect(Object.keys(styleLabels).sort()).toEqual(['balanced', 'direct', 'supportive']);
  });
});
