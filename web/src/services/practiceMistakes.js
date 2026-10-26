import { apiRequest } from './api';
import { DIMENSION_LABEL_KEYS, DIMENSION_LABELS } from './practiceArchive';

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

export const DIFFICULTY_LABEL_KEYS = Object.fromEntries(
  Object.keys(DIFFICULTY_LABELS).map((key) => [key, `domain.difficulty.${key}`]),
);

/** 错题页每页条数：与后端 `MistakeBookService.DEFAULT_LIMIT` 保持一致（改要一起改）。 */
export const PAGE_SIZE = 20;

/**
 * 拉一页错题。
 *
 * `limit` 之外还支持 `offset` 与 `role`：总览页只传 `limit: 5`（拿最差的前 5 条 + 总数），
 * `/mistakes` 页用它做真分页与岗位筛选。参数拼在这里而不是组件里，是为了能单测。
 */
export function loadMistakes({ role = '', offset = 0, limit = PAGE_SIZE } = {}) {
  const params = new URLSearchParams({ offset: String(Math.max(0, offset)), limit: String(limit) });
  if (role) params.set('role', role);
  return apiRequest(`/practice-archive/mistakes?${params.toString()}`);
}

export function retryMistake(questionId) {
  return apiRequest(`/practice-archive/mistakes/${encodeURIComponent(questionId)}/retry`, { method: 'POST' });
}

/**
 * 后端汇总 → 页面要的东西。
 *
 * 文案（"差 12 分"这种）放前端：改措辞不用重新部署后端。
 */
export function buildMistakes(book, t = null) {
  const threshold = typeof book?.passThreshold === 'number' ? book.passThreshold : 75;
  const mistakes = (book?.mistakes || []).map((item) => {
    const best = round(item.bestScore);
    return {
      questionId: item.questionId,
      text: item.text || (t ? t('mistakes.missingQuestion') : '（这道题没有保存题干）'),
      category: item.category || (t ? t('domain.general') : '综合'),
      role: item.role || '',
      difficultyLabel: t && DIFFICULTY_LABEL_KEYS[item.difficulty]
        ? t(DIFFICULTY_LABEL_KEYS[item.difficulty])
        : DIFFICULTY_LABELS[item.difficulty] || item.difficulty || '',
      attempts: item.attempts || 0,
      bestScore: best,
      latestScore: round(item.latestScore),
      /** 还差多少分到线：比"52 分"更能说明重练的目标 */
      gap: best == null ? null : Math.max(0, round(threshold - best)),
      label: best == null ? '—' : String(best),
      weakestLabel: item.weakestDimension
        ? (t && DIMENSION_LABEL_KEYS[item.weakestDimension]
          ? t(DIMENSION_LABEL_KEYS[item.weakestDimension])
          : DIMENSION_LABELS[item.weakestDimension] || item.weakestDimension)
        : '',
      weakestScore: round(item.weakestScore),
      attemptsLabel: item.attempts > 1
        ? (t ? t('mistakes.attempts', { count: item.attempts - 1 }) : `已重练 ${item.attempts - 1} 次`)
        : (t ? t('mistakes.neverRetried') : '还没重练过'),
    };
  });

  return {
    scopeNote: book?.scopeNote || '',
    passThreshold: threshold,
    total: book?.total ?? mistakes.length,
    offset: book?.offset ?? 0,
    limit: book?.limit || mistakes.length || PAGE_SIZE,
    role: book?.role || '',
    roles: (book?.roles || []).map((item) => ({ role: item.role, count: item.count })),
    mistakes,
    /** 分页控件要的东西：当前第几页、共几页、还有没有下一页 */
    page: pageOf(book?.offset, book?.limit, book?.total),
    pageCount: pageCountOf(book?.limit, book?.total ?? mistakes.length),
    hasMore: hasMoreOf(book?.offset, book?.limit, book?.total),
  };
}

function pageOf(offset, limit, total) {
  const size = Number(limit) > 0 ? Number(limit) : PAGE_SIZE;
  const from = Math.max(0, Number(offset) || 0);
  return Math.floor(from / size) + 1;
}

function pageCountOf(limit, total) {
  const size = Number(limit) > 0 ? Number(limit) : PAGE_SIZE;
  return Math.max(1, Math.ceil(Math.max(0, Number(total) || 0) / size));
}

function hasMoreOf(offset, limit, total) {
  const size = Number(limit) > 0 ? Number(limit) : PAGE_SIZE;
  const from = Math.max(0, Number(offset) || 0);
  return from + size < (Number(total) || 0);
}

function round(value) {
  return typeof value === 'number' ? Math.round(value * 10) / 10 : null;
}
