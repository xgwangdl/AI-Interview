<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createCohort, joinCohort, listCohorts, roleLabel } from '../services/cohorts';

const router = useRouter();
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
    ElMessage.error(error?.message || '无法读取学习小组');
  } finally {
    loading.value = false;
  }
}

async function create() {
  if (!name.value.trim()) return ElMessage.warning('先给小组取个名字');
  creating.value = true;
  try {
    const cohort = await createCohort(name.value.trim());
    name.value = '';
    await load();
    router.push(`/cohorts/${cohort.id}`);
  } catch (error) {
    ElMessage.error(error?.message || '创建失败');
  } finally {
    creating.value = false;
  }
}

async function join() {
  if (!inviteCode.value.trim()) return ElMessage.warning('请输入邀请码');
  joining.value = true;
  try {
    const cohort = await joinCohort(inviteCode.value.trim());
    inviteCode.value = '';
    await load();
    router.push(`/cohorts/${cohort.id}`);
  } catch (error) {
    ElMessage.error(error?.message || '加入失败，请检查邀请码');
  } finally {
    joining.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="cohort-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">LEARNING GROUPS</div>
        <h1>学习小组</h1>
        <p>助教布置作业，学员完成训练；默认只共享完成状态，分数由你自己决定是否共享。</p>
      </div>
    </div>

    <div class="entry-grid">
      <section class="panel entry-card">
        <strong>创建小组</strong>
        <p>你是助教：生成邀请码、布置作业、查看完成情况。</p>
        <div class="entry-row">
          <el-input v-model="name" maxlength="120" placeholder="例如：2026 秋招 Java 训练营" @keyup.enter="create" />
          <el-button class="primary-button" :loading="creating" @click="create">创建</el-button>
        </div>
      </section>
      <section class="panel entry-card">
        <strong>加入小组</strong>
        <p>输入助教给你的邀请码，作业会自动进入你的今日训练。</p>
        <div class="entry-row">
          <el-input v-model="inviteCode" maxlength="16" placeholder="例如：AB3K9QZX" @keyup.enter="join" />
          <el-button class="soft-button" :loading="joining" @click="join">加入</el-button>
        </div>
      </section>
    </div>

    <div class="section-title"><span>我的小组</span><small>{{ cohorts.length }} 个</small></div>
    <div v-if="cohorts.length" class="cohort-grid">
      <button v-for="cohort in cohorts" :key="cohort.id" class="cohort-card panel" @click="router.push(`/cohorts/${cohort.id}`)">
        <div class="cohort-head"><strong>{{ cohort.name }}</strong><el-tag size="small">{{ roleLabel(cohort.role) }}</el-tag></div>
        <p>{{ cohort.memberCount }} 位成员</p>
        <span>{{ cohort.role === 'coach' ? `邀请码 ${cohort.inviteCode}` : '点击查看作业与进度' }}</span>
      </button>
    </div>
    <div v-else class="panel empty-cohort">
      <el-icon><UserFilled /></el-icon>
      <strong>还没有加入小组</strong>
      <p>如果助教给了你邀请码，在上方输入即可；也可以自己创建一个小组。</p>
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
