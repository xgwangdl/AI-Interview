<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { createResume, deleteResume, listResumes, parseResumeFile } from '../services/resume';

const router = useRouter();
const loading = ref(true);
const saving = ref(false);
const parsing = ref(false);
const resumes = ref([]);
const creator = ref(false);

const form = reactive({ title: '', source: 'manual', fileName: '', rawText: '' });

onMounted(load);

async function load() {
  loading.value = true;
  try {
    resumes.value = await listResumes();
  } catch (error) {
    ElMessage.error(error.message || '无法加载简历列表');
  } finally {
    loading.value = false;
  }
}

function openCreator() {
  Object.assign(form, { title: '', source: 'manual', fileName: '', rawText: '' });
  creator.value = true;
}

async function onFile(event) {
  const file = event.target.files?.[0];
  if (!file) return;
  parsing.value = true;
  try {
    const parsed = await parseResumeFile(file);
    if (!parsed?.text) {
      ElMessage.warning('这份文件读不出文字（可能是扫描件或纯图片），请改用手动粘贴。');
      return;
    }
    form.rawText = parsed.text;
    form.fileName = parsed.fileName;
    form.source = 'upload';
    if (!form.title) form.title = (parsed.fileName || '简历').replace(/\.[^.]+$/, '');
    ElMessage.success(`已解析 ${parsed.characters} 字${parsed.truncated ? '（超出部分已截断）' : ''}，请核对后保存。`);
  } catch (error) {
    ElMessage.error(error.message || '解析失败');
  } finally {
    parsing.value = false;
    event.target.value = '';
  }
}

async function save() {
  if (!form.title.trim() || !form.rawText.trim()) {
    ElMessage.warning('标题和正文都要填。');
    return;
  }
  saving.value = true;
  try {
    const created = await createResume({
      title: form.title.trim(), source: form.source, fileName: form.fileName, rawText: form.rawText,
    });
    creator.value = false;
    await load();
    router.push(`/resumes/${created.id}`);
  } catch (error) {
    ElMessage.error(error.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function remove(item) {
  await ElMessageBox.confirm(`删除「${item.title}」？优化记录会一起删掉。`, '删除简历', { type: 'warning' });
  try {
    await deleteResume(item.id);
    await load();
  } catch (error) {
    ElMessage.error(error.message || '删除失败');
  }
}
</script>

<template>
  <section v-loading="loading" class="resume-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">RESUME</div>
        <h1>我的简历</h1>
        <p>存一份简历，按目标岗位让 AI 优化，再把结果带进模拟面试。</p>
      </div>
      <el-button class="primary-button" @click="openCreator"><el-icon><Plus /></el-icon> 添加简历</el-button>
    </div>

    <el-empty v-if="!resumes.length" description="还没有简历，点右上角添加一份" />

    <div class="resume-grid">
      <article v-for="item in resumes" :key="item.id" class="panel resume-card" @click="router.push(`/resumes/${item.id}`)">
        <div class="card-head">
          <strong>{{ item.title }}</strong>
          <el-tag :class="item.optimized ? 'tag-ready' : 'tag-muted'" size="small">
            {{ item.optimized ? '已优化' : '未优化' }}
          </el-tag>
        </div>
        <div class="card-meta">
          <span>{{ item.source === 'upload' ? '上传解析' : '手动填写' }}</span>
          <i></i>
          <span>{{ item.characters }} 字</span>
          <i></i>
          <span>v{{ item.version }}</span>
          <i></i>
          <span>{{ String(item.updatedAt).slice(0, 10) }}</span>
        </div>
        <div class="card-actions">
          <el-button class="soft-button" size="small" @click.stop="router.push(`/resumes/${item.id}`)">打开</el-button>
          <el-button class="soft-button danger" size="small" @click.stop="remove(item)">删除</el-button>
        </div>
      </article>
    </div>

    <el-dialog v-model="creator" title="添加简历" width="720px">
      <el-form label-position="top">
        <el-form-item label="简历标题">
          <el-input v-model="form.title" maxlength="200" placeholder="例如：Java 后端-张三" />
        </el-form-item>
        <el-form-item label="上传文件（PDF / DOCX / TXT，≤ 8MB）">
          <label class="upload-label">
            <input type="file" accept=".pdf,.doc,.docx,.txt" @change="onFile" />
            <el-icon><Upload /></el-icon>{{ parsing ? '解析中…' : '选择文件' }}
          </label>
          <small class="upload-hint">解析结果会填到下面，先核对再保存；扫描件读不出文字时请直接粘贴。</small>
        </el-form-item>
        <el-form-item label="简历正文">
          <el-input v-model="form.rawText" type="textarea" :rows="14" maxlength="20000" show-word-limit
                    placeholder="把简历内容粘在这里，或上传文件自动填入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button class="soft-button" @click="creator = false">取消</el-button>
        <el-button class="primary-button" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.resume-page { display: grid; gap: 14px; }
.resume-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.resume-card { padding: 15px; box-shadow: none; cursor: pointer; }
.resume-card:hover { border-color: var(--green); }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.card-head strong { color: #33413a; font-size: 14px; }
.card-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; margin-top: 9px; color: #8a958e; font-size: 12px; }
.card-meta i { width: 3px; height: 3px; border-radius: 50%; background: #b7c0ba; }
.card-actions { display: flex; gap: 8px; margin-top: 12px; }
.soft-button.danger { color: #a4483a; }
.upload-label { display: inline-flex; align-items: center; gap: 6px; padding: 7px 12px; border: 1px dashed #cfe0d6; border-radius: 6px; color: var(--green-dark); font-size: 13px; cursor: pointer; }
.upload-label input { display: none; }
.upload-hint { margin-left: 10px; color: #8a958e; font-size: 12px; }
</style>
