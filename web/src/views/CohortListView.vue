<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createCohort, joinCohort, listCohorts, roleLabel } from '../services/cohorts';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
const cohorts = ref([]);
const loading = ref(true);
const creating = ref(false);
const joining = ref(false);
const name = ref('');
const inviteCode = ref('');

onMounted(load);

async function load() {
  loading.value = true;
  try {
    cohorts.value = await listCohorts();
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!name.value.trim()) return ElMessage.warning(t('cohorts.nameRequired'));
  creating.value = true;
  try {
    const cohort = await createCohort(name.value.trim());
    name.value = '';
    await load();
    router.push(`/cohorts/${cohort.id}`);
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.createFailed'));
  } finally {
    creating.value = false;
  }
}

async function join() {
  if (!inviteCode.value.trim()) return ElMessage.warning(t('cohorts.codeRequired'));
  joining.value = true;
  try {
    const cohort = await joinCohort(inviteCode.value.trim());
    inviteCode.value = '';
    await load();
    router.push(`/cohorts/${cohort.id}`);
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.joinFailed'));
  } finally {
    joining.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="cohort-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('cohorts.eyebrow') }}</div>
        <h1>{{ t('cohorts.title') }}</h1>
        <p>{{ t('cohorts.subtitle') }}</p>
      </div>
    </div>

    <div class="entry-grid">
      <section class="panel entry-card">
        <strong>{{ t('cohorts.createTitle') }}</strong>
        <p>{{ t('cohorts.createHint') }}</p>
        <div class="entry-row">
          <el-input v-model="name" maxlength="120" :placeholder="t('cohorts.namePlaceholder')" @keyup.enter="create" />
          <el-button class="primary-button" :loading="creating" @click="create">{{ t('cohorts.create') }}</el-button>
        </div>
      </section>
      <section class="panel entry-card">
        <strong>{{ t('cohorts.joinTitle') }}</strong>
        <p>{{ t('cohorts.joinHint') }}</p>
        <div class="entry-row">
          <el-input v-model="inviteCode" maxlength="16" :placeholder="t('cohorts.invitePlaceholder')" @keyup.enter="join" />
          <el-button class="soft-button" :loading="joining" @click="join">{{ t('cohorts.join') }}</el-button>
        </div>
      </section>
    </div>

    <div class="section-title"><span>{{ t('cohorts.myGroups') }}</span><small>{{ t('cohorts.countGroups', { count: cohorts.length }) }}</small></div>
    <div v-if="cohorts.length" class="cohort-grid">
      <button v-for="cohort in cohorts" :key="cohort.id" class="cohort-card panel" @click="router.push(`/cohorts/${cohort.id}`)">
        <div class="cohort-head"><strong>{{ cohort.name }}</strong><el-tag size="small">{{ roleLabel(cohort.role, t) }}</el-tag></div>
        <p>{{ t('cohorts.memberCount', { count: cohort.memberCount }) }}</p>
        <span>{{ cohort.role === 'coach' ? t('cohorts.inviteCode', { code: cohort.inviteCode }) : t('cohorts.clickProgress') }}</span>
      </button>
    </div>
    <div v-else class="panel empty-cohort">
      <el-icon><UserFilled /></el-icon>
      <strong>{{ t('cohorts.emptyTitle') }}</strong>
      <p>{{ t('cohorts.emptyHint') }}</p>
    </div>
  </section>
</template>

<style scoped>
.cohort-page { max-width: 1000px; }
.entry-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 24px; }
.entry-card { display: grid; gap: 7px; padding: 17px 18px; box-shadow: none; }
.entry-card strong { color: #34403a; font-size: 15px; }
.entry-card p { margin: 0; color: #8d9792; font-size: 12px; line-height: 1.7; }
.entry-row { display: flex; gap: 8px; margin-top: 5px; }
.entry-row .el-input { flex: 1; }
.cohort-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px; }
.cohort-card { display: grid; gap: 7px; padding: 15px 16px; border: 1px solid var(--line); text-align: left; box-shadow: none; }
.cohort-card:hover { border-color: #b9d3c2; background: #fbfdfb; }
.cohort-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.cohort-head strong { color: #34403a; font-size: 15px; }
.cohort-card p { margin: 0; color: #8d9792; font-size: 12px; }
.cohort-card > span { color: #a9722f; font-size: 11px; }
.empty-cohort { display: grid; justify-items: center; gap: 8px; padding: 38px 20px; text-align: center; box-shadow: none; }
.empty-cohort .el-icon { color: #98a29d; font-size: 26px; }
.empty-cohort strong { color: #46524c; font-size: 14px; }
.empty-cohort p { margin: 0; color: #8d9792; font-size: 12px; }
@media (max-width: 720px) { .entry-grid, .cohort-grid { grid-template-columns: 1fr; } }
</style>
