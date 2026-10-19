<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { extractResume, planQuestions, trySyncSession } from '../services/api';
import { buildQuestions } from '../data/questions';
import { DIMENSION_LABEL_KEYS, DIMENSION_LABELS } from '../services/practiceArchive';
import { getGoal } from '../services/goals';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const route = useRoute();
const { t } = useI18n();
const practice = usePracticeStore();
const formRef = ref();
const resumeName = ref('');
const resumeText = ref('');
const resumeStatus = ref('');
const form = reactive({ role: '', company: '', jobDescription: '', interviewType: 'behavioral', difficulty: 'standard', interviewerStyle: 'balanced', questionCount: 5, durationMinutes: 20, followUpEnabled: true, mode: 'per_question' });
const goalId = ref('');
watch(() => form.questionCount, (count) => { form.durationMinutes = count * 4; });
watch(() => form.mode, (mode) => {
  if (mode === 'continuous') {
    form.questionCount = 5;
    form.durationMinutes = 20;
    form.followUpEnabled = true;
  }
});
const rules = computed(() => ({
  role: [
    { required: true, message: t('setup.roleRequired'), trigger: 'blur' },
    { min: 2, max: 60, message: t('setup.roleLength'), trigger: 'blur' },
  ],
}));

/**
 * 从训练档案点「针对短板再练一场」过来时带着岗位与弱项维度。
 * 只影响出题倾向，用户可以随时清掉。
 */
const focusDimensions = ref([]);
const focusLabels = computed(() => focusDimensions.value.map((key) => (
  DIMENSION_LABEL_KEYS[key] ? t(DIMENSION_LABEL_KEYS[key]) : key
)));
/** 创建练习期间锁住按钮：出题 + 建会话可能要几秒，重复点会建出多场练习。 */
const submitting = ref(false);

onMounted(async () => {
  if (typeof route.query.role === 'string' && route.query.role) form.role = route.query.role;
  if (typeof route.query.company === 'string' && route.query.company) form.company = route.query.company;
  if (typeof route.query.goalId === 'string' && route.query.goalId) {
    goalId.value = route.query.goalId;
    try {
      const goal = await getGoal(goalId.value);
      form.role = goal.role || form.role;
      form.company = goal.company || form.company;
      form.jobDescription = goal.jobDescription || form.jobDescription;
    } catch {
      goalId.value = '';
    }
  }
  focusDimensions.value = String(route.query.focus || '')
    .split(',')
    .map((key) => key.trim())
    .filter((key) => DIMENSION_LABELS[key]);
});

async function onResumeChange(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.error(t('setup.resumeTooLarge'));
    event.target.value = '';
    return;
  }
  resumeName.value = file.name;
  resumeText.value = '';
  resumeStatus.value = t('setup.resumeParsing');
  try {
    const parsed = await extractResume(file);
    resumeText.value = parsed.text;
    resumeStatus.value = t('setup.resumeParsed', { count: parsed.characters });
  } catch {
    // 简历解析是增强项：失败时仍可只用岗位信息出题。
    resumeStatus.value = t('setup.resumeUnreadable');
  }
}

async function continueToDevices() {
  if (submitting.value) return;
  submitting.value = true;
  try {
    await formRef.value.validate();
    const questions = await resolveQuestions();
    const draft = practice.createDraft({ ...form, goalId: goalId.value || null, resumeName: resumeName.value, questions });
    const remote = await trySyncSession(draft);
    draft.id = remote.id;
    draft.createdAt = remote.createdAt;
    draft.retentionUntil = remote.retentionUntil;
    practice.saveDraft();
    router.push(`/practice/device-check?id=${draft.id}`);
  } catch (error) {
    ElMessage.error(error.message || t('setup.createFailed'));
  } finally {
    submitting.value = false;
  }
}

/**
 * 优先按岗位与 JD 让 AI 出题；模型不可用（未配置 Key、超时、返回不合法）时
 * 回退到内置题库，保证练习一定能开始。
 */
async function resolveQuestions() {
  try {
    const plan = await planQuestions({
      role: form.role,
      company: form.company,
      jobDescription: form.jobDescription,
      resumeName: resumeName.value,
      resumeText: resumeText.value,
      interviewType: form.interviewType,
      difficulty: form.difficulty,
      interviewerStyle: form.interviewerStyle,
      questionCount: form.questionCount,
      focusDimensions: focusDimensions.value,
    });
    if (plan?.questions?.length === form.questionCount) return plan.questions;
  } catch (error) {
    // 静默回退，不打断创建流程；但额度用完这件事必须说出来 ——
    // 否则用户只会觉得"这次的题怎么变水了"，也不知道额度是共享资源。
    if (error?.status === 429) {
      ElMessage.warning(t('setup.quotaWarning', {
        message: error.message || t('setup.quotaExhausted'),
      }));
    }
  }
  return buildQuestions(form.role, form.questionCount, form.interviewType);
}

</script>

<template>
  <section class="setup-page">
    <div class="page-heading"><div><div class="eyebrow">{{ t('setup.step') }}</div><h1>{{ t('setup.title') }}</h1><p>{{ t('setup.subtitle') }}</p></div><el-tag class="tag-muted">{{ t('setup.retention') }}</el-tag></div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="setup-grid">
      <div class="setup-main">
        <section v-if="focusLabels.length" class="panel focus-banner">
          <el-icon><Aim /></el-icon>
          <div>
            <strong>{{ t('setup.focusTitle', { dimensions: focusLabels.join(' / ') }) }}</strong>
            <small>{{ t('setup.focusHint') }}</small>
          </div>
          <button class="focus-clear" @click="focusDimensions = []">{{ t('setup.clear') }}</button>
        </section>
        <section class="form-section panel">
          <div class="form-section-title"><span class="number-badge">01</span><div><strong>{{ t('setup.jobInfo') }}</strong><small>{{ t('setup.jobInfoHint') }}</small></div></div>
          <div class="field-grid">
            <el-form-item :label="t('setup.role')" prop="role"><el-input v-model="form.role" :placeholder="t('setup.rolePlaceholder')" maxlength="60" show-word-limit /></el-form-item>
            <el-form-item :label="t('setup.company')"><el-input v-model="form.company" :placeholder="t('setup.companyPlaceholder')" maxlength="60" /></el-form-item>
          </div>
          <el-form-item :label="t('setup.jobDescription')"><el-input v-model="form.jobDescription" type="textarea" :rows="4" maxlength="3000" show-word-limit :placeholder="t('setup.jobDescriptionPlaceholder')" /></el-form-item>
          <div class="resume-upload-row">
            <div class="resume-copy"><div class="resume-icon"><el-icon><Document /></el-icon></div><div><strong>{{ resumeName || t('setup.resume') }}</strong><small>{{ resumeStatus || t('setup.resumeHint') }}</small></div></div>
            <label class="upload-label"><input type="file" accept=".pdf,.doc,.docx" @change="onResumeChange" /><el-icon><Upload /></el-icon>{{ t('setup.chooseFile') }}</label>
          </div>
        </section>

        <section class="form-section panel">
          <div class="form-section-title"><span class="number-badge">02</span><div><strong>{{ t('setup.preferences') }}</strong><small>{{ t('setup.preferencesHint') }}</small></div></div>
          <el-form-item :label="t('setup.mode')">
            <el-radio-group v-model="form.mode" class="choice-grid mode-grid">
              <el-radio-button value="per_question"><span class="choice-title">{{ t('setup.perQuestion') }}</span><span class="choice-subtitle">{{ t('setup.perQuestionHint') }}</span></el-radio-button>
              <el-radio-button value="continuous"><span class="choice-title">{{ t('setup.continuous') }}</span><span class="choice-subtitle">{{ t('setup.continuousHint') }}</span></el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item :label="t('setup.interviewType')"><el-radio-group v-model="form.interviewType" class="choice-grid">
            <el-radio-button value="behavioral"><span class="choice-title">{{ t('setup.behavioral') }}</span><span class="choice-subtitle">{{ t('setup.behavioralHint') }}</span></el-radio-button>
            <el-radio-button value="technical"><span class="choice-title">{{ t('setup.technical') }}</span><span class="choice-subtitle">{{ t('setup.technicalHint') }}</span></el-radio-button>
            <el-radio-button value="manager"><span class="choice-title">{{ t('setup.manager') }}</span><span class="choice-subtitle">{{ t('setup.managerHint') }}</span></el-radio-button>
          </el-radio-group></el-form-item>
          <el-form-item :label="t('setup.interviewerStyle')"><el-radio-group v-model="form.interviewerStyle" class="choice-grid">
            <el-radio-button value="balanced"><span class="choice-title">{{ t('setup.balanced') }}</span><span class="choice-subtitle">{{ t('setup.balancedHint') }}</span></el-radio-button>
            <el-radio-button value="direct"><span class="choice-title">{{ t('setup.direct') }}</span><span class="choice-subtitle">{{ t('setup.directHint') }}</span></el-radio-button>
            <el-radio-button value="supportive"><span class="choice-title">{{ t('setup.supportive') }}</span><span class="choice-subtitle">{{ t('setup.supportiveHint') }}</span></el-radio-button>
          </el-radio-group></el-form-item>
          <div class="field-grid lower-fields">
            <el-form-item :label="t('setup.difficulty')"><el-select v-model="form.difficulty"><el-option :label="t('setup.foundation')" value="foundation" /><el-option :label="t('setup.standard')" value="standard" /><el-option :label="t('setup.advanced')" value="advanced" /></el-select></el-form-item>
            <el-form-item :label="t('setup.followUp')">
              <el-switch v-model="form.followUpEnabled" />
              <span class="switch-hint">
                {{ form.followUpEnabled
                  ? t('setup.followUpOn')
                  : t('setup.followUpOff') }}
              </span>
            </el-form-item>
            <el-form-item :label="t('setup.questionCount')"><el-select v-model="form.questionCount"><el-option :value="3" :label="t('setup.questions3')" /><el-option :value="5" :label="t('setup.questions5')" /><el-option :value="8" :label="t('setup.questions8')" /></el-select></el-form-item>
          </div>
        </section>
      </div>
      <aside class="setup-aside">
        <div class="summary-card panel"><div class="section-title">{{ t('setup.summary') }}</div><div class="summary-role">{{ form.role || t('setup.pendingRole') }}</div><div class="summary-company">{{ form.company || t('setup.noCompany') }}</div><div class="summary-divider"></div><div class="summary-row"><span>{{ t('setup.modeLabel') }}</span><strong>{{ form.mode === 'continuous' ? t('setup.continuous') : t('setup.perQuestion') }}</strong></div><div class="summary-row"><span>{{ t('setup.typeLabel') }}</span><strong>{{ t(`setup.${form.interviewType}`) }}</strong></div><div class="summary-row"><span>{{ t('setup.countLabel') }}</span><strong>{{ t('setup.countValue', { count: form.questionCount }) }}</strong></div><div class="summary-row"><span>{{ t('setup.durationLabel') }}</span><strong>{{ t('setup.durationValue', { minutes: form.durationMinutes }) }}</strong></div><div class="summary-row"><span>{{ t('setup.storageLabel') }}</span><strong>{{ t('setup.storageValue') }}</strong></div></div>
        <div class="privacy-card"><el-icon><Lock /></el-icon><div><strong>{{ t('setup.privacyTitle') }}</strong><p>{{ t('setup.privacyText') }}</p></div></div>
        <el-button class="primary-button continue-button" :loading="submitting" :disabled="submitting"
                   @click="continueToDevices">{{ t('setup.continueDevices') }} <el-icon><ArrowRight /></el-icon></el-button>
      </aside>
    </el-form>
  </section>
</template>

<style scoped>
.setup-grid { display: grid; grid-template-columns: minmax(0, 1fr) 274px; align-items: start; gap: 18px; }
.setup-main { display: grid; gap: 15px; }.form-section { padding: 20px 21px 17px; box-shadow: none; }.form-section-title { display: flex; align-items: center; gap: 11px; margin-bottom: 20px; }.form-section-title > div { display: grid; gap: 4px; }.form-section-title strong { color: #34413a; font-size: 15px; }.form-section-title small { color: #87928c; font-size: 13px; }.number-badge { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 50%; background: var(--green-soft); color: var(--green); font-size: 13px; font-weight: 700; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; }.setup-page :deep(.el-form-item) { margin-bottom: 16px; }.setup-page :deep(.el-form-item__label) { height: auto; margin-bottom: 7px; color: #5e6a64; font-size: 14px; line-height: 1.3; }.setup-page :deep(.el-input__wrapper) { min-height: 38px; }.setup-page :deep(.el-textarea__inner) { min-height: 100px !important; padding: 11px; }.setup-page :deep(.el-input__count) { background: transparent; font-size: 13px; }
.resume-upload-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 2px; padding: 12px; border: 1px dashed #dce4de; border-radius: 6px; background: #fafcfb; }.resume-copy { display: flex; min-width: 0; align-items: center; gap: 10px; }.resume-icon { display: grid; width: 32px; height: 32px; flex: none; place-items: center; border-radius: 6px; background: var(--blue-soft); color: var(--blue); }.resume-copy strong { display: block; overflow: hidden; color: #47534d; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }.resume-copy small { display: block; margin-top: 4px; color: #98a19c; font-size: 13px; }.upload-label { display: inline-flex; flex: none; align-items: center; gap: 5px; color: var(--green); font-size: 13px; font-weight: 600; cursor: pointer; }.upload-label input { display: none; }
/* 面试类型与面试官风格共用同一套「标题 + 说明」卡片：
   标题 12px、说明 11px，说明文字要能换行，否则 Element 的 nowrap 会把长句挤成一行溢出。 */
.choice-grid { display: grid; width: 100%; grid-template-columns: repeat(3,1fr); gap: 9px; }.mode-grid { grid-template-columns: repeat(2,1fr); }.choice-grid :deep(.el-radio-button) { width: 100%; }.choice-grid :deep(.el-radio-button__inner) { display: grid; width: 100%; height: auto; min-height: 76px; align-content: center; gap: 5px; padding: 10px 11px; border: 1px solid #e2e7e3 !important; border-radius: 6px !important; box-shadow: none !important; text-align: left; white-space: normal; line-height: 1.45; }.choice-grid :deep(.el-radio-button:first-child .el-radio-button__inner),.choice-grid :deep(.el-radio-button:last-child .el-radio-button__inner) { border-left: 1px solid #e2e7e3; border-radius: 6px; }.choice-grid :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--green) !important; background: #f0f8f4; color: var(--green-dark); }.choice-title { font-size: 14px; font-weight: 600; }.choice-subtitle { color: #5f6d66; font-size: 14px; line-height: 1.5; }
.lower-fields { margin-top: 5px; }.lower-fields :deep(.el-select) { width: 100%; }
.setup-aside { position: sticky; top: 82px; }.summary-card { padding: 17px; box-shadow: none; }.summary-card .section-title { margin-bottom: 17px; }.summary-role { color: #35413b; font-size: 16px; font-weight: 650; }.summary-company { margin-top: 5px; color: #98a29d; font-size: 13px; }.summary-divider { margin: 16px 0 13px; border-top: 1px solid #edf0ed; }.summary-row { display: flex; justify-content: space-between; gap: 8px; margin: 12px 0; color: #818c86; font-size: 13px; }.summary-row strong { color: #4c5952; font-size: 13px; font-weight: 600; }.privacy-card { display: flex; gap: 10px; margin: 12px 0; padding: 14px; border: 1px solid #dcece2; border-radius: 6px; background: #eff7f1; color: var(--green); }.privacy-card > .el-icon { flex: none; margin-top: 2px; }.privacy-card strong { color: #3d7458; font-size: 13px; }.privacy-card p { margin: 5px 0 0; color: #718d7b; font-size: 13px; line-height: 1.6; }.continue-button { width: 100%; }
.focus-banner { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 12px; padding: 13px 15px; border-color: #f0dcc6; background: #fdf6ee; box-shadow: none; }
.focus-banner > .el-icon { flex: none; margin-top: 2px; color: #c07a34; font-size: 16px; }
.focus-banner strong { display: block; color: #a7622c; font-size: 13px; }
.focus-banner small { display: block; margin-top: 4px; color: #9c8a72; font-size: 12px; line-height: 1.6; }
.focus-clear { margin-left: auto; padding: 0; border: 0; background: transparent; color: #a7622c; font-size: 12px; cursor: pointer; }
.switch-hint { margin-left: 10px; color: #8a958e; font-size: 12px; }
@media(max-width: 900px) { .setup-grid { grid-template-columns: minmax(0,1fr) 240px; gap: 13px; }.form-section { padding: 17px 15px; } }
@media(max-width: 720px) { .setup-grid { grid-template-columns: 1fr; }.setup-aside { position: static; order: -1; display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.summary-card { grid-row: span 2; }.privacy-card { margin: 0; }.continue-button { align-self: end; }.choice-subtitle { font-size: 13px; } }
/* 窄屏改成单列：说明文字是这两个选项的主要价值，不能在手机上被藏掉。 */
@media(max-width: 460px) { .setup-aside { grid-template-columns: 1fr; }.summary-card { grid-row: auto; }.privacy-card { min-height: 70px; }.continue-button { min-height: 39px; }.field-grid { grid-template-columns: 1fr; gap: 0; }.choice-grid { grid-template-columns: 1fr; gap: 6px; }.choice-grid :deep(.el-radio-button__inner) { min-height: 0; padding: 9px 10px; } }
.upload-label { position: relative; }
.upload-label:focus-within { border-radius: 4px; outline: 2px solid #9dc7ac; outline-offset: 2px; }
/* 原来的 display:none 让文件输入无法聚焦，键盘用户根本打不开选择器。
   改成覆盖整个标签的透明输入：鼠标、键盘（Tab 后回车）与自动化都能触发。 */
.upload-label input { display: block; position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
</style>
