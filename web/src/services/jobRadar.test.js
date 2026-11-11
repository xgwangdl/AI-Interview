import { describe, expect, it } from 'vitest';
import { buildQuery, isRunActive, JOB_STATUSES, QUESTION_CATEGORIES } from './jobRadar';

describe('job radar service helpers', () => {
  it('builds a stable query while omitting empty filters', () => {
    expect(buildQuery({ offset: 20, limit: 10, status: '', role: null })).toBe('?offset=20&limit=10');
    expect(buildQuery()).toBe('');
  });

  it('treats pending, running and deferred runs as active', () => {
    expect(['pending', 'running', 'deferred'].every(isRunActive)).toBe(true);
    expect(isRunActive('succeeded')).toBe(false);
    expect(isRunActive(null)).toBe(false);
  });

  it('keeps backend status and category vocabularies aligned', () => {
    expect(JOB_STATUSES).toContain('evaluating');
    expect(JOB_STATUSES).toContain('archived');
    expect(QUESTION_CATEGORIES).toContain('system_design');
    expect(QUESTION_CATEGORIES).toHaveLength(6);
  });
});
