<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { listDebriefs } from '../services/jobRadar';

const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const items = ref([]);
const total = ref(0);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const result = await listDebriefs({ limit: 50 });
    items.value = result.items || [];
    total.value = result.total || 0;
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.debriefPage.loadFailed'));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="debrief-list-page">
    <div class="page-heading">
      <div>
        <button class="text-back" @click="router.push('/jobs')"><el-icon><ArrowLeft /></el-icon>{{ t('jobRadar.backToJobs') }}</button>
        <h1>{{ t('jobRadar.debriefPage.title') }}</h1>
        <p>{{ t('jobRadar.debriefPage.subtitle') }}</p>
      </div>
      <el-button class="primary-button" @click="router.push('/debrief/new')"><el-icon><Plus /></el-icon>{{ t('jobRadar.debriefPage.new') }}</el-button>
    </div>

    <div v-loading="loading" class="panel debrief-list">
      <button v-for="item in items" :key="item.id" class="debrief-row" @click="router.push(`/debrief/${item.id}`)">
        <span class="round-mark"><el-icon><ChatDotSquare /></el-icon></span>
        <span class="debrief-main">
          <strong>{{ item.roundName }} · {{ item.interviewDate }}</strong>
          <small>{{ t(`jobRadar.outcome.${item.outcome}`) }} · {{ t(`jobRadar.parseStatus.${item.parseStatus}`) }}</small>
        </span>
        <span class="weak-count" :class="{ empty: !item.weakPoints?.length }">
          <strong>{{ item.weakPoints?.length || 0 }}</strong><small>{{ t('jobRadar.debriefPage.weakPoints') }}</small>
        </span>
        <el-icon class="row-arrow"><ArrowRight /></el-icon>
      </button>
      <div v-if="!loading && !items.length" class="empty-state">
        <span class="empty-state-icon"><el-icon><DocumentAdd /></el-icon></span>
        <strong>{{ t('jobRadar.debriefPage.empty') }}</strong><p>{{ t('jobRadar.debriefPage.emptyHint') }}</p>
        <el-button class="primary-button" @click="router.push('/debrief/new')">{{ t('jobRadar.debriefPage.new') }}</el-button>
      </div>
    </div>
    <p v-if="total > items.length" class="list-note">{{ t('jobRadar.showing', { shown: items.length, total }) }}</p>
  </section>
</template>

<style scoped>
.debrief-list-page { max-width: 980px; }
.text-back { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 8px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.debrief-list { overflow: hidden; }
.debrief-row { display: grid; width: 100%; grid-template-columns: 44px minmax(0, 1fr) 70px 20px; gap: 13px; align-items: center; padding: 16px 19px; border: 0; border-bottom: 1px solid #edf0ed; background: #fff; text-align: left; }
.debrief-row:last-child { border-bottom: 0; }
.debrief-row:hover { background: #fafcf9; }
.round-mark { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 8px; background: var(--blue-soft); color: var(--blue); }
.debrief-main, .weak-count { display: flex; min-width: 0; flex-direction: column; gap: 5px; }
.debrief-main strong { overflow: hidden; color: #334039; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.debrief-main small, .weak-count small { color: #87918c; font-size: 12px; }
.weak-count { align-items: flex-end; }
.weak-count strong { color: var(--coral); font-size: 20px; }
.weak-count.empty strong { color: #aab2ae; }
.row-arrow { color: #a2aaa6; }
.list-note { margin: 12px 2px 0; color: #8e9893; font-size: 12px; text-align: right; }
@media (max-width: 520px) {
  .debrief-row { grid-template-columns: 38px minmax(0, 1fr) 55px; padding: 14px; }
  .row-arrow { display: none; }
}
</style>
