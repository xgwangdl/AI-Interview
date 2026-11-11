<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { createDebrief, listJobLeads } from '../services/jobRadar';

const router = useRouter();
const { t } = useI18n();
const jobs = ref([]);
const loadingJobs = ref(true);
const submitting = ref(false);
const privacyAcknowledged = ref(false);
const today = new Date().toISOString().slice(0, 10);
const form = reactive({
  jobLeadId: '', interviewDate: today, roundName: '', outcome: 'unknown',
  rawText: '', interviewerFocus: '', selfNotes: '',
});

onMounted(async () => {
  try {
    const result = await listJobLeads({ limit: 50 });
    jobs.value = result.items || [];
  } catch {
    jobs.value = [];
  } finally {
    loadingJobs.value = false;
  }
});

async function submit() {
  if (!form.interviewDate || !form.roundName.trim()) return ElMessage.warning(t('jobRadar.debriefForm.required'));
  if (form.rawText.trim().length < 10) return ElMessage.warning(t('jobRadar.debriefForm.rawRequired'));
  if (!privacyAcknowledged.value) return ElMessage.warning(t('jobRadar.debriefForm.privacyRequired'));
  submitting.value = true;
  try {
    const result = await createDebrief({
      jobLeadId: form.jobLeadId || null,
      practiceSessionId: null,
      interviewDate: form.interviewDate,
      roundName: form.roundName.trim(),
      outcome: form.outcome,
      rawText: form.rawText.trim(),
      interviewerFocus: form.interviewerFocus.trim() || null,
      selfNotes: form.selfNotes.trim() || null,
      privacyAcknowledged: true,
    });
    ElMessage.success(t('jobRadar.debriefForm.submitted'));
    router.replace(`/debrief/${result.debriefId}`);
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.debriefForm.submitFailed'));
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <section class="debrief-new-page">
    <div class="page-heading">
      <div>
        <button class="text-back" @click="router.push('/debrief')"><el-icon><ArrowLeft /></el-icon>{{ t('jobRadar.backToDebrief') }}</button>
        <h1>{{ t('jobRadar.debriefForm.title') }}</h1>
        <p>{{ t('jobRadar.debriefForm.subtitle') }}</p>
      </div>
    </div>

    <div class="debrief-form-layout">
      <section class="panel form-panel">
        <div class="form-grid">
          <label>{{ t('jobRadar.debriefForm.job') }}
            <el-select v-model="form.jobLeadId" clearable :loading="loadingJobs" :placeholder="t('jobRadar.debriefForm.noJob')">
              <el-option v-for="job in jobs" :key="job.id" :label="`${job.role} · ${job.company || t('jobRadar.companyUnknown')}`" :value="job.id" />
            </el-select>
          </label>
          <label>{{ t('jobRadar.debriefForm.date') }}<el-date-picker v-model="form.interviewDate" type="date" value-format="YYYY-MM-DD" :clearable="false" /></label>
          <label>{{ t('jobRadar.debriefForm.round') }}<el-input v-model="form.roundName" maxlength="30" :placeholder="t('jobRadar.debriefForm.roundPlaceholder')" /></label>
          <label>{{ t('jobRadar.debriefForm.outcome') }}
            <el-select v-model="form.outcome">
              <el-option v-for="value in ['unknown', 'pending', 'pass', 'reject']" :key="value" :value="value" :label="t(`jobRadar.outcome.${value}`)" />
            </el-select>
          </label>
        </div>

        <label class="textarea-field">{{ t('jobRadar.debriefForm.rawText') }}
          <el-input v-model="form.rawText" type="textarea" :rows="11" maxlength="20000" show-word-limit :placeholder="t('jobRadar.debriefForm.rawHint')" />
        </label>
        <label class="textarea-field">{{ t('jobRadar.debriefForm.focus') }}
          <el-input v-model="form.interviewerFocus" maxlength="200" :placeholder="t('jobRadar.debriefForm.focusHint')" />
        </label>
        <label class="textarea-field">{{ t('jobRadar.debriefForm.notes') }}
          <el-input v-model="form.selfNotes" type="textarea" :rows="3" maxlength="5000" :placeholder="t('jobRadar.debriefForm.notesHint')" />
        </label>
      </section>

      <aside class="privacy-card panel">
        <span class="privacy-icon"><el-icon><Lock /></el-icon></span>
        <h2>{{ t('jobRadar.debriefForm.privacyTitle') }}</h2>
        <ul>
          <li>{{ t('jobRadar.debriefForm.privacyPoint1') }}</li>
          <li>{{ t('jobRadar.debriefForm.privacyPoint2') }}</li>
          <li>{{ t('jobRadar.debriefForm.privacyPoint3') }}</li>
        </ul>
        <el-checkbox v-model="privacyAcknowledged">{{ t('jobRadar.debriefForm.privacyCheck') }}</el-checkbox>
        <el-button class="primary-button submit-button" :loading="submitting" :disabled="submitting" @click="submit">
          <el-icon><Upload /></el-icon>{{ t('jobRadar.debriefForm.submit') }}
        </el-button>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.debrief-new-page { max-width: 1100px; }
.text-back { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 8px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.debrief-form-layout { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(260px, .62fr); gap: 20px; align-items: start; }
.form-panel { padding: 21px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; margin-bottom: 18px; }
.form-grid label, .textarea-field { display: grid; gap: 7px; color: #5e6a64; font-size: 13px; }
.textarea-field { margin-top: 15px; }
:deep(.el-date-editor), :deep(.el-select) { width: 100%; }
.privacy-card { position: sticky; top: 86px; padding: 21px; }
.privacy-icon { display: grid; width: 40px; height: 40px; place-items: center; border-radius: 50%; background: var(--green-soft); color: var(--green); font-size: 18px; }
.privacy-card h2 { margin: 14px 0 10px; color: #334038; font-size: 17px; }
.privacy-card ul { margin: 0 0 19px; padding-left: 18px; color: #75817b; font-size: 13px; line-height: 1.75; }
.submit-button { width: 100%; margin-top: 18px; }
@media (max-width: 850px) { .debrief-form-layout { grid-template-columns: 1fr; } .privacy-card { position: static; } }
@media (max-width: 560px) { .form-grid { grid-template-columns: 1fr; } .form-panel { padding: 16px; } }
</style>
