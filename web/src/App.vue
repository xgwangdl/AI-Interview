<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { usePracticeStore } from './stores/practice';
import { savePreferences } from './services/api';
import { THEMES, applyTheme, bootstrapTheme, rememberTheme } from './services/theme';
import { buildUsage, loadModelUsage } from './services/modelUsage';
import {
  applyAppearanceSettings, loadProfile, profileDisplayName, readCachedDisplayName, rememberDisplayName,
} from './services/profile';

const route = useRoute();
const router = useRouter();
const practice = usePracticeStore();
const menuOpen = ref(false);
const profileName = ref(readCachedDisplayName());
// 主题先按本地记录渲染（避免闪一下默认色），登录后再用服务端的偏好覆盖。
const theme = ref(bootstrapTheme());
/** 今日 AI 额度：和主题色一样是"账号状态"，顶栏一直能看见 */
const usage = ref(buildUsage(null));

async function chooseTheme(name) {
  theme.value = name;
  applyTheme(name);
  rememberTheme(name);
  try {
    await savePreferences({ theme: name });
  } catch {
    // 没登录或离线时只记在本地，不打断用户。
  }
}

async function loadThemePreference() {
  await loadAccountProfile();
}

async function loadAccountProfile() {
  if (!localStorage.getItem('token')) return;
  try {
    const profile = await loadProfile();
    profileName.value = profileDisplayName(profile);
    rememberDisplayName(profileName.value);
    applyAppearanceSettings(profile);
    if (profile.theme && THEMES[profile.theme]) theme.value = profile.theme;
  } catch {
    // 个人资料接口不可用时保留本地昵称与主题，不阻塞练习流程。
  }
}

function syncProfileName(event) {
  const name = event?.detail?.displayName;
  if (name) profileName.value = name;
}

function syncTheme(event) {
  if (THEMES[event?.detail?.theme]) theme.value = event.detail.theme;
}
watch(() => route.name, (name, previous) => {
  profileName.value = readCachedDisplayName();
  if (name !== 'login' && previous === 'login') {
    void refreshPractice();
    void loadThemePreference();
  }
});
const activeKey = computed(() => route.name === 'history' ? 'history' : 'dashboard');
const pageTitle = computed(() => ({ dashboard: '训练总览', today: '今日训练', 'weekly-report': '本周报告', notifications: '提醒设置', 'profile-settings': '个人设置', goals: '求职目标', 'goal-new': '添加目标', 'goal-edit': '编辑目标', 'goal-detail': '目标详情', cohorts: '学习小组', 'cohort-detail': '小组详情', setup: '创建训练', 'device-check': '设备检测', retake: '照着改写重录', session: '模拟面试', report: '练习报告', history: '练习记录', mistakes: '错题本', 'coding-setup': '笔试练习', 'coding-exam': '笔试作答', 'coding-report': '笔试报告', 'resume-list': '我的简历', 'resume-new': '添加简历', 'resume-detail': '简历详情', language: '外语口语', 'language-session': '外语对话' }[route.name] || '面试练习室'));
watch(() => route.fullPath, () => {
  menuOpen.value = false;
  // 每次换页顺手刷一次：一场练习里追问与配音都在扣额度，数字要跟得上
  void refreshUsage();
});

async function refreshUsage() {
  if (!localStorage.getItem('token')) {
    usage.value = buildUsage(null);
    return;
  }
  try {
    usage.value = buildUsage(await loadModelUsage());
  } catch {
    // 额度只是提示：拿不到就不显示，不能因此打扰用户
    usage.value = buildUsage(null);
  }
}

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

onMounted(() => {
  window.addEventListener('light-interview-profile-updated', syncProfileName);
  window.addEventListener('light-interview-theme-updated', syncTheme);
  if (route.name !== 'login') {
    void refreshPractice();
    void refreshUsage();
    void loadAccountProfile();
  }
});

onUnmounted(() => {
  window.removeEventListener('light-interview-profile-updated', syncProfileName);
  window.removeEventListener('light-interview-theme-updated', syncTheme);
});

function navigate(path) {
  menuOpen.value = false;
  router.push(path);
}

function accountCommand(command) {
  if (command === 'profile') {
    router.push('/settings/profile');
    return;
  }
  if (command === 'notifications') {
    router.push('/settings/notifications');
    return;
  }
  if (command !== 'logout') return;
  ['token', 'userId', 'userName', 'username', 'light-interview-display-name'].forEach((key) => localStorage.removeItem(key));
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
        <button :class="{ active: ['goals', 'goal-new', 'goal-edit', 'goal-detail', 'today'].includes(route.name) }" @click="navigate('/goals')"><el-icon><Aim /></el-icon><span>求职目标</span></button>
        <button :class="{ active: route.name === 'cohorts' || route.name === 'cohort-detail' }" @click="navigate('/cohorts')"><el-icon><UserFilled /></el-icon><span>学习小组</span></button>
        <button :class="{ active: route.name === 'setup' }" @click="navigate('/practice/setup')"><el-icon><CirclePlus /></el-icon><span>开始新练习</span></button>
        <button :class="{ active: route.name === 'coding-setup' }" @click="navigate('/coding')"><el-icon><EditPen /></el-icon><span>笔试练习</span></button>
        <button :class="{ active: route.name === 'language' || route.name === 'language-session' }" @click="navigate('/language')"><el-icon><ChatDotRound /></el-icon><span>外语口语</span></button>
        <button :class="{ active: route.name === 'resume-list' || route.name === 'resume-detail' || route.name === 'resume-new' }" @click="navigate('/resumes')"><el-icon><Document /></el-icon><span>我的简历</span></button>
        <button :class="{ active: activeKey === 'history' }" @click="navigate('/history')"><el-icon><Clock /></el-icon><span>练习记录</span></button>
      </nav>
      <div class="sidebar-bottom">
        <div class="theme-picker" role="group" aria-label="页面主题色">
          <span class="theme-label">主题色</span>
          <button
            v-for="(item, key) in THEMES"
            :key="key"
            type="button"
            class="theme-dot"
            :class="{ active: theme === key }"
            :style="{ background: item.swatch }"
            :title="item.label"
            :aria-label="item.label"
            :aria-pressed="theme === key"
            @click="chooseTheme(key)"
          ></button>
        </div>
        <div class="privacy-note"><el-icon><Lock /></el-icon><span>录制内容仅保留 7 天<small>可随时删除练习数据</small></span></div>
        <el-dropdown class="profile-row" trigger="click" @command="accountCommand"><span class="profile-trigger"><div class="profile-avatar">{{ profileName.slice(0,1) }}</div><span class="profile-name">{{ profileName }}<small>个人空间</small></span><el-icon class="profile-more"><MoreFilled /></el-icon></span><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile"><el-icon><User /></el-icon>个人设置</el-dropdown-item><el-dropdown-item command="notifications"><el-icon><Bell /></el-icon>提醒设置</el-dropdown-item><el-dropdown-item divided command="logout"><el-icon><SwitchButton /></el-icon>退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
    </aside>

    <div v-if="menuOpen" class="mobile-backdrop" @click="menuOpen = false"></div>
    <main class="main-area">
      <header class="topbar">
        <button class="mobile-menu" aria-label="打开导航" @click="menuOpen = true"><el-icon><Menu /></el-icon></button>
        <div class="breadcrumb"><span>工作空间</span><el-icon><ArrowRight /></el-icon><strong>{{ pageTitle }}</strong></div>
        <div class="topbar-actions">
          <span v-if="usage.enabled" class="quota-chip" :class="{ tight: usage.tight, empty: usage.exhausted }"
                :title="`${usage.note}${usage.resetLabel ? ' ' + usage.resetLabel : ''}`">
            <el-icon><Lightning /></el-icon>{{ usage.label }}<small v-if="usage.resetLabel">{{ usage.resetLabel }}</small>
          </span>
          <span class="privacy-status"><i></i>个人练习空间</span><button class="help-button" aria-label="查看隐私信息" title="查看隐私信息"><el-icon><QuestionFilled /></el-icon></button>
        </div>
      </header>
      <div class="page-content"><RouterView /></div>
    </main>
  </div>
</template>
