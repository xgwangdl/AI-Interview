const DIMENSIONS = ['relevance', 'completeness', 'professionalism', 'starStructure', 'logic'];

/** AI scoring is diagnostic. Whole numbers and bands avoid laboratory-style false precision. */
export function displayScore(value) {
  if (value === null || value === undefined || value === '') return '—';
  const number = Number(value);
  return Number.isFinite(number) ? String(Math.round(number)) : '—';
}

export function scoreBandKey(value) {
  if (value === null || value === undefined || value === '') return 'unknown';
  const number = Number(value);
  if (!Number.isFinite(number)) return 'unknown';
  if (number >= 80) return 'good';
  if (number >= 60) return 'fair';
  if (number >= 40) return 'weak';
  return 'poor';
}

export function buildDimensionAssessments(evaluation) {
  const feedback = new Map(
    (evaluation?.dimensionFeedback || [])
      .filter((item) => item?.dimension)
      .map((item) => [item.dimension, item]),
  );
  return DIMENSIONS.map((key) => {
    const value = evaluation?.scores?.[key];
    const detail = feedback.get(key);
    return {
      key,
      score: displayScore(value),
      band: scoreBandKey(value),
      reason: detail?.reason || '',
      evidenceQuote: detail?.evidenceQuote || '',
    };
  });
}

export function hasEvidenceTiming(evidence) {
  return Number.isFinite(evidence?.startSeconds)
    && Number.isFinite(evidence?.endSeconds)
    && evidence.endSeconds >= evidence.startSeconds;
}
