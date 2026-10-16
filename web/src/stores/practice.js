import { defineStore } from 'pinia';
import { deleteMedia } from '../services/mediaStore';
import { getSessions, tryDeleteSession } from '../services/api';
import { retryMistake as createRetrySession } from '../services/practiceMistakes';

const storageKey = () => `ai-interview-practice-v1:${localStorage.getItem('userId') || 'anonymous'}`;
const retentionDays = 7;

function readSessions() {
  try {
    const value = JSON.parse(localStorage.getItem(storageKey()) || '[]');
    return Array.isArray(value) ? value : [];
  } catch {
    return [];
  }
}

function persist(sessions) {
  localStorage.setItem(storageKey(), JSON.stringify(sessions));
}

export const usePracticeStore = defineStore('practice', {
  state: () => ({ sessions: readSessions(), draft: null }),
  getters: {
    completed: (state) => state.sessions.filter((item) => item.status === 'completed'),
    inProgress: (state) => state.sessions.filter((item) => item.status === 'in_progress'),
  },
  actions: {
    createDraft(configuration) {
      this.draft = {
        id: crypto.randomUUID(),
        ...configuration,
        status: 'in_progress',
        createdAt: new Date().toISOString(),
        retentionUntil: new Date(Date.now() + retentionDays * 86400000).toISOString(),
        answers: [],
      };
      return this.draft;
    },
    async loadFromServer() {
      const remoteSessions = await getSessions();
      const localSessions = readSessions();
      this.sessions = remoteSessions.map((remote) => {
        const local = localSessions.find((item) => item.id === remote.id);
        const localAnswers = local?.answers || [];
        return {
          ...remote,
          answers: remote.answers.map((answer) => {
            const cached = localAnswers.find((item) => item.questionIndex === answer.questionIndex);
            return {
              ...answer,
              mediaId: answer.id,
              localMediaId: cached?.localMediaId || cached?.mediaId || null,
              savedAt: cached?.savedAt || answer.createdAt,
              bytes: answer.mediaSizeBytes ?? cached?.bytes ?? 0,
              mimeType: answer.mediaContentType || cached?.mimeType || 'video/webm',
            };
          }),
        };
      });
      persist(this.sessions);
      if (this.draft) this.draft = this.find(this.draft.id) || null;
    },
    saveDraft() {
      if (!this.draft) return;
      const index = this.sessions.findIndex((item) => item.id === this.draft.id);
      if (index === -1) this.sessions.unshift(this.draft);
      else this.sessions[index] = this.draft;
      persist(this.sessions);
    },
    setAnswer(answer) {
      if (!this.draft) return;
      const index = this.draft.answers.findIndex((item) => item.questionIndex === answer.questionIndex);
      if (index === -1) this.draft.answers.push(answer);
      else this.draft.answers[index] = answer;
      this.saveDraft();
    },
    finishDraft() {
      if (!this.draft) return;
      this.draft.status = 'completed';
      this.draft.completedAt = new Date().toISOString();
      this.saveDraft();
      this.draft = null;
    },
    resumeDraft(session) {
      this.draft = session;
      this.draft.status = 'in_progress';
    },
    find(id) {
      return this.sessions.find((item) => item.id === id);
    },
    async remove(id) {
      const session = this.find(id);
      if (session) await tryDeleteSession(id);
      this.sessions = this.sessions.filter((item) => item.id !== id);
      if (this.draft?.id === id) this.draft = null;
      persist(this.sessions);
      if (session) {
        try {
          await deleteMedia(session.answers.map((answer) => answer.localMediaId || answer.mediaId).filter(Boolean));
        } catch {
          return;
        }
      }
    },
    /**
     * 重练一道错题。
     *
     * 后端建的是一场**正常的练习**（只有这一道题），所以这里顺手刷新一次列表：
     * 设备检测页是从本地列表里找这场练习的，不刷新就会"找不到这次练习"。
     * 放在 store 里而不是两个视图各写一遍，也是为了让"重练"只有一条路径。
     *
     * @returns 新建练习的 id
     */
    async retryMistake(questionId) {
      const created = await createRetrySession(questionId);
      await this.loadFromServer();
      return created.id;
    },
    async pruneExpired() {
      const expired = this.sessions.filter((item) => item.retentionUntil && Date.parse(item.retentionUntil) <= Date.now());
      for (const item of expired) {
        try {
          await tryDeleteSession(item.id);
          await deleteMedia(item.answers.map((answer) => answer.localMediaId || answer.mediaId).filter(Boolean));
        } catch {
          return;
        }
      }
      this.sessions = this.sessions.filter((item) => !expired.includes(item));
      persist(this.sessions);
    },
  },
});
