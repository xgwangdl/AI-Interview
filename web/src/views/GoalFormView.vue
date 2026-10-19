<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createGoal, getGoal, STAGE_LABEL_KEYS, toIsoDateTime, toLocalDateTime, updateGoal } from '../services/goals';
import { listResumes } from '../services/resume';
import { useI18n } from 'vue-i18n';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const formRef = ref();
const resumes = ref([]);
const loading = ref(false);
const saving = ref(false);
const editing = ref(Boolean(route.params.id));

const form = reactive({
  company: '',
  role: '',
  stage: 'technical',
  interviewAt: '',
  jobDescription: '',
  resumeId: '',
  notes: '',
  status: 'preparing',
});

const rules = computed(() => ({
  role: [{ required: true, message: t('goals.roleRequired'), trigger: 'blur' }],
}));

onMounted(async () => {
  loading.value = true;
  try {
    resumes.value = await listResumes();
    if (editing.value) {
      const goal = await getGoal(route.params.id);
      Object.assign(form, {
        company: goal.company || '',
        role: goal.role || '',
        stage: goal.stage || 'technical',
        interviewAt: toLocalDateTime(goal.interviewAt),
        jobDescription: goal.jobDescription || '',
        resumeId: goal.resumeId || '',
        notes: goal.notes || '',
        status: goal.status === 'archived' ? 'preparing' : goal.status,
      });
    }
  } catch (error) {
    ElMessage.error(error?.message || t('goals.readFailed'));
  } finally {
    loading.value = false;
  }
});

async function save() {
  try {
    await formRef.value.validate();
  } catch {
    return;
  }
  saving.value = true;
  try {
    const payload = {
      company: form.company.trim() || null,
      role: form.role.trim(),
      stage: form.stage,
      interviewAt: toIsoDateTime(form.interviewAt),
      jobDescription: form.jobDescription.trim() || null,
      resumeId: form.resumeId || null,
      notes: form.notes.trim() || null,
      status: form.status,
    };
    const saved = editing.value
      ? await updateGoal(route.params.id, payload)
      : await createGoal(payload);
    ElMessage.success(editing.value ? t('goals.updated') : t('goals.created'));
    router.push(`/goals/${saved.id}`);
  } catch (error) {
    ElMessage.error(error?.message || t('goals.saveFailed'));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="goal-form-page">
    <div class="page-heading">
      <div>
        <button class="back-link" @click="router.push('/goals')"><el-icon><ArrowLeft /></el-icon>{{ t('goals.back') }}</button>
        <h1>{{ editing ? t('goals.editTitle') : t('goals.addTitle') }}</h1>
        <p>{{ t('goals.subtitleForm') }}</p>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="panel goal-form">
      <div class="form-grid">
        <el-form-item :label="t('goals.company')">
          <el-input v-model="form.company" maxlength="200" :placeholder="t('goals.companyPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('goals.role')" prop="role">
          <el-input v-model="form.role" maxlength="160" :placeholder="t('goals.rolePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('goals.stage')">
          <el-select v-model="form.stage">
            <el-option v-for="(path, key) in STAGE_LABEL_KEYS" :key="key" :label="t(path)" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('goals.interviewTime')">
          <el-date-picker v-model="form.interviewAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                          :placeholder="t('goals.interviewTimePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('goals.resume')">
          <el-select v-model="form.resumeId" clearable :placeholder="t('goals.optional')">
            <el-option v-for="resume in resumes" :key="resume.id" :label="resume.title" :value="resume.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('goals.status')">
          <el-select v-model="form.status">
            <el-option :label="t('domain.goalStatus.preparing')" value="preparing" />
            <el-option :label="t('domain.goalStatus.applied')" value="applied" />
            <el-option :label="t('domain.goalStatus.interviewing')" value="interviewing" />
            <el-option :label="t('domain.goalStatus.done')" value="done" />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item :label="t('goals.jobDescription')">
        <el-input v-model="form.jobDescription" type="textarea" :rows="5" maxlength="10000" show-word-limit
                  :placeholder="t('goals.jobDescriptionPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('goals.notes')">
        <el-input v-model="form.notes" type="textarea" :rows="3" maxlength="2000"
                  :placeholder="t('goals.notesPlaceholder')" />
      </el-form-item>

      <div class="form-actions">
        <el-button class="soft-button" @click="router.push('/goals')">{{ t('goals.cancel') }}</el-button>
        <el-button class="primary-button" :loading="saving" @click="save">
          {{ editing ? t('goals.saveChanges') : t('goals.create') }}
        </el-button>
      </div>
    </el-form>
  </section>
</template>

<style scoped>
.goal-form-page { max-width: 880px; }
.back-link { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 7px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.back-link:hover { color: var(--green); }
.goal-form { display: grid; gap: 14px; padding: 22px; box-shadow: none; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 16px; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 4px; }
@media (max-width: 680px) { .form-grid { grid-template-columns: 1fr; } }
</style>
