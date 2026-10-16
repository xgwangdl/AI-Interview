<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  applyOptimization, deleteResume, downloadResume, getResume, optimizeResume, updateResume,
  RESUME_EXPORT_FORMATS,
} from '../services/resume';

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const saving = ref(false);
const optimizing = ref(false);
const applying = ref(false);
const exporting = ref(false);
const resume = ref(null);
const optimization = ref(null);
const selected = ref([]);

const form = reactive({ title: '', rawText: '' });
const job = reactive({ role: '', jobDescription: '' });

const placeholderPattern = /【[^】]{1,60}】/g;

/** 把文本切成"普通片段 / 占位符片段"，用于高亮待补充内容（不用 v-html，避免注入）。 */
function segmentsOf(text) {
  if (!text) return [];
  const parts = [];
  let last = 0;
  for (const match of text.matchAll(placeholderPattern)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index), placeholder: false });
    parts.push({ text: match[0], placeholder: true });
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), placeholder: false });
  return parts;
}

const sections = computed(() => optimization.value?.sections || []);
const pendingCount = computed(() => optimization.value?.placeholders?.length || 0);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const data = await getResume(route.params.id);
    resume.value = data;
    form.title = data.title;
    form.rawText = data.rawText;
    // 从「从零创建」保存并勾了"顺手优化"过来的：把目标岗位填好并把优化面板滚到眼前
    if (route.query.optimize) {
      job.role = String(route.query.role || '');
      await nextTick();
      document.querySelector('.optimize-panel')?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  } catch (error) {
    ElMessage.error(error.message || '无法加载简历');
    router.replace('/resumes');
  } finally {
    loading.value = false;
  }
}

async function save() {
  if (!form.title.trim() || !form.rawText.trim()) {
    ElMessage.warning('标题和正文都要填。');
    return;
  }
  saving.value = true;
  try {
    resume.value = await updateResume(resume.value.id, {
      title: form.title.trim(), source: resume.value.source,
      fileName: resume.value.fileName, rawText: form.rawText,
    });
    form.rawText = resume.value.rawText;
    ElMessage.success(`已保存（v${resume.value.version}）`);
  } catch (error) {
    ElMessage.error(error.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function optimize() {
  if (!job.jobDescription.trim()) {
    ElMessage.warning('请先粘贴目标岗位的 JD。');
    return;
  }
  optimizing.value = true;
  optimization.value = null;
  selected.value = [];
  try {
    const result = await optimizeResume(resume.value.id, job.role.trim(), job.jobDescription.trim());
    optimization.value = result;
    if (result.status !== 'completed') {
      ElMessage.error(result.error || '优化失败，可以稍后重试');
      return;
    }
    // 默认勾选真正有改动、且原文能在正文里定位到的段
    selected.value = result.sections
      .map((section, index) => ({ section, index }))
      .filter(({ section }) => section.changeType !== 'keep'
        && section.original && form.rawText.includes(section.original))
      .map(({ index }) => index);
  } catch (error) {
    ElMessage.error(error.message || '优化失败');
  } finally {
    optimizing.value = false;
  }
}

async function apply(selectedOnly) {
  const indices = selectedOnly ? selected.value : [];
  if (selectedOnly && !indices.length) {
    ElMessage.warning('还没有勾选任何段落。');
    return;
  }
  if (!selectedOnly) {
    await ElMessageBox.confirm('会用优化后的内容整体替换正文（采纳其中能定位到的段落）。继续？', '全部采用', { type: 'warning' });
  }
  applying.value = true;
  try {
    const updated = await applyOptimization(resume.value.id, optimization.value.id, indices);
    resume.value = updated;
    form.rawText = updated.rawText;
    ElMessage.success(`已采纳，版本 v${updated.version}`);
  } catch (error) {
    ElMessage.error(error.message || '采纳失败');
  } finally {
    applying.value = false;
  }
}

async function remove() {
  await ElMessageBox.confirm(`删除「${resume.value.title}」？优化记录会一起删掉。`, '删除简历', { type: 'warning' });
  try {
    await deleteResume(resume.value.id);
    router.replace('/resumes');
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
}

async function exportResume(format) {
  exporting.value = true;
  try {
    await downloadResume(resume.value.id, resume.value.title, format);
  } catch (error) {
    ElMessage.error(error.message || '导出失败');
  } finally {
    exporting.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="resume-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">RESUME</div>
        <h1>{{ resume?.title || '简历' }}</h1>
        <p v-if="resume">
          {{ resume.source === 'upload' ? '上传解析' : '手动填写' }}
          · v{{ resume.version }} · 更新于 {{ String(resume.updatedAt).slice(0, 16).replace('T', ' ') }}
        </p>
      </div>
      <div class="heading-actions">
        <el-button class="soft-button" @click="router.push('/resumes')">返回列表</el-button>
        <el-dropdown trigger="click" @command="exportResume">
          <el-button class="soft-button" :loading="exporting">
            <el-icon><Download /></el-icon> 导出
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in RESUME_EXPORT_FORMATS" :key="item.value" :command="item.value">
                {{ item.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button class="soft-button danger" @click="remove">删除</el-button>
      </div>
    </div>

    <div v-if="resume" class="resume-layout">
      <section class="panel">
        <div class="section-title">简历正文</div>
        <el-input v-model="form.title" maxlength="200" placeholder="标题" class="title-input" />
        <el-input v-model="form.rawText" type="textarea" :rows="18" maxlength="20000" show-word-limit />
        <div class="row-actions">
          <el-button class="primary-button" :loading="saving" @click="save">保存修改</el-button>
        </div>
      </section>

      <section class="panel optimize-panel">
        <div class="section-title">按岗位优化</div>
        <el-form label-position="top">
          <el-form-item label="目标岗位"><el-input v-model="job.role" maxlength="200" placeholder="例如：Java 后端工程师" /></el-form-item>
          <el-form-item label="岗位描述（JD）">
            <el-input v-model="job.jobDescription" type="textarea" :rows="6" maxlength="10000" show-word-limit
                      placeholder="粘贴 JD，AI 会按它调整简历" />
          </el-form-item>
        </el-form>
        <el-button class="primary-button" :loading="optimizing" @click="optimize">
          {{ optimizing ? '正在优化…' : '开始优化' }}
        </el-button>
        <p class="hint">优化只改措辞与结构，不会编造经历和数字；缺数据的地方会标成待补充。</p>
      </section>
    </div>

    <section v-if="optimization" class="panel result-panel">
      <div class="result-head">
        <div class="section-title">优化结果</div>
        <el-tag :class="optimization.status === 'completed' ? 'tag-ready' : 'tag-warn'" size="small">
          {{ optimization.status === 'completed' ? '已完成' : '失败' }}
        </el-tag>
        <span v-if="pendingCount" class="pending">有 {{ pendingCount }} 处待你补充</span>
      </div>

      <p v-if="optimization.summary" class="summary">{{ optimization.summary }}</p>
      <p v-if="optimization.error" class="error-text">{{ optimization.error }}</p>

      <div v-if="optimization.keywordCoverage" class="coverage">
        <span>JD 关键词：</span>
        <em v-for="word in optimization.keywordCoverage.matched || []" :key="word" class="matched">{{ word }}</em>
        <em v-for="word in optimization.keywordCoverage.missing || []" :key="word" class="missing">{{ word }}（缺）</em>
      </div>

      <div class="section-list">
        <div v-for="(section, index) in sections" :key="index" class="section-row">
          <el-checkbox v-model="selected" :value="index" class="row-check"
                       :disabled="!section.original || !form.rawText.includes(section.original)">
            <span class="change-type">{{ section.changeType }}</span>
          </el-checkbox>
          <div class="pair">
            <div class="col">
              <small>原文</small>
              <p class="muted">{{ section.original || '（新增段落）' }}</p>
            </div>
            <div class="col">
              <small>优化后</small>
              <p>
                <template v-for="(seg, i) in segmentsOf(section.optimized)" :key="i">
                  <mark v-if="seg.placeholder">{{ seg.text }}</mark>
                  <template v-else>{{ seg.text }}</template>
                </template>
              </p>
              <span class="reason">{{ section.reason }}</span>
            </div>
          </div>
        </div>
      </div>

      <div class="row-actions">
        <el-button class="primary-button" :loading="applying" @click="apply(true)">
          采纳选中的 {{ selected.length }} 段
        </el-button>
        <el-button class="soft-button" :loading="applying" @click="apply(false)">全部采用</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.resume-page { display: grid; gap: 14px; }
.heading-actions { display: flex; gap: 8px; }
.resume-layout { display: grid; grid-template-columns: minmax(0, 1.3fr) minmax(0, 1fr); gap: 14px; align-items: start; }
.resume-layout .panel, .result-panel { padding: 16px; box-shadow: none; }
.title-input { margin-bottom: 10px; }
.row-actions { display: flex; gap: 8px; margin-top: 12px; }
.hint { margin: 10px 0 0; color: #8a958e; font-size: 12px; line-height: 1.7; }
.soft-button.danger { color: #a4483a; }
.result-head { display: flex; align-items: center; gap: 10px; }
.pending { color: #b4622a; font-size: 12px; }
.summary { margin: 10px 0 0; color: #47534d; font-size: 13px; line-height: 1.7; }
.error-text { margin: 10px 0 0; color: #a4483a; font-size: 13px; }
.coverage { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 10px; color: #6b7770; font-size: 12px; }
.coverage em { padding: 2px 7px; border-radius: 10px; font-style: normal; }
.coverage .matched { background: var(--green-soft); color: var(--green-dark); }
.coverage .missing { background: #fbeee4; color: #b4622a; }
.section-list { display: grid; gap: 10px; margin-top: 14px; }
.section-row { display: flex; gap: 10px; padding: 11px; border: 1px solid #e8eeea; border-radius: 6px; background: #fafcfb; }
.row-check { margin-right: 2px; }
.change-type { color: #8a958e; font-size: 11px; }
.pair { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; flex: 1; min-width: 0; }
.col small { color: #8a958e; font-size: 11px; }
.col p { margin: 5px 0 0; color: #3f4c45; font-size: 13px; line-height: 1.7; white-space: pre-wrap; }
.col p.muted { color: #7c8782; }
.col mark { padding: 1px 4px; border-radius: 3px; background: #fbeee4; color: #b4622a; }
.reason { display: block; margin-top: 6px; color: #8a958e; font-size: 11px; line-height: 1.6; }
@media (max-width: 900px) { .resume-layout { grid-template-columns: 1fr; } .pair { grid-template-columns: 1fr; } }
</style>
