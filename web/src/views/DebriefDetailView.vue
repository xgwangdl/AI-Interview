<script setup>
import { onMounted, onUnmounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useI18n } from 'vue-i18n';
import {
  closeWeakPoint, deleteDebrief, getDebrief, QUESTION_CATEGORIES, updateDebriefQuestions,
} from '../services/jobRadar';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const deleting = ref(false);
const editing = ref(false);
const debrief = ref(null);
const questions = ref([]);
let pollTimer = null;

onMounted(load);
onUnmounted(() => clearTimeout(pollTimer));

async function load() {
  clearTimeout(pollTimer);
  loading.value = !debrief.value;
  try {
    debrief.value = await getDebrief(route.params.id);
    if (['pending', 'parsing'].includes(debrief.value.parseStatus)) pollTimer = setTimeout(load, 3500);
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.debriefDetail.loadFailed'));
  } finally {
    loading.value = false;
  }
}

function startEdit() {
  questions.value = (debrief.value?.questions || []).map(item => ({
    text: item.text, category: item.category, hard: item.hard, note: item.note || '',
  }));
  editing.value = true;
}

function addQuestion() {
  if (questions.value.length >= 10) return;
  questions.value.push({ text: '', category: 'other', hard: false, note: '' });
}

function removeQuestion(index) {
  questions.value.splice(index, 1);
}

async function saveQuestions() {
  const normalized = questions.value.map(item => ({ ...item, text: item.text.trim(), note: item.note.trim() || null }))
    .filter(item => item.text.length >= 5);
  saving.value = true;
  try {
    debrief.value = await updateDebriefQuestions(route.params.id, normalized);
    editing.value = false;
    ElMessage.success(t('jobRadar.debriefDetail.saved'));
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.debriefDetail.saveFailed'));
  } finally {
    saving.value = false;
  }
}

async function closePoint(point) {
  try {
    const updated = await closeWeakPoint(point.id);
    const index = debrief.value.weakPoints.findIndex(item => item.id === point.id);
    if (index >= 0) debrief.value.weakPoints.splice(index, 1, updated);
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.debriefDetail.closeFailed'));
  }
}

async function removeDebrief() {
  try {
    await ElMessageBox.confirm(t('jobRadar.debriefDetail.deleteConfirm'), t('jobRadar.debriefDetail.deleteTitle'), {
      confirmButtonText: t('common.delete'), cancelButtonText: t('common.cancel'), type: 'warning',
    });
    deleting.value = true;
    await deleteDebrief(route.params.id);
    ElMessage.success(t('jobRadar.debriefDetail.deleted'));
    router.replace('/debrief');
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error?.message || t('jobRadar.debriefDetail.deleteFailed'));
  } finally {
    deleting.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="debrief-detail-page">
    <div v-if="debrief" class="page-heading">
      <div>
        <button class="text-back" @click="router.push('/debrief')"><el-icon><ArrowLeft /></el-icon>{{ t('jobRadar.backToDebrief') }}</button>
        <h1>{{ debrief.roundName }}</h1>
        <p>{{ debrief.interviewDate }} · {{ t(`jobRadar.outcome.${debrief.outcome}`) }} · {{ t(`jobRadar.parseStatus.${debrief.parseStatus}`) }}</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="!editing" class="soft-button" @click="startEdit"><el-icon><EditPen /></el-icon>{{ t('jobRadar.debriefDetail.edit') }}</el-button>
        <el-button class="danger-button" :loading="deleting" :disabled="deleting" @click="removeDebrief"><el-icon><Delete /></el-icon>{{ t('jobRadar.debriefDetail.delete') }}</el-button>
      </div>
    </div>

    <div v-if="debrief" class="debrief-layout">
      <section class="panel question-panel">
        <div class="section-title">{{ t('jobRadar.debriefDetail.questions') }}<small>{{ t('jobRadar.debriefDetail.questionCount', { count: debrief.questions?.length || 0 }) }}</small></div>
        <div v-if="debrief.parseStatus === 'pending' || debrief.parseStatus === 'parsing'" class="parsing-state">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span><strong>{{ t('jobRadar.debriefDetail.parsing') }}</strong><small>{{ t('jobRadar.debriefDetail.parsingHint') }}</small></span>
        </div>

        <template v-if="editing">
          <div v-for="(question, index) in questions" :key="index" class="question-editor">
            <span class="question-number">{{ index + 1 }}</span>
            <div class="question-fields">
              <el-input v-model="question.text" type="textarea" :rows="2" maxlength="300" show-word-limit />
              <div class="question-options">
                <el-select v-model="question.category">
                  <el-option v-for="category in QUESTION_CATEGORIES" :key="category" :value="category" :label="t(`jobRadar.category.${category}`)" />
                </el-select>
                <el-checkbox v-model="question.hard">{{ t('jobRadar.debriefDetail.hard') }}</el-checkbox>
                <el-button text type="danger" @click="removeQuestion(index)"><el-icon><Delete /></el-icon></el-button>
              </div>
              <el-input v-model="question.note" maxlength="500" :placeholder="t('jobRadar.debriefDetail.noteHint')" />
            </div>
          </div>
          <div class="edit-actions">
            <el-button text :disabled="questions.length >= 10" @click="addQuestion"><el-icon><Plus /></el-icon>{{ t('jobRadar.debriefDetail.addQuestion') }}</el-button>
            <span></span>
            <el-button class="soft-button" @click="editing = false">{{ t('common.cancel') }}</el-button>
            <el-button class="primary-button" :loading="saving" :disabled="saving" @click="saveQuestions">{{ t('jobRadar.debriefDetail.confirmQuestions') }}</el-button>
          </div>
        </template>

        <template v-else-if="debrief.questions?.length">
          <article v-for="question in debrief.questions" :key="question.index" class="question-item">
            <span class="question-number">{{ question.index }}</span>
            <div><strong>{{ question.text }}</strong><p>{{ t(`jobRadar.category.${question.category}`) }}<template v-if="question.hard"> · {{ t('jobRadar.debriefDetail.hard') }}</template></p><small v-if="question.note">{{ question.note }}</small></div>
          </article>
        </template>
        <div v-else class="empty-state">
          <span class="empty-state-icon"><el-icon><Document /></el-icon></span>
          <strong>{{ t('jobRadar.debriefDetail.noQuestions') }}</strong><p>{{ t('jobRadar.debriefDetail.noQuestionsHint') }}</p>
          <el-button class="soft-button" @click="startEdit">{{ t('jobRadar.debriefDetail.manualEdit') }}</el-button>
        </div>
      </section>

      <aside class="aside-stack">
        <section class="panel weak-panel">
          <div class="section-title">{{ t('jobRadar.debriefDetail.weakPoints') }}<small>{{ t('jobRadar.debriefDetail.autoClose') }}</small></div>
          <div v-for="point in debrief.weakPoints || []" :key="point.id" class="weak-row" :class="{ closed: point.status === 'closed' }">
            <span><strong>{{ point.topic }}</strong><small>{{ t(`jobRadar.dimension.${point.dimensionKey}`) }} · {{ t('jobRadar.debriefDetail.weight', { weight: point.weight }) }}</small></span>
            <el-button v-if="point.status !== 'closed'" text @click="closePoint(point)"><el-icon><CircleCheck /></el-icon>{{ t('jobRadar.debriefDetail.close') }}</el-button>
            <el-tag v-else size="small" type="info">{{ t('jobRadar.debriefDetail.closed') }}</el-tag>
          </div>
          <p v-if="!debrief.weakPoints?.length" class="aside-empty">{{ t('jobRadar.debriefDetail.noWeakPoints') }}</p>
        </section>
        <section class="panel privacy-panel">
          <el-icon><Lock /></el-icon>
          <div><strong>{{ t('jobRadar.debriefDetail.privacyTitle') }}</strong><p>{{ t('jobRadar.debriefDetail.privacyText', { length: debrief.rawTextLength }) }}</p><small>{{ t('jobRadar.debriefDetail.ackAt', { time: new Date(debrief.privacyAcknowledgedAt).toLocaleString() }) }}</small></div>
        </section>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.debrief-detail-page { max-width: 1120px; min-height: 420px; }
.text-back { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 8px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.heading-actions { display: flex; gap: 8px; }
.danger-button.el-button { height: 38px; border-color: #ecd4ce; color: #b65c48; }
.debrief-layout { display: grid; grid-template-columns: minmax(0, 1.45fr) minmax(270px, .62fr); gap: 20px; align-items: start; }
.question-panel, .weak-panel { padding: 20px; }
.parsing-state { display: flex; align-items: center; gap: 11px; margin-bottom: 16px; padding: 13px; border-radius: 6px; background: var(--blue-soft); color: var(--blue); }
.parsing-state span, .weak-row span { display: flex; flex-direction: column; gap: 4px; }
.parsing-state small, .weak-row small { color: #7e8d95; font-size: 12px; }
.question-item { display: grid; grid-template-columns: 32px minmax(0, 1fr); gap: 10px; padding: 14px 0; border-top: 1px solid #edf0ed; }
.question-number { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 50%; background: var(--green-soft); color: var(--green-dark); font-size: 12px; font-weight: 700; }
.question-item strong { color: #37443d; font-size: 14px; line-height: 1.65; }
.question-item p, .question-item small { margin: 5px 0 0; color: #8a9590; font-size: 12px; }
.aside-stack { display: grid; gap: 15px; }
.weak-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 11px 0; border-top: 1px solid #edf0ed; }
.weak-row strong { color: #46534c; font-size: 13px; line-height: 1.55; }
.weak-row.closed { opacity: .62; }
.aside-empty { color: #9aa39f; font-size: 13px; line-height: 1.7; }
.privacy-panel { display: flex; gap: 10px; padding: 17px; color: var(--green); }
.privacy-panel strong { color: #3e4b44; font-size: 13px; }
.privacy-panel p { margin: 7px 0; color: #7d8882; font-size: 12px; line-height: 1.7; }
.privacy-panel small { color: #9ba49f; font-size: 11px; }
.question-editor { display: grid; grid-template-columns: 30px minmax(0, 1fr); gap: 10px; padding: 13px 0; border-top: 1px solid #edf0ed; }
.question-fields { display: grid; gap: 8px; }
.question-options { display: grid; grid-template-columns: minmax(150px, 1fr) auto 34px; gap: 8px; align-items: center; }
.edit-actions { display: grid; grid-template-columns: auto 1fr auto auto; gap: 8px; align-items: center; padding-top: 15px; border-top: 1px solid #edf0ed; }
@media (max-width: 880px) { .debrief-layout { grid-template-columns: 1fr; } }
@media (max-width: 560px) {
  .heading-actions { width: 100%; }
  .heading-actions .el-button { flex: 1; margin-left: 0; }
  .question-panel, .weak-panel { padding: 15px; }
  .question-options { grid-template-columns: 1fr auto; }
  .question-options .el-button { grid-column: 2; }
  .edit-actions { grid-template-columns: 1fr 1fr; }
  .edit-actions span { display: none; }
}
</style>
