import { apiRequest } from './api';
import { DIMENSION_LABELS } from './practiceArchive';

/**
 * 错题本。
 *
 * 与训练档案的分工：档案告诉你"哪一维弱"，错题本告诉你"哪句话没说出来"，
 * 并且能就地重练那一题（重练成绩过线后它自己会消失）。
 *
 * 与 practiceArchive.js 一样，纯计算放这里：项目没有 @vue/test-utils，
 * 组件测不了，而"哪条算错题、分数怎么显示"恰恰是最该钉住的部分。
 */

/** 与 SetupView 的难度选项一致；认不出的值原样显示，不隐藏信息。 */
export const DIFFICULTY_LABELS = {
  foundation: '基础',
  standard: '标准',
  advanced: '进阶',
};

export function loadMistakes(limit = 5) {
  return apiRequest(`/practice-archive/mistakes?limit=${encodeURIComponent(limit)}`);
}

export function retryMistake(questionId) {
  return apiRequest(`/practice-archive/mistakes/${encodeURIComponent(questionId)}/retry`, { method: 'POST' });
}

/**
 * 后端汇总 → 页面要的东西。
 *
 * 文案（"差 12 分"这种）放前端：改措辞不用重新部署后端。
 */
export function buildMistakes(book) {
  const threshold = typeof book?.passThreshold === 'number' ? book.passThreshold : 75;
  const mistakes = (book?.mistakes || []).map((item) => {
    const best = round(item.bestScore);
    return {
      questionId: item.questionId,
      text: item.text || '（这道题没有保存题干）',
      category: item.category || '综合',
      role: item.role || '',
      difficultyLabel: DIFFICULTY_LABELS[item.difficulty] || item.difficulty || '',
      attempts: item.attempts || 0,
      bestScore: best,
      latestScore: round(item.latestScore),
      /** 还差多少分到线：比"52 分"更能说明重练的目标 */
      gap: best == null ? null : Math.max(0, round(threshold - best)),
      label: best == null ? '—' : String(best),
      weakestLabel: item.weakestDimension ? (DIMENSION_LABELS[item.weakestDimension] || item.weakestDimension) : '',
      weakestScore: round(item.weakestScore),
      attemptsLabel: item.attempts > 1 ? `已重练 ${item.attempts - 1} 次` : '还没重练过',
    };
  });

  return {
    scopeNote: book?.scopeNote || '',
    passThreshold: threshold,
    total: book?.total ?? mistakes.length,
    mistakes,
  };
}

function round(value) {
  return typeof value === 'number' ? Math.round(value * 10) / 10 : null;
}
