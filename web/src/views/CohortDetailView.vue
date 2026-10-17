<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  archiveCohort, completionPercent, createAssignment, dueLabel, getCohort, listAssignments, listHelp,
  listPeerReviewTasks, listReceivedPeerReviews, optInPeerReview, roleLabel, sendHelpFeedback,
  setScoreSharing, submitPeerReview,
} from '../services/cohorts';

const route = useRoute();
const router = useRouter();
const cohort = ref(null);
const assignments = ref([]);
const loading = ref(true);
const creating = ref(false);
const showCreate = ref(false);
const form = reactive({ title: '', description: '', days: 5, itemsPerDay: 3, dueAt: '' });
const helpRequests = ref([]);
const peerTasks = ref([]);
const receivedReviews = ref([]);
const peerEnabled = ref(false);
const feedbackDrafts = reactive({});
const reviewTarget = ref(null);
const reviewForm = reactive({ clarity: 3, relevance: 3, depth: 3, suggestion: '' });
const userName = localStorage.getItem('userName') || localStorage.getItem('username') || '';

const isCoach = computed(() => cohort.value?.role === 'coach');
const me = computed(() => cohort.value?.members?.find((member) => member.role === 'learner') || null);
const shareScores = computed(() => cohort.value?.members?.find((member) => member.role === 'learner')?.shareScores || false);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    cohort.value = await getCohort(route.params.id);
    assignments.value = await listAssignments(route.params.id);
    const me = cohort.value.members?.find((member) => member.owner === userName);
    peerEnabled.value = Boolean(me?.peerReviewEnabled);
    helpRequests.value = await listHelp(route.params.id, isCoach.value ? 'open' : '');
    if (!isCoach.value) {
      receivedReviews.value = await listReceivedPeerReviews(route.params.id);
      peerTasks.value = peerEnabled.value ? await listPeerReviewTasks(route.params.id, 5) : [];
    }
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

async function togglePeerReview(value) {
  try {
    await optInPeerReview(cohort.value.id, value);
    await load();
    ElMessage.success(value ? '已开启同伴互评' : '已关闭同伴互评');
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  }
}

async function sendFeedback(item) {
  const text = (feedbackDrafts[item.id] || '').trim();
  if (!text) return ElMessage.warning('请先写一条反馈');
  try {
    await sendHelpFeedback(cohort.value.id, item.id, text);
    feedbackDrafts[item.id] = '';
    await load();
    ElMessage.success('反馈已发送');
  } catch (error) {
    ElMessage.error(error?.message || '发送失败');
  }
}

function openReview(task) {
  reviewTarget.value = task;
  reviewForm.clarity = 3;
  reviewForm.relevance = 3;
  reviewForm.depth = 3;
  reviewForm.suggestion = '';
}

async function submitReview() {
  if (!reviewTarget.value || !reviewForm.suggestion.trim()) return ElMessage.warning('请写一句具体建议');
  try {
    await submitPeerReview(cohort.value.id, {
      answerId: reviewTarget.value.answerId,
      clarity: reviewForm.clarity,
      relevance: reviewForm.relevance,
      depth: reviewForm.depth,
      suggestion: reviewForm.suggestion.trim(),
    });
    reviewTarget.value = null;
    await load();
    ElMessage.success('互评已提交，对方会匿名看到');
  } catch (error) {
    ElMessage.error(error?.message || '提交失败');
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
      <div v-if="!isCoach" class="panel sharing-card peer-sharing">
        <div>
          <strong>参与匿名同伴互评</strong>
          <small>开启后，其他成员只能看到你的转写，不会看到录像；你也可以评价别人的转写。</small>
        </div>
        <el-switch :model-value="peerEnabled" @change="togglePeerReview" />
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

      <section v-if="isCoach" class="panel help-queue">
        <div class="section-title"><span>待反馈求助</span><small>{{ helpRequests.length }} 条</small></div>
        <div v-if="helpRequests.length" class="help-list">
          <article v-for="item in helpRequests" :key="item.id" class="help-row">
            <div class="help-head"><strong>{{ item.owner }} · 第 {{ item.questionIndex + 1 }} 题</strong><small>{{ dueLabel(item.createdAt) }}</small></div>
            <p class="help-question">{{ item.question }}</p>
            <p class="help-transcript">{{ item.transcript }}</p>
            <p v-if="item.note" class="help-note">学员补充：{{ item.note }}</p>
            <div class="help-feedback">
              <el-input v-model="feedbackDrafts[item.id]" type="textarea" :rows="2" maxlength="2000" placeholder="给学员一条具体、可执行的反馈" />
              <el-button class="primary-button" @click="sendFeedback(item)">发送反馈</el-button>
            </div>
          </article>
        </div>
        <p v-else class="muted-copy">目前没有学员求助。</p>
      </section>

      <section v-if="!isCoach && helpRequests.length" class="panel help-queue">
        <div class="section-title"><span>我的求助</span><small>{{ helpRequests.length }} 条</small></div>
        <div class="help-list">
          <article v-for="item in helpRequests" :key="item.id" class="help-row">
            <div class="help-head"><strong>第 {{ item.questionIndex + 1 }} 题 · {{ item.status === 'answered' ? '已回复' : '等待助教' }}</strong><small>{{ dueLabel(item.createdAt) }}</small></div>
            <p class="help-question">{{ item.question }}</p>
            <p v-if="item.feedback" class="help-feedback-text">助教反馈：{{ item.feedback }}</p>
          </article>
        </div>
      </section>

      <section v-if="!isCoach && peerEnabled" class="panel peer-panel">
        <div class="section-title"><span>待我互评</span><small>只显示转写，匿名</small></div>
        <div v-if="peerTasks.length" class="peer-task-list">
          <article v-for="task in peerTasks" :key="task.answerId" class="peer-task">
            <div class="help-head"><strong>{{ task.alias }} · {{ task.question }}</strong><button class="text-action" @click="openReview(task)">评价这条</button></div>
            <p>{{ task.transcript }}</p>
          </article>
        </div>
        <p v-else class="muted-copy">暂时没有待评价的回答，等同伴完成练习后再来看看。</p>

        <div v-if="reviewTarget" class="review-form">
          <div class="review-scores">
            <label>表达清晰 <el-rate v-model="reviewForm.clarity" :max="5" /></label>
            <label>切题程度 <el-rate v-model="reviewForm.relevance" :max="5" /></label>
            <label>细节深度 <el-rate v-model="reviewForm.depth" :max="5" /></label>
          </div>
          <el-input v-model="reviewForm.suggestion" type="textarea" :rows="2" maxlength="500" placeholder="写一句具体建议（匿名）" />
          <div class="form-actions"><el-button class="soft-button" @click="reviewTarget = null">取消</el-button><el-button class="primary-button" @click="submitReview">提交互评</el-button></div>
        </div>
      </section>

      <section v-if="!isCoach && receivedReviews.length" class="panel peer-panel">
        <div class="section-title"><span>收到的互评</span><small>匿名</small></div>
        <article v-for="review in receivedReviews" :key="review.id" class="received-review">
          <div class="help-head"><strong>{{ review.reviewerAlias }}</strong><small>清晰 {{ review.clarity }} · 切题 {{ review.relevance }} · 深度 {{ review.depth }}</small></div>
          <p>{{ review.suggestion }}</p>
        </article>
      </section>
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
.peer-sharing { margin-top: -8px; }
.help-queue, .peer-panel { display: grid; gap: 10px; margin-top: 16px; padding: 16px 17px; box-shadow: none; }
.help-list, .peer-task-list { display: grid; gap: 9px; }
.help-row, .peer-task { display: grid; gap: 6px; padding: 10px 11px; border: 1px solid #eef1ee; border-radius: 7px; }
.help-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.help-head strong { color: #46524c; font-size: 13px; }
.help-head small { color: #98a29d; font-size: 11px; }
.help-question { margin: 0; color: #34403a; font-size: 13px; font-weight: 550; }
.help-transcript, .peer-task p { margin: 0; color: #7d8982; font-size: 12px; line-height: 1.7; }
.help-note { margin: 0; padding: 6px 8px; border-radius: 5px; background: #fdf6ea; color: #9a6b37; font-size: 11px; }
.help-feedback { display: flex; gap: 8px; align-items: flex-end; }
.help-feedback .el-input { flex: 1; }
.help-feedback-text { margin: 0; padding: 8px 10px; border-radius: 6px; background: #f0f8f4; color: #4d7a5e; font-size: 12px; line-height: 1.7; }
.muted-copy { margin: 0; color: #98a29d; font-size: 12px; }
.peer-task { background: #fbfcfb; }
.text-action { padding: 0; border: 0; background: transparent; color: var(--green); font-size: 12px; }
.review-form { display: grid; gap: 9px; padding: 12px; border-radius: 7px; background: #f7f9f8; }
.review-scores { display: flex; flex-wrap: wrap; gap: 14px; }
.review-scores label { display: flex; align-items: center; gap: 6px; color: #5d6a64; font-size: 12px; }
.received-review { padding: 9px 10px; border-bottom: 1px dashed #eef1ee; }
.received-review:last-child { border-bottom: 0; }
.received-review p { margin: 5px 0 0; color: #5d6a64; font-size: 12px; line-height: 1.7; }
@media (max-width: 760px) { .form-grid { grid-template-columns: 1fr 1fr; }.member-row { grid-template-columns: 80px 40px 1fr 40px; }.member-row small { grid-column: 1 / -1; text-align: left; } }
@media (max-width: 620px) { .help-feedback { align-items: stretch; flex-direction: column; }.help-feedback .el-button { align-self: flex-end; } }
</style>
