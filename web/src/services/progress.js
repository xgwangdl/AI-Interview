import { apiRequest } from './api';

/** 训练周报与提醒偏好。 */

export const DIMENSION_LABELS = {
  relevance: '内容相关性',
  completeness: '回答完整度',
  professionalism: '专业程度',
  starStructure: 'STAR 结构',
  logic: '回答逻辑',
};

export function loadWeeklyReport(week = '') {
  const query = week ? `?week=${encodeURIComponent(week)}` : '';
  return apiRequest(`/progress/weekly${query}`);
}

export function loadNotificationPreferences() {
  return apiRequest('/me/notifications');
}

export function saveNotificationPreferences(payload) {
  return apiRequest('/me/notifications', { method: 'PUT', body: JSON.stringify(payload) });
}

export function sendTestNotification() {
  return apiRequest('/me/notifications/test', { method: 'POST' });
}

export function buildWeekly(report) {
  const dimensions = Object.entries(report?.dimensions || {}).map(([key, value]) => ({
    key,
    label: DIMENSION_LABELS[key] || key,
    value: typeof value === 'number' ? Math.round(value * 10) / 10 : null,
  }));
  const best = report?.bestImprovement
    ? {
      ...report.bestImprovement,
      delta: typeof report.bestImprovement.afterScore === 'number' && typeof report.bestImprovement.beforeScore === 'number'
        ? Math.round((report.bestImprovement.afterScore - report.bestImprovement.beforeScore) * 10) / 10
        : null,
    }
    : null;
  return {
    ...report,
    dimensions,
    bestImprovement: best,
    completionRate: typeof report?.completionRate === 'number' ? Math.round(report.completionRate) : 0,
    deltaLabel: report?.scoreDelta == null ? '暂无可对比数据'
      : `${report.scoreDelta >= 0 ? '+' : ''}${report.scoreDelta.toFixed(1)} 分`,
  };
}

export function weekLabel(value) {
  if (!value) return '';
  const date = new Date(`${value}T00:00:00`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric' }).format(date);
}

/** 分享文案只放统计，不放题干/转写，避免把隐私内容复制出去。 */
export function shareText(report) {
  if (!report) return '';
  const avg = report.averageScore == null ? '暂无分数' : `${report.averageScore.toFixed(1)} 分`;
  return `本周训练：完成 ${report.planCompleted}/${report.planTotal} 个训练项，`
    + `${report.sessions} 场练习，综合 ${avg}。`;
}
