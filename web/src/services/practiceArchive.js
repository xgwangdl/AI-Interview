import { apiRequest } from './api';

/**
 * 训练档案（跨场次成长 + 短板）。
 *
 * 纯计算放在这里而不是组件里：项目没有 @vue/test-utils，组件测不了，
 * 而"哪个维度算短板""什么时候不给建议"这些判断恰恰最需要测。
 */

/** 与报告页保持一致；改动请同时改 ReportView.vue。 */
export const DIMENSION_LABELS = {
  relevance: '内容相关性',
  completeness: '回答完整度',
  professionalism: '专业程度',
  starStructure: 'STAR 结构',
  logic: '回答逻辑',
};

/** 短板对应的可执行建议：只说"差"没用，得告诉他下次注意什么。 */
const DIMENSION_ADVICE = {
  relevance: '回答前先复述问题里的关键点，确认自己没答偏',
  completeness: '按"结论 → 两三条理由 → 一个例子"把要点答全',
  professionalism: '多带技术细节：具体方案、参数、取舍，而不是笼统地说"优化了性能"',
  starStructure: '讲经历按 Situation → Task → Action → Result 走，尤其别漏 Result',
  logic: '先用一句话给结论，再分点展开，少用"然后…然后…"',
};

export function loadArchive(limit = 5) {
  return apiRequest(`/practice-archive?limit=${encodeURIComponent(limit)}`);
}

/**
 * 后端汇总 → 页面要的东西。
 *
 * 后端故意只回数据（维度 key + 分数），标签与建议文案放前端，
 * 这样调整措辞不用重新部署后端；而 key 到中文的翻译只有这一处。
 */
export function buildArchive(summary) {
  const trend = (summary?.trend || []).map((point) => ({
    sessionId: point.sessionId,
    role: point.role,
    createdAt: point.createdAt,
    overall: typeof point.overall === 'number' ? point.overall : null,
    label: shortDate(point.createdAt),
  }));
  const dimensions = (summary?.dimensions || []).map((item) => ({
    key: item.key,
    label: DIMENSION_LABELS[item.key] || item.key,
    average: round(item.average),
    samples: item.samples || 0,
  }));
  const weakest = (summary?.weakest || []).map((item) => ({
    key: item.key,
    label: DIMENSION_LABELS[item.key] || item.key,
    average: round(item.average),
    advice: DIMENSION_ADVICE[item.key] || '下次练习时留意这一点',
  }));

  return {
    scopeNote: summary?.scopeNote || '',
    scoredSessions: summary?.totals?.scoredSessions || 0,
    evaluatedAnswers: summary?.totals?.evaluatedAnswers || 0,
    enoughForTrend: Boolean(summary?.enoughForTrend),
    trend,
    dimensions,
    weakest,
    /** 只练过一场：告诉他还差一场才能看到趋势，而不是画一条看不出来的线。 */
    needsMoreSessions: !summary?.enoughForTrend,
    focusKeys: weakest.map((item) => item.key),
    latestRole: trend.length ? trend[trend.length - 1].role : '',
  };
}

function round(value) {
  return typeof value === 'number' ? Math.round(value * 10) / 10 : null;
}

function shortDate(value) {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

/**
 * 折线图坐标（0-100 分映射到画布高度）。
 *
 * 只有两个点时也要画得出来 —— 一次线上就是"两场练习"的场景。
 */
export function trendPoints(trend, width, height, padding = 12) {
  if (!trend?.length) return [];
  const usableWidth = width - padding * 2;
  const usableHeight = height - padding * 2;
  const step = trend.length > 1 ? usableWidth / (trend.length - 1) : 0;
  return trend.map((point, index) => {
    const score = typeof point.overall === 'number' ? point.overall : 0;
    const x = trend.length > 1 ? padding + step * index : width / 2;
    const y = padding + usableHeight * (1 - Math.min(Math.max(score, 0), 100) / 100);
    return { x, y, score, label: point.label };
  });
}
