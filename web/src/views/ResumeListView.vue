<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { deleteResume, listResumes } from '../services/resume';
import { useI18n } from 'vue-i18n';

const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const resumes = ref([]);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    resumes.value = await listResumes();
  } catch (error) {
    ElMessage.error(error.message || t('resume.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function remove(item) {
  await ElMessageBox.confirm(
    t('resume.deleteConfirmBody', { title: item.title }),
    t('resume.deleteConfirmTitle'),
    { type: 'warning' },
  );
  try {
    await deleteResume(item.id);
    await load();
  } catch (error) {
    ElMessage.error(error.message || t('resume.deleteFailed'));
  }
}
</script>

<template>
  <section v-loading="loading" class="resume-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('resume.eyebrow') }}</div>
        <h1>{{ t('resume.title') }}</h1>
        <p>{{ t('resume.subtitle') }}</p>
      </div>
      <el-button class="primary-button" @click="router.push('/resumes/new')">
        <el-icon><Plus /></el-icon> {{ t('resume.add') }}
      </el-button>
    </div>

    <el-empty v-if="!resumes.length" :description="t('resume.empty')" />

    <div class="resume-grid">
      <article v-for="item in resumes" :key="item.id" class="panel resume-card" @click="router.push(`/resumes/${item.id}`)">
        <div class="card-head">
          <strong>{{ item.title }}</strong>
          <el-tag :class="item.optimized ? 'tag-ready' : 'tag-muted'" size="small">
            {{ item.optimized ? t('resume.optimized') : t('resume.unoptimized') }}
          </el-tag>
        </div>
        <div class="card-meta">
          <span>{{ item.source === 'upload' ? t('resume.uploadSource') : t('resume.manualSource') }}</span>
          <i></i>
          <span>{{ t('resume.characters', { count: item.characters }) }}</span>
          <i></i>
          <span>v{{ item.version }}</span>
          <i></i>
          <span>{{ String(item.updatedAt).slice(0, 10) }}</span>
        </div>
        <div class="card-actions">
          <el-button class="soft-button" size="small" @click.stop="router.push(`/resumes/${item.id}`)">{{ t('resume.open') }}</el-button>
          <el-button class="soft-button danger" size="small" @click.stop="remove(item)">{{ t('resume.delete') }}</el-button>
        </div>
      </article>
    </div>

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
</style>
