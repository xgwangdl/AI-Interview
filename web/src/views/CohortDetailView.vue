<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  archiveCohort, completionPercent, createAssignment, dueLabel, getCohort, listAssignments, listHelp,
  listPeerReviewTasks, listReceivedPeerReviews, optInPeerReview, roleLabel, sendHelpFeedback,
  setScoreSharing, submitPeerReview,
} from '../services/cohorts';
import { useI18n } from 'vue-i18n';

const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();
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
    ElMessage.error(error?.message || t('cohorts.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function copyInvite() {
  try {
    await navigator.clipboard.writeText(cohort.value.inviteCode);
    ElMessage.success(t('cohorts.copySuccess'));
  } catch {
    ElMessage.info(t('cohorts.inviteInfo', { code: cohort.value.inviteCode }));
  }
}

async function toggleSharing(value) {
  try {
    await setScoreSharing(cohort.value.id, value);
    await load();
    ElMessage.success(value ? t('cohorts.shareOn') : t('cohorts.shareOff'));
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.saveFailed'));
  }
}

async function submitAssignment() {
  if (!form.title.trim()) return ElMessage.warning(t('cohorts.titleRequired'));
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
    ElMessage.success(t('cohorts.assigned'));
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.assignFailed'));
  } finally {
    creating.value = false;
  }
}

async function togglePeerReview(value) {
  try {
    await optInPeerReview(cohort.value.id, value);
    await load();
    ElMessage.success(value ? t('cohorts.peerOn') : t('cohorts.peerOff'));
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.saveFailed'));
  }
}

async function sendFeedback(item) {
  const text = (feedbackDrafts[item.id] || '').trim();
  if (!text) return ElMessage.warning(t('cohorts.feedbackRequired'));
  try {
    await sendHelpFeedback(cohort.value.id, item.id, text);
    feedbackDrafts[item.id] = '';
    await load();
    ElMessage.success(t('cohorts.feedbackSent'));
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.submitFailed'));
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
  if (!reviewTarget.value || !reviewForm.suggestion.trim()) return ElMessage.warning(t('cohorts.suggestionRequired'));
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
    ElMessage.success(t('cohorts.reviewSubmitted'));
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.submitFailed'));
  }
}

async function archive() {
  try {
    await ElMessageBox.confirm(t('cohorts.archiveConfirmBody'), t('cohorts.archiveConfirmTitle'), {
      confirmButtonText: t('cohorts.archive'), cancelButtonText: t('common.cancel'), type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await archiveCohort(cohort.value.id);
    ElMessage.success(t('cohorts.archived'));
    router.replace('/cohorts');
  } catch (error) {
    ElMessage.error(error?.message || t('cohorts.archiveFailed'));
  }
}
</script>

<template>
  <section v-loading="loading" class="cohort-detail">
    <template v-if="cohort">
      <div class="page-heading">
        <div>
          <button class="back-link" @click="router.push('/cohorts')"><el-icon><ArrowLeft /></el-icon>{{ t('cohorts.back') }}</button>
          <h1>{{ cohort.name }}</h1>
          <p>{{ t('cohorts.summary', { members: cohort.memberCount, role: roleLabel(cohort.role, t) }) }}</p>
        </div>
        <div class="heading-actions">
          <el-button v-if="isCoach" class="soft-button" @click="showCreate = !showCreate">
            <el-icon><Plus /></el-icon>{{ t('cohorts.assign') }}
          </el-button>
          <el-button class="soft-button invite-button" @click="copyInvite"><el-icon><CopyDocument /></el-icon>{{ cohort.inviteCode }}</el-button>
          <el-button v-if="isCoach" class="soft-button" @click="archive">{{ t('cohorts.archive') }}</el-button>
        </div>
      </div>

      <div v-if="!isCoach" class="panel sharing-card">
        <div>
          <strong>{{ t('cohorts.shareTitle') }}</strong>
          <small>{{ t('cohorts.shareHint') }}</small>
        </div>
        <el-switch :model-value="shareScores" @change="toggleSharing" />
      </div>
      <div v-if="!isCoach" class="panel sharing-card peer-sharing">
        <div>
          <strong>{{ t('cohorts.peerTitle') }}</strong>
          <small>{{ t('cohorts.peerHint') }}</small>
        </div>
        <el-switch :model-value="peerEnabled" @change="togglePeerReview" />
      </div>

      <section v-if="isCoach && showCreate" class="panel assignment-form">
        <div class="section-title"><span>{{ t('cohorts.newAssignment') }}</span><small>{{ t('cohorts.newAssignmentHint') }}</small></div>
        <div class="form-grid">
          <el-form-item :label="t('cohorts.assignmentTitle')"><el-input v-model="form.title" maxlength="200" :placeholder="t('cohorts.assignmentPlaceholder')" /></el-form-item>
          <el-form-item :label="t('cohorts.dueAt')"><el-date-picker v-model="form.dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" :placeholder="t('cohorts.optional')" /></el-form-item>
          <el-form-item :label="t('cohorts.days')"><el-input-number v-model="form.days" :min="1" :max="14" /></el-form-item>
          <el-form-item :label="t('cohorts.itemsPerDay')"><el-input-number v-model="form.itemsPerDay" :min="1" :max="5" /></el-form-item>
        </div>
        <el-form-item :label="t('cohorts.description')"><el-input v-model="form.description" type="textarea" :rows="2" maxlength="2000" /></el-form-item>
        <div class="form-actions"><el-button class="soft-button" @click="showCreate = false">{{ t('cohorts.cancel') }}</el-button><el-button class="primary-button" :loading="creating" @click="submitAssignment">{{ t('cohorts.assign') }}</el-button></div>
      </section>

      <div class="section-title"><span>{{ t('cohorts.assignments') }}</span><small>{{ t('cohorts.countAssignments', { count: assignments.length }) }}</small></div>
      <div v-if="assignments.length" class="assignment-list">
        <article v-for="assignment in assignments" :key="assignment.id" class="panel assignment-card">
          <div class="assignment-head">
            <div><strong>{{ assignment.title }}</strong><small>{{ t('cohorts.due', { date: dueLabel(assignment.dueAt, t, locale) }) }}</small></div>
            <el-tag v-if="!isCoach" size="small">{{ t('cohorts.myCompleted', { completed: assignment.myCompleted, total: assignment.myTotal }) }}</el-tag>
            <el-tag v-else size="small">{{ t('cohorts.memberCompleted', { completed: assignment.completedMembers, total: assignment.memberCount }) }}</el-tag>
          </div>
          <p v-if="assignment.description">{{ assignment.description }}</p>
          <div class="assignment-progress">
            <span><i :style="{ width: `${completionPercent(assignment)}%` }"></i></span>
            <small>{{ t('cohorts.membersCompleted', { completed: assignment.completedMembers, total: assignment.memberCount }) }}</small>
          </div>
          <div v-if="isCoach" class="member-progress">
            <div v-for="member in assignment.progress" :key="member.owner" class="member-row">
              <span class="member-name">{{ member.owner }}</span>
              <span class="member-role">{{ roleLabel(member.role, t) }}</span>
              <span class="member-bar"><i :style="{ width: `${member.percent}%` }"></i></span>
              <strong>{{ member.completed }}/{{ member.total }}</strong>
              <small v-if="member.shareScores">{{ t('cohorts.points', { score: member.averageScore ?? '—' }) }}</small>
              <small v-else class="muted">{{ t('cohorts.notShared') }}</small>
            </div>
          </div>
          <div v-if="!isCoach" class="assignment-actions">
            <el-button class="primary-button" @click="router.push('/today')">{{ t('cohorts.goToday') }}</el-button>
          </div>
        </article>
      </div>
      <div v-else class="panel empty-assignment">
        <strong>{{ isCoach ? t('cohorts.noAssignmentsCoach') : t('cohorts.noAssignmentsLearner') }}</strong>
        <p>{{ isCoach ? t('cohorts.noAssignmentsCoachHint') : t('cohorts.noAssignmentsLearnerHint') }}</p>
      </div>

      <section v-if="isCoach" class="panel help-queue">
        <div class="section-title"><span>{{ t('cohorts.helpQueue') }}</span><small>{{ t('cohorts.countItems', { count: helpRequests.length }) }}</small></div>
        <div v-if="helpRequests.length" class="help-list">
          <article v-for="item in helpRequests" :key="item.id" class="help-row">
            <div class="help-head"><strong>{{ t('cohorts.questionNth', { owner: item.owner, number: item.questionIndex + 1 }) }}</strong><small>{{ dueLabel(item.createdAt, t, locale) }}</small></div>
            <p class="help-question">{{ item.question }}</p>
            <p class="help-transcript">{{ item.transcript }}</p>
            <p v-if="item.note" class="help-note">{{ t('cohorts.learnerNote', { note: item.note }) }}</p>
            <div class="help-feedback">
              <el-input v-model="feedbackDrafts[item.id]" type="textarea" :rows="2" maxlength="2000" :placeholder="t('cohorts.feedbackPlaceholder')" />
              <el-button class="primary-button" @click="sendFeedback(item)">{{ t('cohorts.sendFeedback') }}</el-button>
            </div>
          </article>
        </div>
        <p v-else class="muted-copy">{{ t('cohorts.noHelp') }}</p>
      </section>

      <section v-if="!isCoach && helpRequests.length" class="panel help-queue">
        <div class="section-title"><span>{{ t('cohorts.myHelp') }}</span><small>{{ t('cohorts.countItems', { count: helpRequests.length }) }}</small></div>
        <div class="help-list">
          <article v-for="item in helpRequests" :key="item.id" class="help-row">
            <div class="help-head"><strong>{{ t('coding.questionNth', { number: item.questionIndex + 1 }) }} · {{ item.status === 'answered' ? t('cohorts.answered') : t('cohorts.waitingCoach') }}</strong><small>{{ dueLabel(item.createdAt, t, locale) }}</small></div>
            <p class="help-question">{{ item.question }}</p>
            <p v-if="item.feedback" class="help-feedback-text">{{ t('cohorts.coachFeedback', { text: item.feedback }) }}</p>
          </article>
        </div>
      </section>

      <section v-if="!isCoach && peerEnabled" class="panel peer-panel">
        <div class="section-title"><span>{{ t('cohorts.peerTasks') }}</span><small>{{ t('cohorts.anonymousTranscripts') }}</small></div>
        <div v-if="peerTasks.length" class="peer-task-list">
          <article v-for="task in peerTasks" :key="task.answerId" class="peer-task">
            <div class="help-head"><strong>{{ task.alias }} · {{ task.question }}</strong><button class="text-action" @click="openReview(task)">{{ t('cohorts.reviewThis') }}</button></div>
            <p>{{ task.transcript }}</p>
          </article>
        </div>
        <p v-else class="muted-copy">{{ t('cohorts.noPeerTasks') }}</p>

        <div v-if="reviewTarget" class="review-form">
          <div class="review-scores">
            <label>{{ t('cohorts.clarity') }} <el-rate v-model="reviewForm.clarity" :max="5" /></label>
            <label>{{ t('cohorts.relevance') }} <el-rate v-model="reviewForm.relevance" :max="5" /></label>
            <label>{{ t('cohorts.depth') }} <el-rate v-model="reviewForm.depth" :max="5" /></label>
          </div>
          <el-input v-model="reviewForm.suggestion" type="textarea" :rows="2" maxlength="500" :placeholder="t('cohorts.reviewPlaceholder')" />
          <div class="form-actions"><el-button class="soft-button" @click="reviewTarget = null">{{ t('common.cancel') }}</el-button><el-button class="primary-button" @click="submitReview">{{ t('cohorts.submitReview') }}</el-button></div>
        </div>
      </section>

      <section v-if="!isCoach && receivedReviews.length" class="panel peer-panel">
        <div class="section-title"><span>{{ t('cohorts.receivedReviews') }}</span><small>{{ t('cohorts.anonymous') }}</small></div>
        <article v-for="review in receivedReviews" :key="review.id" class="received-review">
          <div class="help-head"><strong>{{ review.reviewerAlias }}</strong><small>{{ t('cohorts.reviewScores', { clarity: review.clarity, relevance: review.relevance, depth: review.depth }) }}</small></div>
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
