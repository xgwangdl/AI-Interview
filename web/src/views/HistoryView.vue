<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t, locale } = useI18n();
const practice = usePracticeStore();
const keyword = ref('');
const status = ref('all');
const visibleSessions = computed(() => practice.sessions.filter((item) => {
  const matchesText = !keyword.value || `${item.role} ${item.company || ''}`.toLowerCase().includes(keyword.value.trim().toLowerCase());
  const matchesStatus = status.value === 'all' || item.status === status.value;
  return matchesText && matchesStatus;
}));

function open(session) {
  router.push(session.status === 'completed' ? `/practice/${session.id}/report` : `/practice/${session.id}/session`);
}

async function remove(session) {
  try {
    await ElMessageBox.confirm(
      t('history.deleteConfirmBody', { role: session.role }),
      t('history.deleteConfirmTitle'),
      { confirmButtonText: t('history.deleteAction'), cancelButtonText: t('common.cancel'), type: 'warning' },
    );
  } catch { return; }
  try {
    await practice.remove(session.id);
    ElMessage.success(t('history.deleted'));
  } catch (error) {
    ElMessage.error(error.message || t('history.deleteFailed'));
  }
}

function dateLabel(date) {
  return new Intl.DateTimeFormat(locale.value, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(date));
}

function retentionLabel(date) {
  return new Intl.DateTimeFormat(locale.value, { month: 'numeric', day: 'numeric' }).format(new Date(date));
}
</script>

<template>
  <section class="history-page">
    <div class="page-heading"><div><div class="eyebrow">{{ t('history.eyebrow') }}</div><h1>{{ t('history.title') }}</h1><p>{{ t('history.subtitle') }}</p></div><el-button class="primary-button" @click="router.push('/practice/setup')"><el-icon><Plus /></el-icon> {{ t('history.createPractice') }}</el-button></div>
    <div class="history-toolbar panel"><el-input v-model="keyword" clearable :placeholder="t('history.searchPlaceholder')" class="search-input"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-radio-group v-model="status" class="status-filter"><el-radio-button value="all">{{ t('history.all', { count: practice.sessions.length }) }}</el-radio-button><el-radio-button value="completed">{{ t('history.completedFilter', { count: practice.completed.length }) }}</el-radio-button><el-radio-button value="in_progress">{{ t('history.inProgressFilter', { count: practice.inProgress.length }) }}</el-radio-button></el-radio-group></div>
    <div v-if="visibleSessions.length" class="history-list">
      <article v-for="session in visibleSessions" :key="session.id" class="history-row panel">
        <div class="history-icon" :class="session.status"><el-icon><VideoCamera /></el-icon></div>
        <div class="history-main"><div class="history-title-line"><strong>{{ session.role }}</strong><el-tag v-if="session.status === 'completed'" class="status-done" size="small">{{ t('history.completed') }}</el-tag><el-tag v-else class="status-progress" size="small">{{ t('history.inProgress') }}</el-tag></div><div class="history-subline"><span>{{ session.company || t('history.selfPractice') }}</span><i></i><span>{{ t(`setup.${session.interviewType}`) || t('report.mockInterview') }}</span><i></i><span>{{ t('history.answers', { answered: session.answers.length, total: session.questionCount }) }}</span></div></div>
        <div class="history-details"><strong>{{ dateLabel(session.completedAt || session.createdAt) }}</strong><small>{{ t('history.retainedUntil', { date: retentionLabel(session.retentionUntil) }) }}</small></div>
        <button class="icon-action" :aria-label="t('history.deleteAria')" :title="t('history.deleteAria')" @click="remove(session)"><el-icon><Delete /></el-icon></button><el-button class="soft-button open-button" @click="open(session)">{{ session.status === 'completed' ? t('history.viewReport') : t('history.continuePractice') }} <el-icon><ArrowRight /></el-icon></el-button>
      </article>
    </div>
    <div v-else class="history-empty panel"><div class="empty-state-icon"><el-icon><FolderOpened /></el-icon></div><strong>{{ practice.sessions.length ? t('history.noMatches') : t('history.emptyTitle') }}</strong><p>{{ practice.sessions.length ? t('history.noMatchesHint') : t('history.emptyHint') }}</p><el-button v-if="!practice.sessions.length" class="soft-button" @click="router.push('/practice/setup')">{{ t('history.firstPractice') }}</el-button></div>
    <div v-if="practice.sessions.length" class="retention-footnote"><el-icon><Lock /></el-icon>{{ t('history.retention') }}</div>
  </section>
</template>

<style scoped>
.history-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 14px; padding: 11px 13px; box-shadow: none; }.search-input { max-width: 290px; }.search-input :deep(.el-input__wrapper) { min-height: 34px; }.search-input :deep(.el-input__inner) { font-size: 13px; }.status-filter :deep(.el-radio-button__inner) { padding: 8px 11px; border-color: #e4e9e5; box-shadow: none !important; font-size: 13px; }.status-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--green); background: var(--green-soft); color: var(--green-dark); }
.history-list { display: grid; gap: 9px; }.history-row { display: flex; min-height: 82px; align-items: center; gap: 13px; padding: 12px 14px; box-shadow: none; }.history-icon { display: grid; width: 38px; height: 38px; flex: none; place-items: center; border-radius: 7px; background: var(--blue-soft); color: var(--blue); }.history-icon.completed { background: var(--green-soft); color: var(--green); }.history-main { min-width: 0; flex: 1; }.history-title-line { display: flex; align-items: center; gap: 9px; }.history-title-line strong { overflow: hidden; color: #344039; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }.history-subline { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-top: 7px; color: #909a95; font-size: 13px; }.history-subline i { width: 3px; height: 3px; border-radius: 50%; background: #bdc5bf; }.history-details { display: grid; width: 150px; flex: none; gap: 5px; }.history-details strong { color: #657169; font-size: 13px; font-weight: 500; }.history-details small { color: #a2aaa5; font-size: 12px; }.icon-action { display: grid; width: 31px; height: 31px; flex: none; place-items: center; border: 1px solid transparent; border-radius: 5px; background: transparent; color: #99a29d; }.icon-action:hover { border-color: #f0dcd7; background: #fff7f5; color: #bf6350; }.open-button { min-width: 90px; }.history-empty { display: flex; min-height: 280px; align-items: center; flex-direction: column; justify-content: center; box-shadow: none; }.history-empty strong { color: #47534c; font-size: 14px; }.history-empty p { color: #8e9893; font-size: 13px; }.retention-footnote { display: flex; align-items: center; gap: 6px; margin-top: 13px; color: #87928b; font-size: 13px; }
@media(max-width: 850px) { .history-details { width: 118px; }.history-row { gap: 9px; padding: 11px; } }
@media(max-width: 680px) { .history-toolbar { align-items: stretch; flex-direction: column; }.search-input { max-width: none; }.status-filter { display: flex; }.status-filter :deep(.el-radio-button) { flex: 1; }.status-filter :deep(.el-radio-button__inner) { width: 100%; padding: 8px 4px; font-size: 12px; }.history-row { display: grid; grid-template-columns: 36px minmax(0,1fr) 31px; gap: 9px; }.history-icon { grid-row: span 2; }.history-main { grid-column: 2; }.history-details { grid-column: 2; width: auto; }.icon-action { grid-column: 3; grid-row: 1; }.open-button { grid-column: 2 / 4; width: 100%; } }
</style>
