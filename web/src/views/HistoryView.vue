<script setup>
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { usePracticeStore } from '../stores/practice';

const router = useRouter();
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
    await ElMessageBox.confirm(`“${session.role}”的录像和练习记录将永久删除。此操作无法撤销。`, '删除练习记录？', { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' });
  } catch { return; }
  try {
    await practice.remove(session.id);
    ElMessage.success('练习记录与录像已删除');
  } catch (error) {
    ElMessage.error(error.message || '删除失败，请检查连接后重试。');
  }
}

function dateLabel(date) {
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(date));
}
</script>

<template>
  <section class="history-page">
    <div class="page-heading"><div><div class="eyebrow">PRACTICE LIBRARY</div><h1>练习记录</h1><p>查看每轮练习状态、回答片段与保留期限。</p></div><el-button class="primary-button" @click="router.push('/practice/setup')"><el-icon><Plus /></el-icon> 创建练习</el-button></div>
    <div class="history-toolbar panel"><el-input v-model="keyword" clearable placeholder="搜索岗位或公司" class="search-input"><template #prefix><el-icon><Search /></el-icon></template></el-input><el-radio-group v-model="status" class="status-filter"><el-radio-button value="all">全部 {{ practice.sessions.length }}</el-radio-button><el-radio-button value="completed">已完成 {{ practice.completed.length }}</el-radio-button><el-radio-button value="in_progress">进行中 {{ practice.inProgress.length }}</el-radio-button></el-radio-group></div>
    <div v-if="visibleSessions.length" class="history-list">
      <article v-for="session in visibleSessions" :key="session.id" class="history-row panel">
        <div class="history-icon" :class="session.status"><el-icon><VideoCamera /></el-icon></div>
        <div class="history-main"><div class="history-title-line"><strong>{{ session.role }}</strong><el-tag v-if="session.status === 'completed'" class="status-done" size="small">已完成</el-tag><el-tag v-else class="status-progress" size="small">进行中</el-tag></div><div class="history-subline"><span>{{ session.company || '自主练习' }}</span><i></i><span>{{ { behavioral: '综合行为', technical: '技术能力', manager: '管理岗位' }[session.interviewType] || '模拟面试' }}</span><i></i><span>{{ session.answers.length }}/{{ session.questionCount }} 道回答</span></div></div>
        <div class="history-details"><strong>{{ dateLabel(session.completedAt || session.createdAt) }}</strong><small>保留至 {{ new Intl.DateTimeFormat('zh-CN', { month: 'numeric', day: 'numeric' }).format(new Date(session.retentionUntil)) }}</small></div>
        <button class="icon-action" aria-label="删除练习记录" title="删除练习记录" @click="remove(session)"><el-icon><Delete /></el-icon></button><el-button class="soft-button open-button" @click="open(session)">{{ session.status === 'completed' ? '查看报告' : '继续练习' }} <el-icon><ArrowRight /></el-icon></el-button>
      </article>
    </div>
    <div v-else class="history-empty panel"><div class="empty-state-icon"><el-icon><FolderOpened /></el-icon></div><strong>{{ practice.sessions.length ? '没有符合条件的练习' : '练习记录会显示在这里' }}</strong><p>{{ practice.sessions.length ? '换一个关键词或筛选条件试试。' : '录制回答后，可以从这里回看每题视频并查看分析状态。' }}</p><el-button v-if="!practice.sessions.length" class="soft-button" @click="router.push('/practice/setup')">创建第一轮练习</el-button></div>
    <div v-if="practice.sessions.length" class="retention-footnote"><el-icon><Lock /></el-icon>录像与练习记录默认在创建后 7 天自动从服务器清理，也可随时手动删除。</div>
  </section>
</template>

<style scoped>
.history-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 14px; padding: 11px 13px; box-shadow: none; }.search-input { max-width: 290px; }.search-input :deep(.el-input__wrapper) { min-height: 34px; }.search-input :deep(.el-input__inner) { font-size: 13px; }.status-filter :deep(.el-radio-button__inner) { padding: 8px 11px; border-color: #e4e9e5; box-shadow: none !important; font-size: 13px; }.status-filter :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--green); background: var(--green-soft); color: var(--green-dark); }
.history-list { display: grid; gap: 9px; }.history-row { display: flex; min-height: 82px; align-items: center; gap: 13px; padding: 12px 14px; box-shadow: none; }.history-icon { display: grid; width: 38px; height: 38px; flex: none; place-items: center; border-radius: 7px; background: var(--blue-soft); color: var(--blue); }.history-icon.completed { background: var(--green-soft); color: var(--green); }.history-main { min-width: 0; flex: 1; }.history-title-line { display: flex; align-items: center; gap: 9px; }.history-title-line strong { overflow: hidden; color: #344039; font-size: 14px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }.history-subline { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-top: 7px; color: #909a95; font-size: 13px; }.history-subline i { width: 3px; height: 3px; border-radius: 50%; background: #bdc5bf; }.history-details { display: grid; width: 150px; flex: none; gap: 5px; }.history-details strong { color: #657169; font-size: 13px; font-weight: 500; }.history-details small { color: #a2aaa5; font-size: 12px; }.icon-action { display: grid; width: 31px; height: 31px; flex: none; place-items: center; border: 1px solid transparent; border-radius: 5px; background: transparent; color: #99a29d; }.icon-action:hover { border-color: #f0dcd7; background: #fff7f5; color: #bf6350; }.open-button { min-width: 90px; }.history-empty { display: flex; min-height: 280px; align-items: center; flex-direction: column; justify-content: center; box-shadow: none; }.history-empty strong { color: #47534c; font-size: 14px; }.history-empty p { color: #8e9893; font-size: 13px; }.retention-footnote { display: flex; align-items: center; gap: 6px; margin-top: 13px; color: #87928b; font-size: 13px; }
@media(max-width: 850px) { .history-details { width: 118px; }.history-row { gap: 9px; padding: 11px; } }
@media(max-width: 680px) { .history-toolbar { align-items: stretch; flex-direction: column; }.search-input { max-width: none; }.status-filter { display: flex; }.status-filter :deep(.el-radio-button) { flex: 1; }.status-filter :deep(.el-radio-button__inner) { width: 100%; padding: 8px 4px; font-size: 12px; }.history-row { display: grid; grid-template-columns: 36px minmax(0,1fr) 31px; gap: 9px; }.history-icon { grid-row: span 2; }.history-main { grid-column: 2; }.history-details { grid-column: 2; width: auto; }.icon-action { grid-column: 3; grid-row: 1; }.open-button { grid-column: 2 / 4; width: 100%; } }
</style>
