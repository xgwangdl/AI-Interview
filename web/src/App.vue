<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from './stores/practice';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const menuOpen = ref(false);
const profileName = ref(localStorage.getItem('userName') || '练习者');
watch(() => route.name, (name, previous) => {
  profileName.value = localStorage.getItem('userName') || '练习者';
  if (name !== 'login' && previous === 'login') void refreshPractice();
});
const activeKey = computed(() => route.name === 'history' ? 'history' : 'dashboard');
const pageTitle = computed(() => ({ dashboard: '训练总览', setup: '创建训练', 'device-check': '设备检测', session: '模拟面试', report: '练习报告', history: '练习记录', 'coding-setup': '笔试练习', 'coding-exam': '笔试作答', 'coding-report': '笔试报告' }[route.name] || '面试练习室'));
watch(() => route.fullPath, () => { menuOpen.value = false; });

async function refreshPractice() {
  // 登录页与首次跳转期间还没有 token，直接跳过，避免必然 401 的请求和误导性报错。
  if (!localStorage.getItem('token')) return;
  try {
    await practice.loadFromServer();
    await practice.pruneExpired();
  } catch (error) {
    // 登录过期时守卫会跳转登录页，这时再弹「检查后端连接」只会误导用户。
    if (error?.status !== 401) ElMessage.error('无法读取练习记录，请检查后端连接后刷新页面。');
  }
}

onMounted(() => { if (route.name !== 'login') void refreshPractice(); });

function navigate(path) {
  menuOpen.value = false;
  router.push(path);
}

function accountCommand(command) {
  if (command !== 'logout') return;
  ['token', 'userId', 'userName', 'username'].forEach((key) => localStorage.removeItem(key));
  window.location.assign('/login');
}
</script>

<template>
  <div v-if="route.name === 'login'" class="login-shell"><RouterView /></div>
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ 'is-open': menuOpen }">
      <button class="brand" type="button" @click="navigate('/')">
        <span class="brand-mark"><span></span><span></span><span></span></span>
        <span>面试练习室<small>INTERVIEW STUDIO</small></span>
      </button>
      <div class="workspace-label">工作空间</div>
      <nav class="main-nav" aria-label="主导航">
        <button :class="{ active: activeKey === 'dashboard' }" @click="navigate('/')"><el-icon><DataLine /></el-icon><span>训练总览</span></button>
        <button :class="{ active: route.name === 'setup' }" @click="navigate('/practice/setup')"><el-icon><CirclePlus /></el-icon><span>开始新练习</span></button>
        <button :class="{ active: route.name === 'coding-setup' }" @click="navigate('/coding')"><el-icon><EditPen /></el-icon><span>笔试练习</span></button>
        <button :class="{ active: activeKey === 'history' }" @click="navigate('/history')"><el-icon><Clock /></el-icon><span>练习记录</span></button>
      </nav>
      <div class="sidebar-bottom">
        <div class="privacy-note"><el-icon><Lock /></el-icon><span>录制内容仅保留 7 天<small>可随时删除练习数据</small></span></div>
        <el-dropdown class="profile-row" trigger="click" @command="accountCommand"><span class="profile-trigger"><div class="profile-avatar">{{ profileName.slice(0,1) }}</div><span class="profile-name">{{ profileName }}<small>个人空间</small></span><el-icon class="profile-more"><MoreFilled /></el-icon></span><template #dropdown><el-dropdown-menu><el-dropdown-item command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
    </aside>

    <div v-if="menuOpen" class="mobile-backdrop" @click="menuOpen = false"></div>
    <main class="main-area">
      <header class="topbar">
        <button class="mobile-menu" aria-label="打开导航" @click="menuOpen = true"><el-icon><Menu /></el-icon></button>
        <div class="breadcrumb"><span>工作空间</span><el-icon><ArrowRight /></el-icon><strong>{{ pageTitle }}</strong></div>
        <div class="topbar-actions"><span class="privacy-status"><i></i>个人练习空间</span><button class="help-button" aria-label="查看隐私信息" title="查看隐私信息"><el-icon><QuestionFilled /></el-icon></button></div>
      </header>
      <div class="page-content"><RouterView /></div>
    </main>
  </div>
</template>
