<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { createResume, draftResume, parseResumeFile, refineResume } from '../services/resume';
import {
  BASICS_FIELDS, ENTRY_STEPS, EXAMPLES, WIZARD_STEPS, clearLocalDraft, countFilled, defaultTitle,
  emptyEntry, emptyState, loadLocalDraft, saveLocalDraft, toMaterials,
} from '../services/resumeWizard';

const router = useRouter();
const tab = ref('guided');

// ---- 从零创建 ----
const state = reactive(emptyState());
const stepIndex = ref(0);
const generating = ref(false);
const generated = ref(null);
const draftText = ref('');
const supplement = ref('');
const refining = ref(false);
const saving = ref(false);
const title = ref('');

const step = computed(() => WIZARD_STEPS[stepIndex.value]);
const entryConfig = computed(() => ENTRY_STEPS[step.value.key] || null);
const entries = computed(() => (entryConfig.value ? state[step.value.key] : null));
const filled = computed(() => countFilled(state));
const placeholderPattern = /【[^】]{1,60}】/g;

/** 占位符单独切片，用于高亮（不用 v-html，避免注入）。 */
function segmentsOf(text) {
  const parts = [];
  let last = 0;
  for (const match of String(text || '').matchAll(placeholderPattern)) {
    if (match.index > last) parts.push({ text: text.slice(last, match.index), placeholder: false });
    parts.push({ text: match[0], placeholder: true });
    last = match.index + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), placeholder: false });
  return parts;
}

onMounted(() => {
  const restored = loadLocalDraft();
  if (restored) {
    Object.assign(state, restored);
    ElMessage.info('已恢复上次没写完的内容，可以直接继续。');
  }
});

let saveTimer = null;
watch(state, () => {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => saveLocalDraft(state), 300);
}, { deep: true });

function addEntry() {
  if (!entries.value) return;
  if (entries.value.length >= 12) {
    ElMessage.warning('一段经历最多 12 条，再多就不是简历了。');
    return;
  }
  entries.value.push(emptyEntry());
}

function removeEntry(index) {
  entries.value.splice(index, 1);
}

async function generate() {
  generating.value = true;
  try {
    const result = await draftResume(toMaterials(state));
    generated.value = result;
    draftText.value = result.rawText;
    if (!title.value) title.value = defaultTitle(state);
  } catch (error) {
    ElMessage.error(error.message || '生成失败，请稍后重试');
  } finally {
    generating.value = false;
  }
}

async function refine() {
  if (!supplement.value.trim()) {
    ElMessage.warning('先写点要补充的内容，比如"接口耗时从 800ms 降到 120ms"。');
    return;
  }
  refining.value = true;
  try {
    const result = await refineResume(draftText.value, supplement.value.trim(), generated.value?.questions || []);
    generated.value = result;
    draftText.value = result.rawText;
    supplement.value = '';
    ElMessage.success(result.placeholders?.length
      ? `已更新，还有 ${result.placeholders.length} 处待补充。`
      : '已更新，没有待补充的地方了。');
  } catch (error) {
    ElMessage.error(error.message || '补全失败，请稍后重试');
  } finally {
    refining.value = false;
  }
}

/** optimize=true 时保存完直接跳到详情页的优化面板（Q10：不自动串，但要顺手）。 */
async function save(optimize = false) {
  if (!title.value.trim() || !draftText.value.trim()) {
    ElMessage.warning('标题和正文都要有内容。');
    return;
  }
  saving.value = true;
  try {
    const created = await createResume({
      title: title.value.trim(), source: 'generated', fileName: '', rawText: draftText.value,
    });
    clearLocalDraft();
    generated.value = null;
    const query = optimize ? { optimize: '1', role: state.targetRole || undefined } : {};
    router.push({ path: `/resumes/${created.id}`, query });
  } catch (error) {
    ElMessage.error(error.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

function discard() {
  generated.value = null;
  draftText.value = '';
}

// ---- 上传解析 / 手动填写 ----
const manual = reactive({ title: '', rawText: '', fileName: '', source: 'manual' });
const parsing = ref(false);

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
    manual.rawText = parsed.text;
    manual.fileName = parsed.fileName;
    manual.source = 'upload';
    if (!manual.title) manual.title = (parsed.fileName || '简历').replace(/\.[^.]+$/, '');
    ElMessage.success(`已解析 ${parsed.characters} 字${parsed.truncated ? '（超出部分已截断）' : ''}，请核对后保存。`);
  } catch (error) {
    ElMessage.error(error.message || '解析失败');
  } finally {
    parsing.value = false;
    event.target.value = '';
  }
}

async function saveManual() {
  if (!manual.title.trim() || !manual.rawText.trim()) {
    ElMessage.warning('标题和正文都要填。');
    return;
  }
  saving.value = true;
  try {
    const created = await createResume({
      title: manual.title.trim(), source: manual.source, fileName: manual.fileName, rawText: manual.rawText,
    });
    router.push(`/resumes/${created.id}`);
  } catch (error) {
    ElMessage.error(error.message || '保存失败');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="resume-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">RESUME</div>
        <h1>添加简历</h1>
        <p>没有简历就选「从零创建」，它会把该问的都问一遍；已有文件就传上来解析。</p>
      </div>
      <el-button class="soft-button" @click="router.push('/resumes')">返回列表</el-button>
    </div>

    <el-tabs v-model="tab">
      <el-tab-pane label="从零创建" name="guided">
        <template v-if="!generated">
          <div class="step-bar">
            <button v-for="(item, index) in WIZARD_STEPS" :key="item.key" type="button"
                    :class="{ active: index === stepIndex, done: index < stepIndex }"
                    @click="stepIndex = index">
              <i>{{ index + 1 }}</i>{{ item.label }}
            </button>
          </div>

          <section class="panel step-panel">
            <div class="section-title">
              {{ step.label }}
              <small>不想写就跳过，AI 会留成待补充</small>
            </div>

            <el-input v-if="step.kind === 'text'" v-model="state[step.key]" maxlength="200"
                      :placeholder="EXAMPLES[step.key]" />
            <p v-if="step.kind === 'text'" class="hint">
              写上目标岗位，AI 会按它组织内容和用词；还没想好也行，后面随时能改。
            </p>

            <el-form v-else-if="step.kind === 'basics'" label-position="top">
              <el-form-item v-for="field in BASICS_FIELDS" :key="field.key" :label="field.label">
                <el-input v-model="state.basics[field.key]" maxlength="200" :placeholder="field.example" />
              </el-form-item>
            </el-form>

            <template v-else-if="step.kind === 'entries'">
              <p v-if="!entries.length" class="hint">
                还没有内容。点下面的按钮加一段，或者直接跳过 —— 生成时会留成待补充。
              </p>
              <div v-for="(entry, index) in entries" :key="index" class="entry-card">
                <div class="entry-head">
                  <strong>{{ entryConfig.entryLabel }} {{ index + 1 }}</strong>
                  <el-button class="soft-button" size="small" @click="removeEntry(index)">删除</el-button>
                </div>
                <el-form label-position="top">
                  <el-form-item v-for="field in entryConfig.fields" :key="field.key" :label="field.label">
                    <el-input v-if="field.rows" v-model="entry[field.key]" type="textarea" :rows="field.rows"
                              maxlength="600" :placeholder="field.example" />
                    <el-input v-else v-model="entry[field.key]" maxlength="200" :placeholder="field.example" />
                  </el-form-item>
                </el-form>
              </div>
              <el-button class="soft-button" @click="addEntry">{{ entryConfig.addLabel }}</el-button>
            </template>

            <template v-else>
              <el-input v-model="state[step.key]" type="textarea" :rows="6" maxlength="600"
                        :placeholder="EXAMPLES[step.key]" />
            </template>

            <div class="step-actions">
              <el-button class="soft-button" :disabled="stepIndex === 0" @click="stepIndex -= 1">上一步</el-button>
              <span class="step-hint">已填 {{ filled }} 项；没填的会留成待补充，不会编造</span>
              <el-button v-if="stepIndex < WIZARD_STEPS.length - 1" class="soft-button"
                         @click="stepIndex += 1">跳过 / 下一步</el-button>
              <el-button v-else class="primary-button" :loading="generating" @click="generate">
                {{ generating ? '正在生成…' : '生成简历' }}
              </el-button>
            </div>
            <p v-if="!filled" class="hint">
              什么都没填也能生成：出来是一份带占位符的骨架，你照着补就行。
            </p>
          </section>
        </template>

        <template v-else>
          <section class="panel draft-panel">
            <div class="section-title">
              生成结果
              <span v-if="generated.placeholders?.length" class="pending">
                还有 {{ generated.placeholders.length }} 处待补充
              </span>
            </div>
            <p class="hint">下面是 AI 写好的正文，可以直接改。带琥珀色的地方是它没敢编、留给你补的。</p>
            <div class="draft-preview">
              <template v-for="(seg, index) in segmentsOf(draftText)" :key="index">
                <mark v-if="seg.placeholder">{{ seg.text }}</mark>
                <template v-else>{{ seg.text }}</template>
              </template>
            </div>
            <el-input v-model="draftText" type="textarea" :rows="12" maxlength="20000" show-word-limit />
          </section>

          <section v-if="generated.questions?.length" class="panel">
            <div class="section-title">还缺这些，补上会明显更好</div>
            <ul class="question-list">
              <li v-for="(question, index) in generated.questions" :key="index">{{ question }}</li>
            </ul>
            <el-input v-model="supplement" type="textarea" :rows="3" maxlength="4000"
                      placeholder="在这里补上（例如：接口耗时从 800ms 降到 120ms；会 Docker）" />
            <div class="step-actions">
              <el-button class="soft-button" :loading="refining" @click="refine">继续完善</el-button>
              <span class="step-hint">改完会整篇重写一遍，占位符会自动消掉</span>
            </div>
          </section>

          <section class="panel">
            <div class="section-title">保存到我的简历</div>
            <el-form label-position="top">
              <el-form-item label="简历标题">
                <el-input v-model="title" maxlength="200" placeholder="例如：张三的简历" />
              </el-form-item>
            </el-form>
            <div class="step-actions">
              <el-button class="soft-button" @click="discard">返回修改素材</el-button>
              <el-button class="soft-button" :loading="saving" @click="save(false)">只保存</el-button>
              <el-button class="primary-button" :loading="saving" @click="save(true)">
                保存并去按岗位优化
              </el-button>
            </div>
          </section>
        </template>
      </el-tab-pane>

      <el-tab-pane label="上传解析" name="upload">
        <section class="panel">
          <div class="section-title">上传文件（PDF / Word / txt，≤ 8MB）</div>
          <label class="upload-label">
            <input type="file" accept=".pdf,.doc,.docx,.txt" @change="onFile" />
            <el-icon><Upload /></el-icon>{{ parsing ? '解析中…' : '选择文件' }}
          </label>
          <small class="hint">解析结果会填到下面，先核对再保存；扫描件读不出文字时请改用手动填写。</small>
        </section>
      </el-tab-pane>

      <el-tab-pane label="手动填写" name="manual">
        <section class="panel">
          <div class="section-title">直接粘贴</div>
          <p class="hint">把简历内容粘到下面的正文里就行；没有现成简历的话，用「从零创建」更快。</p>
        </section>
      </el-tab-pane>
    </el-tabs>

    <section v-if="tab !== 'guided'" class="panel">
      <div class="section-title">简历内容</div>
      <el-form label-position="top">
        <el-form-item label="简历标题">
          <el-input v-model="manual.title" maxlength="200" placeholder="例如：Java 后端-张三" />
        </el-form-item>
        <el-form-item label="简历正文">
          <el-input v-model="manual.rawText" type="textarea" :rows="14" maxlength="20000" show-word-limit
                    placeholder="把简历内容粘在这里，或上传文件自动填入" />
        </el-form-item>
      </el-form>
      <div class="step-actions">
        <span class="step-hint">保存后可以继续按岗位优化、导出 Word / PDF</span>
        <el-button class="primary-button" :loading="saving" @click="saveManual">保存简历</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.resume-page { display: grid; gap: 14px; }
.step-bar { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.step-bar button { display: inline-flex; align-items: center; gap: 7px; padding: 7px 12px; border: 1px solid var(--line); border-radius: 999px; background: #fff; color: #6b7671; font-size: 13px; cursor: pointer; }
.step-bar button i { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: #f0f3f1; color: #8a958e; font-size: 11px; font-style: normal; }
.step-bar button.done { color: #4d5a54; }
.step-bar button.active { border-color: var(--green); color: var(--green-dark); font-weight: 600; }
.step-bar button.active i { background: var(--green); color: #fff; }
.step-panel, .draft-panel { padding: 18px; }
.entry-card { margin-bottom: 12px; padding: 14px; border: 1px solid var(--line); border-radius: 8px; background: #fbfcfb; }
.entry-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; color: #33413a; font-size: 14px; }
.step-actions { display: flex; align-items: center; gap: 12px; margin-top: 16px; }
.step-hint { flex: 1; color: #8a958e; font-size: 13px; }
.hint { margin: 8px 0 0; color: #8a958e; font-size: 13px; line-height: 1.7; }
.pending { color: #b45309; font-size: 13px; font-weight: 500; }
.draft-preview { max-height: 170px; margin: 10px 0 12px; padding: 12px 14px; overflow: auto; border: 1px solid var(--line); border-radius: 8px; background: #fbfcfb; color: #3c4650; font-size: 13px; line-height: 1.75; white-space: pre-wrap; }
.draft-preview mark { background: #fdf0e2; color: #b45309; font-weight: 600; }
.question-list { margin: 0 0 12px; padding-left: 20px; color: #44514b; font-size: 14px; line-height: 1.9; }
.upload-label { display: inline-flex; align-items: center; gap: 6px; padding: 7px 12px; border: 1px dashed #cfe0d6; border-radius: 6px; color: var(--green-dark); font-size: 13px; cursor: pointer; }
.upload-label input { display: none; }
</style>
