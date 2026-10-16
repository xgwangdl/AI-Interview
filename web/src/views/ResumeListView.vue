<script setup>
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { deleteResume, listResumes } from '../services/resume';

const router = useRouter();
const loading = ref(true);
const resumes = ref([]);

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
      <el-button class="primary-button" @click="router.push('/resumes/new')">
        <el-icon><Plus /></el-icon> 添加简历
      </el-button>
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
