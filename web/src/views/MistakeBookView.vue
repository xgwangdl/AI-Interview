<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from '../stores/practice';
import { PAGE_SIZE, buildMistakes, loadMistakes } from '../services/practiceMistakes';
import { useI18n } from 'vue-i18n';

/**
 * 错题本全量页。
 *
 * 总览页只放最差的前 5 条（一次列几十条会让人不想开始），但要能"看到后面那些题"，
 * 所以这一页做真分页 + 按岗位筛选；重练走的还是同一条链路（store 里的 retryMistake）。
 */
const router = useRouter();
const { t } = useI18n();
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
    }), t);
  } catch (error) {
    ElMessage.error(error?.message || t('mistakeBook.loadFailed'));
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
    ElMessage.error(error?.message || t('mistakeBook.retryFailed'));
  } finally {
    retrying.value = '';
  }
}
</script>

<template>
  <section class="mistakes-page">
    <div class="page-heading">
      <div>
        <button class="report-back" @click="router.push('/')"><el-icon><ArrowLeft /></el-icon>{{ t('mistakeBook.back') }}</button>
        <h1>{{ t('mistakeBook.title') }}</h1>
        <p v-if="book">{{ book.scopeNote }}</p>
      </div>
      <el-select v-if="roleOptions.length > 1" :model-value="role" class="role-filter"
                 :placeholder="t('mistakeBook.allRoles')" clearable @change="changeRole">
        <el-option :label="t('mistakeBook.allRoles')" value="" />
        <el-option v-for="item in roleOptions" :key="item.role" :label="t('mistakeBook.roleOption', { role: item.role, count: item.count })" :value="item.role" />
      </el-select>
    </div>

    <div v-loading="loading" class="panel mistake-list">
      <div v-for="item in book?.mistakes || []" :key="item.questionId" class="mistake-row">
        <span class="mistake-score">
          <strong>{{ item.label }}</strong>
          <small v-if="item.gap">{{ t('mistakeBook.gap', { gap: item.gap }) }}</small>
          <small v-else>{{ t('mistakeBook.pendingScore') }}</small>
        </span>
        <span class="mistake-main">
          <strong>{{ item.text }}</strong>
          <small>
            <el-tag class="mistake-tag" size="small">{{ item.category }}</el-tag>
            {{ item.role }}<template v-if="item.difficultyLabel"> · {{ item.difficultyLabel }}</template><template v-if="item.weakestLabel">{{ t('mistakeBook.mainWeakness', { name: item.weakestLabel }) }}</template>
            · {{ item.attemptsLabel }}
          </small>
        </span>
        <el-button class="soft-button mistake-action" :loading="retrying === item.questionId" @click="retry(item)">
          <el-icon><Refresh /></el-icon> {{ t('mistakeBook.retry') }}
        </el-button>
      </div>

      <div v-if="!loading && book && !book.mistakes.length" class="mistake-empty">
        <strong>{{ role ? t('mistakeBook.emptyRole') : t('mistakeBook.empty') }}</strong>
        <p>{{ role ? t('mistakeBook.emptyRoleHint') : t('mistakeBook.emptyHint') }}</p>
        <el-button v-if="role" class="soft-button" @click="changeRole('')">{{ t('mistakeBook.viewAll') }}</el-button>
        <el-button v-else class="soft-button" @click="router.push('/practice/setup')">{{ t('mistakeBook.practice') }}</el-button>
      </div>

      <p v-if="!loading && book && book.total > book.mistakes.length" class="mistake-more">
        {{ t('mistakeBook.pageSummary', { visible: book.mistakes.length, total: book.total }) }}
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
