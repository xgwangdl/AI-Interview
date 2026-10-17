<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createGoal, getGoal, STAGE_LABELS, toIsoDateTime, toLocalDateTime, updateGoal } from '../services/goals';
import { listResumes } from '../services/resume';

const route = useRoute();
const router = useRouter();
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

const rules = {
  role: [{ required: true, message: '请填写目标岗位', trigger: 'blur' }],
};

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
    ElMessage.error(error?.message || '无法读取目标');
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
    ElMessage.success(editing.value ? '目标已更新' : '目标已创建');
    router.push(`/goals/${saved.id}`);
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="goal-form-page">
    <div class="page-heading">
      <div>
        <button class="back-link" @click="router.push('/goals')"><el-icon><ArrowLeft /></el-icon>求职目标</button>
        <h1>{{ editing ? '编辑目标' : '添加求职目标' }}</h1>
        <p>把岗位信息填清楚，之后每天的练习都会围绕它。</p>
      </div>
    </div>

    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="panel goal-form">
      <div class="form-grid">
        <el-form-item label="公司">
          <el-input v-model="form.company" maxlength="200" placeholder="例如：字节跳动" />
        </el-form-item>
        <el-form-item label="目标岗位" prop="role">
          <el-input v-model="form.role" maxlength="160" placeholder="例如：Java 后端工程师" />
        </el-form-item>
        <el-form-item label="面试轮次">
          <el-select v-model="form.stage">
            <el-option v-for="(label, key) in STAGE_LABELS" :key="key" :label="label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="面试时间">
          <el-date-picker v-model="form.interviewAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                          placeholder="选填，用于倒计时" />
        </el-form-item>
        <el-form-item label="关联简历">
          <el-select v-model="form.resumeId" clearable placeholder="选填">
            <el-option v-for="resume in resumes" :key="resume.id" :label="resume.title" :value="resume.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标状态">
          <el-select v-model="form.status">
            <el-option label="准备中" value="preparing" />
            <el-option label="已投递" value="applied" />
            <el-option label="面试中" value="interviewing" />
            <el-option label="已结束" value="done" />
          </el-select>
        </el-form-item>
      </div>

      <el-form-item label="岗位描述 JD">
        <el-input v-model="form.jobDescription" type="textarea" :rows="5" maxlength="10000" show-word-limit
                  placeholder="粘贴岗位职责与技能要求；出题会参考它" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.notes" type="textarea" :rows="3" maxlength="2000"
                  placeholder="例如：一面重点问项目，二面可能考系统设计" />
      </el-form-item>

      <div class="form-actions">
        <el-button class="soft-button" @click="router.push('/goals')">取消</el-button>
        <el-button class="primary-button" :loading="saving" @click="save">
          {{ editing ? '保存修改' : '创建目标' }}
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
