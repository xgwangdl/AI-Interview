<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  archiveCohort, completionPercent, createAssignment, dueLabel, getCohort, listAssignments, roleLabel, setScoreSharing,
} from '../services/cohorts';

const route = useRoute();
const router = useRouter();
const cohort = ref(null);
const assignments = ref([]);
const loading = ref(true);
const creating = ref(false);
const showCreate = ref(false);
const form = reactive({ title: '', description: '', days: 5, itemsPerDay: 3, dueAt: '' });

const isCoach = computed(() => cohort.value?.role === 'coach');
const me = computed(() => cohort.value?.members?.find((member) => member.role === 'learner') || null);
const shareScores = computed(() => cohort.value?.members?.find((member) => member.role === 'learner')?.shareScores || false);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    cohort.value = await getCohort(route.params.id);
    assignments.value = await listAssignments(route.params.id);
  } catch (error) {
    ElMessage.error(error?.message || '无法读取学习小组');
  } finally {
    loading.value = false;
  }
}

async function copyInvite() {
  try {
    await navigator.clipboard.writeText(cohort.value.inviteCode);
    ElMessage.success('邀请码已复制');
  } catch {
    ElMessage.info(`邀请码：${cohort.value.inviteCode}`);
  }
}

async function toggleSharing(value) {
  try {
    await setScoreSharing(cohort.value.id, value);
    await load();
    ElMessage.success(value ? '已向助教共享分数' : '已关闭分数共享');
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  }
}

async function submitAssignment() {
  if (!form.title.trim()) return ElMessage.warning('请填写作业标题');
  creating.value = true;
  try {
    await createAssignment(cohort.value.id, {
      title: form.title.trim(),
      description: form.description.trim() || null,
      days: form.days,
      itemsPerDay: form.itemsPerDay,
      dueAt: form.dueAt ? new Date(form.dueAt).toISOString() : null,
    });
    showCreate.value = false;
    form.title = '';
    form.description = '';
    await load();
    ElMessage.success('作业已布置');
  } catch (error) {
    ElMessage.error(error?.message || '布置作业失败');
  } finally {
    creating.value = false;
  }
}

async function archive() {
  try {
    await ElMessageBox.confirm('归档后小组不再出现在你的列表，历史作业仍保留。', '归档小组？', {
      confirmButtonText: '归档', cancelButtonText: '取消', type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await archiveCohort(cohort.value.id);
    ElMessage.success('小组已归档');
    router.replace('/cohorts');
  } catch (error) {
    ElMessage.error(error?.message || '归档失败');
  }
}
</script>

<template>
  <section v-loading="loading" class="cohort-detail">
    <template v-if="cohort">
      <div class="page-heading">
        <div>
          <button class="back-link" @click="router.push('/cohorts')"><el-icon><ArrowLeft /></el-icon>学习小组</button>
          <h1>{{ cohort.name }}</h1>
          <p>{{ cohort.memberCount }} 位成员 · {{ roleLabel(cohort.role) }} · 作业会自动进入学员的今日训练</p>
        </div>
        <div class="heading-actions">
          <el-button v-if="isCoach" class="soft-button" @click="showCreate = !showCreate">
            <el-icon><Plus /></el-icon>布置作业
          </el-button>
          <el-button class="soft-button invite-button" @click="copyInvite"><el-icon><CopyDocument /></el-icon>{{ cohort.inviteCode }}</el-button>
          <el-button v-if="isCoach" class="soft-button" @click="archive">归档</el-button>
        </div>
      </div>

      <div v-if="!isCoach" class="panel sharing-card">
        <div>
          <strong>向助教共享分数</strong>
          <small>默认关闭。开启后助教能看到你的平均分，但看不到录像和转写。</small>
        </div>
        <el-switch :model-value="shareScores" @change="toggleSharing" />
      </div>

      <section v-if="isCoach && showCreate" class="panel assignment-form">
        <div class="section-title"><span>布置新作业</span><small>会为每位学员生成每日训练项</small></div>
        <div class="form-grid">
          <el-form-item label="作业标题"><el-input v-model="form.title" maxlength="200" placeholder="例如：本周项目追问 5 天训练" /></el-form-item>
          <el-form-item label="截止时间"><el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="选填" /></el-form-item>
          <el-form-item label="天数"><el-input-number v-model="form.days" :min="1" :max="14" /></el-form-item>
          <el-form-item label="每天题目"><el-input-number v-model="form.itemsPerDay" :min="1" :max="5" /></el-form-item>
        </div>
        <el-form-item label="说明（选填）"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
        <div class="form-actions"><el-button class="soft-button" @click="showCreate = false">取消</el-button><el-button class="primary-button" :loading="creating" @click="submitAssignment">布置作业</el-button></div>
      </section>

      <div class="section-title"><span>作业</span><small>{{ assignments.length }} 个</small></div>
      <div v-if="assignments.length" class="assignment-list">
        <article v-for="assignment in assignments" :key="assignment.id" class="panel assignment-card">
          <div class="assignment-head">
            <div><strong>{{ assignment.title }}</strong><small>截止 {{ dueLabel(assignment.dueAt) }}</small></div>
            <el-tag v-if="!isCoach" size="small">{{ assignment.myCompleted }}/{{ assignment.myTotal }} 已完成</el-tag>
            <el-tag v-else size="small">{{ assignment.completedMembers }}/{{ assignment.memberCount }} 人完成</el-tag>
          </div>
          <p v-if="assignment.description">{{ assignment.description }}</p>
          <div class="assignment-progress">
            <span><i :style="{ width: `${completionPercent(assignment)}%` }"></i></span>
            <small>{{ assignment.completedMembers }}/{{ assignment.memberCount }} 位成员完成</small>
          </div>
          <div v-if="isCoach" class="member-progress">
            <div v-for="member in assignment.progress" :key="member.owner" class="member-row">
              <span class="member-name">{{ member.owner }}</span>
              <span class="member-role">{{ roleLabel(member.role) }}</span>
              <span class="member-bar"><i :style="{ width: `${member.percent}%` }"></i></span>
              <strong>{{ member.completed }}/{{ member.total }}</strong>
              <small v-if="member.shareScores">{{ member.averageScore ?? '—' }} 分</small>
              <small v-else class="muted">未共享分数</small>
            </div>
          </div>
          <div v-if="!isCoach" class="assignment-actions">
            <el-button class="primary-button" @click="router.push('/today')">去今日训练</el-button>
          </div>
        </article>
      </div>
      <div v-else class="panel empty-assignment">
        <strong>{{ isCoach ? '还没有布置作业' : '助教还没有布置作业' }}</strong>
        <p>{{ isCoach ? '点右上角「布置作业」，生成 5 天 × 3 题之类的训练计划。' : '等助教布置后，作业会自动进入你的今日训练。' }}</p>
      </div>
    </template>
  </section>
</template>

<style scoped>
.cohort-detail { max-width: 1050px; }
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 6px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.heading-actions { display: flex; gap: 8px; }
.sharing-card { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 15px 17px; margin-bottom: 18px; box-shadow: none; }
.sharing-card strong { display: block; color: #46524c; font-size: 14px; }
.sharing-card small { display: block; margin-top: 4px; color: #98a29d; font-size: 12px; }
.assignment-form { display: grid; gap: 11px; padding: 17px 18px; margin-bottom: 18px; box-shadow: none; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr 100px 100px; gap: 10px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; }
.assignment-list { display: grid; gap: 12px; }
.assignment-card { display: grid; gap: 9px; padding: 15px 17px; box-shadow: none; }
.assignment-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.assignment-head strong { display: block; color: #34403a; font-size: 15px; }
.assignment-head small { display: block; margin-top: 4px; color: #98a29d; font-size: 11px; }
.assignment-card > p { margin: 0; color: #7d8982; font-size: 12px; line-height: 1.7; }
.assignment-progress { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 10px; }
.assignment-progress > span { display: block; height: 7px; overflow: hidden; border-radius: 4px; background: #eef1ee; }
.assignment-progress i { display: block; height: 100%; background: var(--green); }
.assignment-progress small { color: #98a29d; font-size: 11px; }
.member-progress { display: grid; gap: 6px; padding-top: 8px; border-top: 1px solid #eef1ee; }
.member-row { display: grid; grid-template-columns: 110px 45px 1fr 45px 75px; align-items: center; gap: 8px; }
.member-name { overflow: hidden; color: #46524c; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.member-role { color: #98a29d; font-size: 11px; }
.member-bar { display: block; height: 6px; overflow: hidden; border-radius: 3px; background: #eef1ee; }
.member-bar i { display: block; height: 100%; background: var(--green); }
.member-row strong { color: #5d6a64; font-size: 11px; text-align: right; }
.member-row small { color: #a9722f; font-size: 11px; text-align: right; }
.member-row small.muted { color: #b0b8b3; }
.assignment-actions { display: flex; justify-content: flex-end; }
.empty-assignment { display: grid; justify-items: center; gap: 7px; padding: 28px; text-align: center; box-shadow: none; }
.empty-assignment strong { color: #46524c; font-size: 14px; }
.empty-assignment p { margin: 0; color: #98a29d; font-size: 12px; }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr 1fr; }.member-row { grid-template-columns: 80px 40px 1fr 40px; }.member-row small { grid-column: 1 / -1; text-align: left; } }
</style>
