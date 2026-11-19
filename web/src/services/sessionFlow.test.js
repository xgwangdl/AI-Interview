import { describe, expect, it } from 'vitest';
import { hiddenQuestionCount, visibleQuestionIndices } from './sessionFlow';

describe('adaptive interview question rail', () => {
  const questions = [
    { id: 'q-1', text: 'Java 调优怎么做？' },
    { id: 'q-2', text: 'Spring 事务什么时候失效？' },
    { id: 'q-3', text: 'MySQL 索引如何设计？' },
  ];

  it('only exposes the current question before any answer exists', () => {
    expect(visibleQuestionIndices(questions, [], 0)).toEqual([0]);
    expect(hiddenQuestionCount(questions, [], 0)).toBe(2);
  });

  it('keeps answered history and the current question without leaking future questions', () => {
    const answers = [{ questionIndex: 0 }];

    expect(visibleQuestionIndices(questions, answers, 1)).toEqual([0, 1]);
    expect(hiddenQuestionCount(questions, answers, 1)).toBe(1);
  });

  it('shows all questions once they have been reached', () => {
    const answers = [{ questionIndex: 0 }, { questionIndex: 1 }];

    expect(visibleQuestionIndices(questions, answers, 2)).toEqual([0, 1, 2]);
    expect(hiddenQuestionCount(questions, answers, 2)).toBe(0);
  });
});
