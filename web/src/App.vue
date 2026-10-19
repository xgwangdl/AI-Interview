<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import en from 'element-plus/es/locale/lang/en';
import ja from 'element-plus/es/locale/lang/ja';
import { useI18n } from 'vue-i18n';
import { usePracticeStore } from './stores/practice';
import { buildUsage, loadModelUsage } from './services/modelUsage';
import {
  applyAppearanceSettings, loadProfile, profileDisplayName, readCachedDisplayName, rememberDisplayName,
} from './services/profile';
import { normalizeLocale, setLocale } from './i18n';
import { clearLocalSession } from './services/authSession';

const route = useRoute();
const router = useRouter();
const { t, locale } = useI18n();
const practice = usePracticeStore();
const menuOpen = ref(false);
const profileName = ref(readCachedDisplayName());
/** 今日 AI 额度是账号状态，登录后一直显示在顶栏。 */
const usageData = ref(null);
const usage = computed(() => buildUsage(usageData.value, t));
const elementLocale = computed(() => locale.value === 'en-US' ? en : locale.value === 'ja-JP' ? ja : zhCn);

async function loadAccountProfile() {
  if (!localStorage.getItem('token')) return;
  try {
    const profile = await loadProfile();
    profileName.value = profileDisplayName(profile);
    rememberDisplayName(profileName.value);
    applyAppearanceSettings(profile);
    setLocale(normalizeLocale(profile.language));
  } catch {
    // 个人资料接口不可用时保留本地昵称与主题，不阻塞练习流程。
  }
}

function syncProfileName(event) {
  const name = event?.detail?.displayName;
  if (name) profileName.value = name;
}

watch(() => route.name, (name, previous) => {
  profileName.value = readCachedDisplayName();
  if (name !== 'login' && previous === 'login') {
    void refreshPractice();
    void loadAccountProfile();
  }
});
const activeKey = computed(() => route.name === 'history' ? 'history' : 'dashboard');
const pageTitle = computed(() => route.name && !['login'].includes(route.name)
  ? t(`routes.${route.name}`)
  : t('app.name'));
watch([pageTitle, locale], () => {
  document.title = `${pageTitle.value} | ${t('app.name')}`;
}, { immediate: true });
watch(() => route.fullPath, () => {
  menuOpen.value = false;
  // 每次换页顺手刷一次：一场练习里追问与配音都在扣额度，数字要跟得上
  void refreshUsage();
});

async function refreshUsage() {
  if (!localStorage.getItem('token')) {
    usageData.value = null;
    return;
  }
  try {
    usageData.value = await loadModelUsage();
  } catch {
    // 额度只是提示：拿不到就不显示，不能因此打扰用户
    usageData.value = null;
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
  if (route.name !== 'login') {
    void refreshPractice();
    void refreshUsage();
    void loadAccountProfile();
  }
});

onUnmounted(() => {
  window.removeEventListener('light-interview-profile-updated', syncProfileName);
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
  clearLocalSession();
  window.location.assign('/login');
}
</script>

<template>
  <el-config-provider :locale="elementLocale">
  <div v-if="route.name === 'login'" class="login-shell"><RouterView /></div>
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ 'is-open': menuOpen }">
      <button class="brand" type="button" @click="navigate('/')">
        <span class="brand-mark"><span></span><span></span><span></span></span>
        <span>{{ t('app.name') }}<small>{{ t('app.subtitle') }}</small></span>
      </button>
      <div class="workspace-label">{{ t('app.workspace') }}</div>
      <nav class="main-nav" :aria-label="t('app.mainNavigation')">
        <button :class="{ active: activeKey === 'dashboard' }" @click="navigate('/')"><el-icon><DataLine /></el-icon><span>{{ t('nav.dashboard') }}</span></button>
        <button :class="{ active: ['goals', 'goal-new', 'goal-edit', 'goal-detail', 'today'].includes(route.name) }" @click="navigate('/goals')"><el-icon><Aim /></el-icon><span>{{ t('nav.goals') }}</span></button>
        <button :class="{ active: route.name === 'cohorts' || route.name === 'cohort-detail' }" @click="navigate('/cohorts')"><el-icon><UserFilled /></el-icon><span>{{ t('nav.cohorts') }}</span></button>
        <button :class="{ active: route.name === 'setup' }" @click="navigate('/practice/setup')"><el-icon><CirclePlus /></el-icon><span>{{ t('nav.newPractice') }}</span></button>
        <button :class="{ active: route.name === 'coding-setup' }" @click="navigate('/coding')"><el-icon><EditPen /></el-icon><span>{{ t('nav.coding') }}</span></button>
        <button :class="{ active: route.name === 'language' || route.name === 'language-session' }" @click="navigate('/language')"><el-icon><ChatDotRound /></el-icon><span>{{ t('nav.language') }}</span></button>
        <button :class="{ active: route.name === 'resume-list' || route.name === 'resume-detail' || route.name === 'resume-new' }" @click="navigate('/resumes')"><el-icon><Document /></el-icon><span>{{ t('nav.resumes') }}</span></button>
        <button :class="{ active: activeKey === 'history' }" @click="navigate('/history')"><el-icon><Clock /></el-icon><span>{{ t('nav.history') }}</span></button>
      </nav>
      <div class="sidebar-bottom">
        <div class="privacy-note"><el-icon><Lock /></el-icon><span>{{ t('app.retention') }}<small>{{ t('app.retentionHint') }}</small></span></div>
        <el-dropdown class="profile-row" trigger="click" @command="accountCommand"><span class="profile-trigger"><div class="profile-avatar">{{ profileName.slice(0,1) }}</div><span class="profile-name">{{ profileName }}<small>{{ t('app.personalSpace') }}</small></span><el-icon class="profile-more"><MoreFilled /></el-icon></span><template #dropdown><el-dropdown-menu><el-dropdown-item command="profile"><el-icon><User /></el-icon>{{ t('menu.profile') }}</el-dropdown-item><el-dropdown-item command="notifications"><el-icon><Bell /></el-icon>{{ t('menu.notifications') }}</el-dropdown-item><el-dropdown-item divided command="logout"><el-icon><SwitchButton /></el-icon>{{ t('menu.logout') }}</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
      </div>
    </aside>

    <div v-if="menuOpen" class="mobile-backdrop" @click="menuOpen = false"></div>
    <main class="main-area">
      <header class="topbar">
        <button class="mobile-menu" :aria-label="t('app.openNavigation')" @click="menuOpen = true"><el-icon><Menu /></el-icon></button>
        <div class="breadcrumb"><span>{{ t('app.workspace') }}</span><el-icon><ArrowRight /></el-icon><strong>{{ pageTitle }}</strong></div>
        <div class="topbar-actions">
          <span v-if="usage.enabled" class="quota-chip" :class="{ tight: usage.tight, empty: usage.exhausted }"
                :title="`${usage.note}${usage.resetLabel ? ' ' + usage.resetLabel : ''}`">
            <el-icon><Lightning /></el-icon><span class="quota-text">{{ usage.label }}</span><small v-if="usage.resetLabel">{{ usage.resetLabel }}</small>
          </span>
          <span class="privacy-status"><i></i>{{ t('app.personalPractice') }}</span><button class="help-button" :aria-label="t('app.privacyHelp')" :title="t('app.privacyHelp')"><el-icon><QuestionFilled /></el-icon></button>
        </div>
      </header>
      <div class="page-content"><RouterView /></div>
    </main>
  </div>
  </el-config-provider>
</template>
