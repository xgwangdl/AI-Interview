/**
 * An interview should reveal questions as they become relevant. Keep answered
 * history plus the current question in the rail, but never leak future prompts.
 */
export function visibleQuestionIndices(questions, answers, currentIndex) {
  if (!Array.isArray(questions) || questions.length === 0) return [];
  const answered = new Set((answers || []).map((item) => Number(item.questionIndex)));
  return questions
    .map((_, index) => index)
    .filter((index) => index === currentIndex || answered.has(index));
}

export function hiddenQuestionCount(questions, answers, currentIndex) {
  const total = Array.isArray(questions) ? questions.length : 0;
  return Math.max(0, total - visibleQuestionIndices(questions, answers, currentIndex).length);
}
