<script setup>
import { reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { trySyncSession } from '../services/api';
import { buildQuestions } from '../data/questions';

const router = useRouter();
const practice = usePracticeStore();
const formRef = ref();
const resumeName = ref('');
const form = reactive({ role: '', company: '', jobDescription: '', interviewType: 'behavioral', difficulty: 'standard', interviewerStyle: 'balanced', questionCount: 5, durationMinutes: 20 });
watch(() => form.questionCount, (count) => { form.durationMinutes = count * 4; });
const rules = { role: [{ required: true, message: '请填写目标岗位', trigger: 'blur' }, { min: 2, max: 60, message: '岗位名称控制在 2 到 60 个字', trigger: 'blur' }] };

function onResumeChange(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  if (file.size > 8 * 1024 * 1024) {
    ElMessage.error('简历不能超过 8 MB');
    event.target.value = '';
    return;
  }
  resumeName.value = file.name;
}

async function continueToDevices() {
  try {
    await formRef.value.validate();
    const draft = practice.createDraft({ ...form, resumeName: resumeName.value, questions: buildQuestions(form.role, form.questionCount, form.interviewType) });
    const remote = await trySyncSession(draft);
    draft.id = remote.id;
    draft.createdAt = remote.createdAt;
    draft.retentionUntil = remote.retentionUntil;
    practice.saveDraft();
    router.push(`/practice/device-check?id=${draft.id}`);
  } catch (error) {
    ElMessage.error(error.message || '无法创建练习，请检查后端连接。');
  }
}

</script>

<template>
  <section class="setup-page">
    <div class="page-heading"><div><div class="eyebrow">STEP 01 / 02</div><h1>配置你的练习</h1><p>补充目标岗位和面试偏好，系统将据此组织练习问题。</p></div><el-tag class="tag-muted">练习数据保存 7 天</el-tag></div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="setup-grid">
      <div class="setup-main">
        <section class="form-section panel">
          <div class="form-section-title"><span class="number-badge">01</span><div><strong>岗位信息</strong><small>让问题更贴近你的目标职位</small></div></div>
          <div class="field-grid">
            <el-form-item label="目标岗位" prop="role"><el-input v-model="form.role" placeholder="例如：Java 后端工程师" maxlength="60" show-word-limit /></el-form-item>
            <el-form-item label="目标公司（选填）"><el-input v-model="form.company" placeholder="例如：某互联网公司" maxlength="60" /></el-form-item>
          </div>
          <el-form-item label="岗位描述 JD（选填）"><el-input v-model="form.jobDescription" type="textarea" :rows="4" maxlength="3000" show-word-limit placeholder="粘贴岗位职责、技能要求或你特别想准备的内容" /></el-form-item>
          <div class="resume-upload-row">
            <div class="resume-copy"><div class="resume-icon"><el-icon><Document /></el-icon></div><div><strong>{{ resumeName || '添加简历' }}</strong><small>PDF、DOCX，最多 8 MB；仅本地记录文件名</small></div></div>
            <label class="upload-label"><input type="file" accept=".pdf,.doc,.docx" @change="onResumeChange" /><el-icon><Upload /></el-icon>选择文件</label>
          </div>
        </section>

        <section class="form-section panel">
          <div class="form-section-title"><span class="number-badge">02</span><div><strong>练习偏好</strong><small>选择本轮面试的节奏与风格</small></div></div>
          <el-form-item label="面试类型"><el-radio-group v-model="form.interviewType" class="choice-grid">
            <el-radio-button value="behavioral"><span class="choice-title">综合行为</span><span class="choice-subtitle">经历 · 协作 · 复盘</span></el-radio-button>
            <el-radio-button value="technical"><span class="choice-title">技术能力</span><span class="choice-subtitle">原理 · 设计 · 实战</span></el-radio-button>
            <el-radio-button value="manager"><span class="choice-title">管理岗位</span><span class="choice-subtitle">决策 · 团队 · 业务</span></el-radio-button>
          </el-radio-group></el-form-item>
          <el-form-item label="面试官风格"><el-radio-group v-model="form.interviewerStyle" class="style-segment">
            <el-radio-button value="balanced">专业平和</el-radio-button><el-radio-button value="direct">直接追问</el-radio-button><el-radio-button value="supportive">循序引导</el-radio-button>
          </el-radio-group></el-form-item>
          <div class="field-grid lower-fields">
            <el-form-item label="练习难度"><el-select v-model="form.difficulty"><el-option label="基础 · 建立节奏" value="foundation" /><el-option label="标准 · 接近常规面试" value="standard" /><el-option label="进阶 · 深挖细节" value="advanced" /></el-select></el-form-item>
            <el-form-item label="问题数量"><el-select v-model="form.questionCount"><el-option :value="3" label="3 道 · 约 10 分钟" /><el-option :value="5" label="5 道 · 约 20 分钟" /><el-option :value="8" label="8 道 · 约 30 分钟" /></el-select></el-form-item>
          </div>
        </section>
      </div>
      <aside class="setup-aside">
        <div class="summary-card panel"><div class="section-title">本次练习</div><div class="summary-role">{{ form.role || '待填写岗位' }}</div><div class="summary-company">{{ form.company || '目标公司未填写' }}</div><div class="summary-divider"></div><div class="summary-row"><span>面试类型</span><strong>{{ { behavioral: '综合行为', technical: '技术能力', manager: '管理岗位' }[form.interviewType] }}</strong></div><div class="summary-row"><span>预计题目</span><strong>{{ form.questionCount }} 道</strong></div><div class="summary-row"><span>预计时长</span><strong>约 {{ form.durationMinutes }} 分钟</strong></div><div class="summary-row"><span>录像保存</span><strong>服务端 · 7 天</strong></div></div>
        <div class="privacy-card"><el-icon><Lock /></el-icon><div><strong>你的练习由你掌控</strong><p>设备录制需要你主动授权。每题独立保存，可重录、回看或立即删除。</p></div></div>
        <el-button class="primary-button continue-button" @click="continueToDevices">继续检测设备 <el-icon><ArrowRight /></el-icon></el-button>
      </aside>
    </el-form>
  </section>
</template>

<style scoped>
.setup-grid { display: grid; grid-template-columns: minmax(0, 1fr) 274px; align-items: start; gap: 18px; }
.setup-main { display: grid; gap: 15px; }.form-section { padding: 20px 21px 17px; box-shadow: none; }.form-section-title { display: flex; align-items: center; gap: 11px; margin-bottom: 20px; }.form-section-title > div { display: grid; gap: 4px; }.form-section-title strong { color: #34413a; font-size: 13px; }.form-section-title small { color: #87928c; font-size: 10px; }.number-badge { display: grid; width: 27px; height: 27px; place-items: center; border-radius: 50%; background: var(--green-soft); color: var(--green); font-size: 10px; font-weight: 700; }
.field-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; }.setup-page :deep(.el-form-item) { margin-bottom: 16px; }.setup-page :deep(.el-form-item__label) { height: auto; margin-bottom: 7px; color: #5e6a64; font-size: 11px; line-height: 1.3; }.setup-page :deep(.el-input__wrapper) { min-height: 38px; }.setup-page :deep(.el-textarea__inner) { min-height: 100px !important; padding: 11px; }.setup-page :deep(.el-input__count) { background: transparent; font-size: 9px; }
.resume-upload-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 2px; padding: 12px; border: 1px dashed #dce4de; border-radius: 6px; background: #fafcfb; }.resume-copy { display: flex; min-width: 0; align-items: center; gap: 10px; }.resume-icon { display: grid; width: 32px; height: 32px; flex: none; place-items: center; border-radius: 6px; background: var(--blue-soft); color: var(--blue); }.resume-copy strong { display: block; overflow: hidden; color: #47534d; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.resume-copy small { display: block; margin-top: 4px; color: #98a19c; font-size: 9px; }.upload-label { display: inline-flex; flex: none; align-items: center; gap: 5px; color: var(--green); font-size: 10px; font-weight: 600; cursor: pointer; }.upload-label input { display: none; }
.choice-grid { display: grid; width: 100%; grid-template-columns: repeat(3,1fr); gap: 9px; }.choice-grid :deep(.el-radio-button) { width: 100%; }.choice-grid :deep(.el-radio-button__inner) { display: grid; width: 100%; height: 61px; align-content: center; gap: 6px; border: 1px solid #e2e7e3 !important; border-radius: 6px !important; box-shadow: none !important; text-align: left; }.choice-grid :deep(.el-radio-button:first-child .el-radio-button__inner),.choice-grid :deep(.el-radio-button:last-child .el-radio-button__inner) { border-left: 1px solid #e2e7e3; border-radius: 6px; }.choice-grid :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--green) !important; background: #f0f8f4; color: var(--green-dark); }.choice-title { font-size: 10px; font-weight: 600; }.choice-subtitle { color: #8a9690; font-size: 9px; }.style-segment { display: flex; flex-wrap: wrap; }.style-segment :deep(.el-radio-button__inner) { height: 32px; padding: 0 13px; border-color: #e2e7e3; box-shadow: none !important; font-size: 10px; }.style-segment :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--green); background: var(--green-soft); color: var(--green-dark); }
.lower-fields { margin-top: 5px; }.lower-fields :deep(.el-select) { width: 100%; }
.setup-aside { position: sticky; top: 82px; }.summary-card { padding: 17px; box-shadow: none; }.summary-card .section-title { margin-bottom: 17px; }.summary-role { color: #35413b; font-size: 15px; font-weight: 650; }.summary-company { margin-top: 5px; color: #98a29d; font-size: 10px; }.summary-divider { margin: 16px 0 13px; border-top: 1px solid #edf0ed; }.summary-row { display: flex; justify-content: space-between; gap: 8px; margin: 12px 0; color: #818c86; font-size: 10px; }.summary-row strong { color: #4c5952; font-size: 10px; font-weight: 600; }.privacy-card { display: flex; gap: 10px; margin: 12px 0; padding: 14px; border: 1px solid #dcece2; border-radius: 6px; background: #eff7f1; color: var(--green); }.privacy-card > .el-icon { flex: none; margin-top: 2px; }.privacy-card strong { color: #3d7458; font-size: 10px; }.privacy-card p { margin: 5px 0 0; color: #718d7b; font-size: 9px; line-height: 1.6; }.continue-button { width: 100%; }
@media(max-width: 900px) { .setup-grid { grid-template-columns: minmax(0,1fr) 240px; gap: 13px; }.form-section { padding: 17px 15px; } }
@media(max-width: 720px) { .setup-grid { grid-template-columns: 1fr; }.setup-aside { position: static; order: -1; display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }.summary-card { grid-row: span 2; }.privacy-card { margin: 0; }.continue-button { align-self: end; }.choice-subtitle { font-size: 8px; } }
@media(max-width: 460px) { .setup-aside { grid-template-columns: 1fr; }.summary-card { grid-row: auto; }.privacy-card { min-height: 70px; }.continue-button { min-height: 39px; }.field-grid { grid-template-columns: 1fr; gap: 0; }.choice-grid { gap: 5px; }.choice-grid :deep(.el-radio-button__inner) { height: 59px; padding: 0 7px; }.choice-subtitle { display: none; }.style-segment :deep(.el-radio-button__inner) { padding: 0 9px; } }
</style>
