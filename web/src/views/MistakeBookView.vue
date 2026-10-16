<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { PAGE_SIZE, buildMistakes, loadMistakes } from '../services/practiceMistakes';

/**
 * 错题本全量页。
 *
 * 总览页只放最差的前 5 条（一次列几十条会让人不想开始），但要能"看到后面那些题"，
 * 所以这一页做真分页 + 按岗位筛选；重练走的还是同一条链路（store 里的 retryMistake）。
 */
const router = useRouter();
const practice = usePracticeStore();
const book = ref(null);
const loading = ref(true);
const page = ref(1);
const role = ref('');
const retrying = ref('');

const roleOptions = computed(() => book.value?.roles || []);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    book.value = buildMistakes(await loadMistakes({
      role: role.value,
      offset: (page.value - 1) * PAGE_SIZE,
      limit: PAGE_SIZE,
    }));
  } catch (error) {
    ElMessage.error(error?.message || '无法读取错题本，请稍后再试。');
    book.value = null;
  } finally {
    loading.value = false;
  }
}

/** 换岗位要回到第一页：停在第 3 页会看到空列表，很像坏了。 */
function changeRole(value) {
  role.value = value || '';
  page.value = 1;
  void load();
}

function changePage(next) {
  page.value = next;
  void load();
}

async function retry(item) {
  if (retrying.value) return;
  retrying.value = item.questionId;
  try {
    const sessionId = await practice.retryMistake(item.questionId);
    router.push(`/practice/device-check?id=${sessionId}`);
  } catch (error) {
    ElMessage.error(error?.message || '暂时无法开始这次重练，请稍后再试。');
  } finally {
    retrying.value = '';
  }
}
</script>

<template>
  <section class="mistakes-page">
    <div class="page-heading">
      <div>
        <button class="report-back" @click="router.push('/')"><el-icon><ArrowLeft /></el-icon>训练总览</button>
        <h1>错题本</h1>
        <p v-if="book">{{ book.scopeNote }}</p>
      </div>
      <el-select v-if="roleOptions.length > 1" :model-value="role" class="role-filter"
                 placeholder="全部岗位" clearable @change="changeRole">
        <el-option label="全部岗位" value="" />
        <el-option v-for="item in roleOptions" :key="item.role" :label="`${item.role}（${item.count}）`" :value="item.role" />
      </el-select>
    </div>

    <div v-loading="loading" class="panel mistake-list">
      <div v-for="item in book?.mistakes || []" :key="item.questionId" class="mistake-row">
        <span class="mistake-score">
          <strong>{{ item.label }}</strong>
          <small v-if="item.gap">还差 {{ item.gap }} 分</small>
          <small v-else>待评分</small>
        </span>
        <span class="mistake-main">
          <strong>{{ item.text }}</strong>
          <small>
            <el-tag class="mistake-tag" size="small">{{ item.category }}</el-tag>
            {{ item.role }}<template v-if="item.difficultyLabel"> · {{ item.difficultyLabel }}</template><template v-if="item.weakestLabel"> · 主要栽在{{ item.weakestLabel }}</template>
            · {{ item.attemptsLabel }}
          </small>
        </span>
        <el-button class="soft-button mistake-action" :loading="retrying === item.questionId" @click="retry(item)">
          <el-icon><Refresh /></el-icon> 重练这道题
        </el-button>
      </div>

      <div v-if="!loading && book && !book.mistakes.length" class="mistake-empty">
        <strong>{{ role ? '这个岗位下暂时没有错题' : '错题本是空的' }}</strong>
        <p>{{ role ? '换个岗位看看，或者清掉筛选。' : '低分题会自动进本；重练过线后它会自己消失。' }}</p>
        <el-button v-if="role" class="soft-button" @click="changeRole('')">看全部错题</el-button>
        <el-button v-else class="soft-button" @click="router.push('/practice/setup')">去练一场</el-button>
      </div>

      <p v-if="!loading && book && book.total > book.mistakes.length" class="mistake-more">
        本页 {{ book.mistakes.length }} 条，共 {{ book.total }} 条错题。
      </p>
    </div>

    <div v-if="book && book.pageCount > 1" class="mistake-pager">
      <el-pagination background layout="prev, pager, next" :current-page="page" :page-size="PAGE_SIZE"
                     :total="book.total" @current-change="changePage" />
    </div>
  </section>
</template>

<style scoped>
.mistakes-page { max-width: 1100px; }
.report-back { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 7px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.report-back:hover { color: var(--green); }
.page-heading { align-items: flex-end; gap: 18px; }
.page-heading h1 { margin: 4px 0 6px; color: #2f3c35; font-size: 24px; font-weight: 650; }
.page-heading p { max-width: 720px; margin: 0; color: #8d9792; font-size: 13px; line-height: 1.7; }
.role-filter { width: 220px; flex: none; }
.mistake-list { min-height: 120px; }
.mistake-empty { display: grid; justify-items: center; gap: 9px; padding: 40px 20px; text-align: center; }
.mistake-empty strong { color: #46524c; font-size: 15px; }
.mistake-empty p { max-width: 460px; margin: 0; color: #8d9792; font-size: 13px; line-height: 1.75; }
.mistake-pager { display: flex; justify-content: flex-end; margin-top: 14px; }
@media (max-width: 680px) {
  .page-heading { align-items: flex-start; flex-direction: column; }
  .role-filter { width: 100%; }
}
</style>
