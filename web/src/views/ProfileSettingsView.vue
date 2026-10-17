<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { THEMES } from '../services/theme';
import {
  FONT_SCALE_OPTIONS, TIMEZONE_OPTIONS, applyAppearanceSettings, changePassword,
  formatJoinedAt, loadProfile, normalizeFontScale, profileDisplayName, profileInitial,
  rememberDisplayName, saveProfile,
} from '../services/profile';
import { loadNotificationPreferences } from '../services/progress';

const router = useRouter();
const loading = ref(true);
const savingProfile = ref(false);
const savingPassword = ref(false);
const notifications = ref(null);
const profile = reactive({
  loginName: '',
  displayName: '',
  email: '',
  timezone: 'Asia/Shanghai',
  fontScale: 'standard',
  reduceMotion: false,
  theme: 'green',
  joinedAt: null,
});
const password = reactive({ current: '', next: '', confirm: '' });

const currentName = computed(() => profileDisplayName(profile));
const timezoneOptions = computed(() => {
  if (!profile.timezone || TIMEZONE_OPTIONS.some((item) => item.value === profile.timezone)) {
    return TIMEZONE_OPTIONS;
  }
  return [...TIMEZONE_OPTIONS, { value: profile.timezone, label: profile.timezone }];
});

onMounted(async () => {
  window.addEventListener('light-interview-theme-updated', syncTheme);
  await load();
});

onUnmounted(() => {
  window.removeEventListener('light-interview-theme-updated', syncTheme);
});

function syncTheme(event) {
  if (event?.detail?.theme && THEMES[event.detail.theme]) {
    profile.theme = event.detail.theme;
  }
}

async function load() {
  loading.value = true;
  try {
    Object.assign(profile, await loadProfile());
    profile.fontScale = normalizeFontScale(profile.fontScale);
    applyAppearanceSettings(profile);
    rememberDisplayName(currentName.value);
    void loadReminderSummary();
  } catch (error) {
    ElMessage.error(error?.message || '无法读取个人资料');
  } finally {
    loading.value = false;
  }
}

async function loadReminderSummary() {
  try {
    notifications.value = await loadNotificationPreferences();
  } catch {
    notifications.value = null;
  }
}

function previewAppearance() {
  applyAppearanceSettings(profile);
}

function chooseTheme(name) {
  profile.theme = name;
  previewAppearance();
}

function chooseFontScale(value) {
  profile.fontScale = value;
  previewAppearance();
}

async function submitProfile() {
  if (savingProfile.value) return;
  const displayName = profile.displayName.trim();
  const email = (profile.email || '').trim();
  if (displayName.length < 2 || displayName.length > 20) {
    ElMessage.warning('昵称长度需在 2 到 20 个字符之间。');
    return;
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning('请输入有效的邮箱地址。');
    return;
  }
  savingProfile.value = true;
  try {
    const updated = await saveProfile({
      displayName,
      email,
      timezone: profile.timezone,
      fontScale: profile.fontScale,
      reduceMotion: profile.reduceMotion,
      theme: profile.theme,
    });
    Object.assign(profile, updated);
    applyAppearanceSettings(profile);
    rememberDisplayName(profileDisplayName(profile));
    ElMessage.success('个人资料与外观设置已保存');
  } catch (error) {
    ElMessage.error(error?.message || '保存个人资料失败');
  } finally {
    savingProfile.value = false;
  }
}

async function submitPassword() {
  if (savingPassword.value) return;
  if (!password.current) {
    ElMessage.warning('请输入当前密码。');
    return;
  }
  if (password.next.length < 6) {
    ElMessage.warning('新密码至少需要 6 位。');
    return;
  }
  if (password.next !== password.confirm) {
    ElMessage.warning('两次输入的新密码不一致。');
    return;
  }
  savingPassword.value = true;
  try {
    await changePassword({ currentPassword: password.current, newPassword: password.next });
    password.current = '';
    password.next = '';
    password.confirm = '';
    ElMessage.success('密码已更新，下次登录请使用新密码');
  } catch (error) {
    ElMessage.error(error?.message || '修改密码失败');
  } finally {
    savingPassword.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="profile-settings-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">PERSONAL SETTINGS</div>
        <h1>个人设置</h1>
        <p>管理你的公开昵称、界面显示方式和账号安全。登录名与历史练习数据保持稳定关联。</p>
      </div>
    </div>

    <div class="settings-layout">
      <section class="panel settings-panel profile-panel">
        <div class="panel-heading">
          <div>
            <h2>个人资料</h2>
            <p>昵称用于页面展示，不会改变登录名。</p>
          </div>
          <span class="panel-icon"><el-icon><User /></el-icon></span>
        </div>

        <div class="identity-strip">
          <div class="profile-avatar large">{{ profileInitial(currentName) }}</div>
          <div>
            <strong>{{ currentName }}</strong>
            <span>加入于 {{ formatJoinedAt(profile.joinedAt) }}</span>
          </div>
          <el-tag class="account-tag" effect="plain">个人账号</el-tag>
        </div>

        <el-form class="settings-form" label-position="top" @submit.prevent="submitProfile">
          <div class="form-grid">
            <el-form-item label="昵称">
              <el-input v-model="profile.displayName" maxlength="20" show-word-limit placeholder="2-20 个字符" />
            </el-form-item>
            <el-form-item label="登录名">
              <el-input :model-value="profile.loginName" disabled>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
              <span class="field-hint">登录名是历史练习与简历的归属标识，暂不支持修改。</span>
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profile.email" maxlength="255" placeholder="用于接收练习提醒">
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item label="所在时区">
              <el-select v-model="profile.timezone" filterable>
                <el-option
                  v-for="item in timezoneOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </div>
        </el-form>
      </section>

      <section class="panel settings-panel appearance-panel">
        <div class="panel-heading">
          <div>
            <h2>外观主题</h2>
            <p>选择更适合你的颜色、字号和动态效果。</p>
          </div>
          <span class="panel-icon warm"><el-icon><Sunny /></el-icon></span>
        </div>

        <div class="setting-block">
          <div class="block-label"><strong>主题色</strong><span>选择后会立即预览</span></div>
          <div class="theme-cards">
            <button
              v-for="(item, key) in THEMES"
              :key="key"
              type="button"
              class="theme-card"
              :class="{ active: profile.theme === key }"
              :aria-pressed="profile.theme === key"
              @click="chooseTheme(key)"
            >
              <span class="theme-preview" :style="{ background: item.vars['--green-soft'] }">
                <i :style="{ background: item.swatch }"></i>
                <i></i><i></i>
              </span>
              <span>{{ item.label }}</span>
              <el-icon v-if="profile.theme === key" class="theme-check"><Check /></el-icon>
            </button>
          </div>
        </div>

        <div class="setting-block">
          <div class="block-label"><strong>界面字号</strong><span>同时调整文字与控件比例</span></div>
          <div class="font-options">
            <button
              v-for="item in FONT_SCALE_OPTIONS"
              :key="item.value"
              type="button"
              :class="{ active: profile.fontScale === item.value }"
              :aria-pressed="profile.fontScale === item.value"
              @click="chooseFontScale(item.value)"
            >
              <b :style="{ fontSize: item.value === 'standard' ? '17px' : item.value === 'large' ? '19px' : '21px' }">{{ item.sample }}</b>
              <span>{{ item.label }}</span>
              <small>{{ item.scale }}</small>
            </button>
          </div>
        </div>

        <div class="motion-row">
          <div>
            <strong>减弱动态效果</strong>
            <span>关闭数字人漂浮、进度过渡等非必要动画</span>
          </div>
          <el-switch v-model="profile.reduceMotion" @change="previewAppearance" />
        </div>
      </section>

      <section class="panel settings-panel security-panel">
        <div class="panel-heading">
          <div>
            <h2>账号安全</h2>
            <p>修改密码后，当前登录保持有效，下次登录使用新密码。</p>
          </div>
          <span class="panel-icon secure"><el-icon><Key /></el-icon></span>
        </div>
        <el-form class="settings-form password-form" label-position="top" @submit.prevent="submitPassword">
          <el-form-item label="当前密码">
            <el-input v-model="password.current" type="password" show-password maxlength="100" autocomplete="current-password" />
          </el-form-item>
          <div class="form-grid">
            <el-form-item label="新密码">
              <el-input v-model="password.next" type="password" show-password maxlength="100" autocomplete="new-password" placeholder="至少 6 位" />
            </el-form-item>
            <el-form-item label="确认新密码">
              <el-input v-model="password.confirm" type="password" show-password maxlength="100" autocomplete="new-password" />
            </el-form-item>
          </div>
        </el-form>
      </section>

      <section class="panel reminder-link-panel">
        <span class="panel-icon reminder"><el-icon><Bell /></el-icon></span>
        <div>
          <strong>提醒设置</strong>
          <p v-if="notifications">
            {{ notifications.emailEnabled ? '邮件提醒已开启' : '邮件提醒已关闭' }}
            <span>·</span>
            {{ notifications.emailEnabled ? `每日 ${notifications.reminderTime} 提醒` : '可随时重新开启' }}
          </p>
          <p v-else>管理每日练习、周报和面试倒计时邮件。</p>
        </div>
        <el-button class="soft-button" @click="router.push('/settings/notifications')">
          管理提醒<el-icon><ArrowRight /></el-icon>
        </el-button>
      </section>
    </div>

    <div class="settings-actions">
      <span>修改会同步到你的账号，换设备登录后仍然生效。</span>
      <div>
        <el-button class="soft-button" :loading="savingPassword" :disabled="savingPassword" @click="submitPassword">更新密码</el-button>
        <el-button class="primary-button" :loading="savingProfile" :disabled="savingProfile" @click="submitProfile">保存个人设置</el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.profile-settings-page { max-width: 1040px; }
.settings-layout { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(330px, .9fr); gap: 16px; align-items: start; }
.settings-panel { padding: 22px; }
.profile-panel { grid-row: span 2; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 18px; }
.panel-heading h2 { margin: 0; color: #293630; font-size: 17px; font-weight: 650; }
.panel-heading p { margin: 6px 0 0; color: #8b9690; font-size: 13px; line-height: 1.65; }
.panel-icon { display: grid; width: 34px; height: 34px; flex: none; place-items: center; border-radius: 7px; background: var(--green-soft); color: var(--green); font-size: 17px; }
.panel-icon.warm { background: #fbf2df; color: #ad7726; }
.panel-icon.secure { background: var(--blue-soft); color: var(--blue); }
.panel-icon.reminder { background: var(--coral-soft); color: #b96a54; }
.identity-strip { display: flex; align-items: center; gap: 11px; margin-top: 20px; padding: 14px; border: 1px solid #e9ede9; border-radius: 7px; background: #fafcf9; }
.profile-avatar.large { width: 42px; height: 42px; flex: none; font-size: 18px; }
.identity-strip > div:nth-child(2) { display: grid; min-width: 0; gap: 4px; }
.identity-strip strong { overflow: hidden; color: #34413a; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.identity-strip span { color: #919b95; font-size: 12px; }
.account-tag { margin-left: auto; }
.settings-form { margin-top: 19px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 0 14px; }
.settings-form :deep(.el-form-item) { display: block; margin-bottom: 17px; }
.settings-form :deep(.el-form-item__label) { height: auto; margin-bottom: 7px; color: #59665f; font-size: 13px; font-weight: 600; line-height: 1.4; }
.settings-form :deep(.el-input__wrapper), .settings-form :deep(.el-select__wrapper) { min-height: 39px; }
.settings-form :deep(.el-select) { width: 100%; }
.field-hint { display: block; margin-top: 5px; color: #98a19c; font-size: 12px; line-height: 1.55; }
.setting-block { margin-top: 19px; }
.block-label { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.block-label strong, .motion-row strong { color: #4a5750; font-size: 13px; }
.block-label span, .motion-row span { color: #98a19c; font-size: 12px; }
.theme-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.theme-card { position: relative; display: grid; gap: 7px; padding: 7px; border: 1px solid #e5eae6; border-radius: 7px; background: white; color: #66736c; font-family: inherit; font-size: 12px; text-align: left; }
.theme-card:hover { border-color: #cbd8d0; }
.theme-card.active { border-color: var(--green); box-shadow: 0 0 0 1px var(--green); color: var(--green-dark); }
.theme-preview { display: flex; height: 52px; align-items: flex-end; gap: 4px; padding: 9px; border-radius: 4px; }
.theme-preview i { display: block; width: 13%; height: 80%; border-radius: 2px; background: rgba(255,255,255,.9); }
.theme-preview i:first-child { width: 22%; height: 100%; }
.theme-preview i:last-child { height: 55%; }
.theme-check { position: absolute; top: 6px; right: 6px; border-radius: 50%; background: white; color: var(--green); font-size: 15px; }
.font-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.font-options button { display: grid; min-height: 65px; align-content: center; justify-items: center; gap: 2px; padding: 7px; border: 1px solid #e5eae6; border-radius: 6px; background: white; color: #68756e; font-family: inherit; }
.font-options button:hover { border-color: #cbd8d0; }
.font-options button.active { border-color: var(--green); background: var(--green-soft); color: var(--green-dark); }
.font-options b { line-height: 1.05; }
.font-options span { font-size: 12px; font-weight: 600; }
.font-options small { color: #9aa39e; font-size: 10px; }
.motion-row { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-top: 18px; padding-top: 16px; border-top: 1px solid #edf0ed; }
.motion-row > div { display: grid; gap: 5px; }
.motion-row span { line-height: 1.5; }
.password-form :deep(.el-form-item:first-child) { max-width: calc(50% - 7px); }
.reminder-link-panel { grid-column: 2; display: flex; align-items: center; gap: 11px; padding: 16px 18px; }
.reminder-link-panel > div { display: grid; min-width: 0; flex: 1; gap: 4px; }
.reminder-link-panel strong { color: #46534c; font-size: 14px; }
.reminder-link-panel p { margin: 0; color: #929c97; font-size: 12px; line-height: 1.55; }
.reminder-link-panel p span { margin: 0 4px; color: #c2c9c5; }
.reminder-link-panel .el-button { flex: none; }
.settings-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 18px; padding: 15px 2px 0; }
.settings-actions > span { color: #929c96; font-size: 12px; }
.settings-actions > div { display: flex; gap: 9px; }
@media (max-width: 980px) {
  .settings-layout { grid-template-columns: 1fr; }
  .profile-panel { grid-row: auto; }
  .reminder-link-panel { grid-column: auto; }
}
@media (max-width: 560px) {
  .settings-panel { padding: 18px 15px; }
  .form-grid { grid-template-columns: 1fr; }
  .password-form :deep(.el-form-item:first-child) { max-width: none; }
  .settings-actions { align-items: stretch; flex-direction: column; }
  .settings-actions > div { display: grid; grid-template-columns: 1fr 1fr; }
  .reminder-link-panel { display: grid; grid-template-columns: 34px minmax(0, 1fr); align-items: start; }
  .reminder-link-panel > div { min-width: 0; }
  .reminder-link-panel .el-button { grid-column: 1 / -1; width: 100%; margin: 4px 0 0; }
}
</style>
