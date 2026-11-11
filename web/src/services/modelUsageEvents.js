export const MODEL_USAGE_CHANGED_EVENT = 'light-interview-model-usage-changed';

/** 让顶栏在 AI 调用结束后立即重新读取后端计数。 */
export function notifyModelUsageChanged() {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') return;
  window.dispatchEvent(new Event(MODEL_USAGE_CHANGED_EVENT));
}
