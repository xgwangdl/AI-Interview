<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { THEMES } from '../services/theme';
import {
  FONT_SCALE_OPTIONS, TIMEZONE_OPTIONS, applyAppearanceSettings, changePassword,
  formatJoinedAt, loadProfile, normalizeFontScale, profileDisplayName, profileInitial,
  rememberDisplayName, saveProfile,
} from '../services/profile';
import { LOCALE_OPTIONS, normalizeLocale, setLocale } from '../i18n';

const { t, locale } = useI18n();
const loading = ref(true);
const savingProfile = ref(false);
const savingPassword = ref(false);
const profile = reactive({
  loginName: '',
  displayName: '',
  email: '',
  timezone: 'Asia/Shanghai',
  fontScale: 'standard',
  reduceMotion: false,
  theme: 'green',
  language: 'zh-CN',
  joinedAt: null,
});
const password = reactive({ next: '', confirm: '' });

const currentName = computed(() => profileDisplayName(profile));
const joinedAtLabel = computed(() => formatJoinedAt(profile.joinedAt, locale.value));
const timezoneOptions = computed(() => {
  const values = TIMEZONE_OPTIONS.map((item) => item.value);
  const options = TIMEZONE_OPTIONS.map((item) => ({
    value: item.value,
    label: t(`timezones.${item.value}`),
  }));
  if (profile.timezone && !values.includes(profile.timezone)) {
    options.push({ value: profile.timezone, label: profile.timezone });
  }
  return options;
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    Object.assign(profile, await loadProfile());
    profile.fontScale = normalizeFontScale(profile.fontScale);
    profile.language = normalizeLocale(profile.language);
    applyAppearanceSettings(profile);
    setLocale(profile.language);
    rememberDisplayName(currentName.value);
  } catch (error) {
    ElMessage.error(error?.message || t('profile.loadFailed'));
  } finally {
    loading.value = false;
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

function chooseLanguage(value) {
  profile.language = normalizeLocale(value);
  setLocale(profile.language);
}

async function submitProfile() {
  if (savingProfile.value) return;
  const displayName = profile.displayName.trim();
  const email = (profile.email || '').trim();
  if (displayName.length < 2 || displayName.length > 20) {
    ElMessage.warning(t('profile.nicknameInvalid'));
    return;
  }
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    ElMessage.warning(t('profile.emailInvalid'));
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
      language: profile.language,
    });
    Object.assign(profile, updated);
    applyAppearanceSettings(profile);
    setLocale(profile.language);
    rememberDisplayName(profileDisplayName(profile));
    ElMessage.success(t('profile.saved'));
  } catch (error) {
    ElMessage.error(error?.message || t('profile.saveFailed'));
  } finally {
    savingProfile.value = false;
  }
}

async function submitPassword() {
  if (savingPassword.value) return;
  if (password.next.length < 6) {
    ElMessage.warning(t('profile.passwordShort'));
    return;
  }
  if (password.next !== password.confirm) {
    ElMessage.warning(t('profile.passwordMismatch'));
    return;
  }
  savingPassword.value = true;
  try {
    await changePassword({ newPassword: password.next });
    password.next = '';
    password.confirm = '';
    ElMessage.success(t('profile.passwordUpdated'));
  } catch (error) {
    ElMessage.error(error?.message || t('profile.passwordFailed'));
  } finally {
    savingPassword.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="profile-settings-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('profile.eyebrow') }}</div>
        <h1>{{ t('profile.title') }}</h1>
        <p>{{ t('profile.subtitle') }}</p>
      </div>
    </div>

    <div class="settings-layout">
      <section class="panel settings-panel profile-panel">
        <div class="panel-heading">
          <div>
            <h2>{{ t('profile.profileInfo') }}</h2>
            <p>{{ t('profile.profileInfoHint') }}</p>
          </div>
          <span class="panel-icon"><el-icon><User /></el-icon></span>
        </div>

        <div class="identity-strip">
          <div class="profile-avatar large">{{ profileInitial(currentName) }}</div>
          <div>
            <strong>{{ currentName }}</strong>
            <span>{{ t('profile.joinedAt', { date: joinedAtLabel }) }}</span>
          </div>
          <el-tag class="account-tag" effect="plain">{{ t('profile.account') }}</el-tag>
        </div>

        <el-form class="settings-form" label-position="top" @submit.prevent="submitProfile">
          <div class="form-grid">
            <el-form-item :label="t('profile.nickname')">
              <el-input v-model="profile.displayName" maxlength="20" show-word-limit :placeholder="t('profile.nicknamePlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('profile.loginName')">
              <el-input :model-value="profile.loginName" disabled>
                <template #prefix><el-icon><Lock /></el-icon></template>
              </el-input>
              <span class="field-hint">{{ t('profile.loginNameHint') }}</span>
            </el-form-item>
            <el-form-item :label="t('profile.email')">
              <el-input v-model="profile.email" maxlength="255" :placeholder="t('profile.emailPlaceholder')">
                <template #prefix><el-icon><Message /></el-icon></template>
              </el-input>
            </el-form-item>
            <el-form-item :label="t('profile.timezone')">
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
            <h2>{{ t('profile.appearance') }}</h2>
            <p>{{ t('profile.appearanceHint') }}</p>
          </div>
          <span class="panel-icon warm"><el-icon><Sunny /></el-icon></span>
        </div>

        <div class="setting-block">
          <div class="block-label"><strong>{{ t('profile.language') }}</strong><span>{{ t('profile.languageHint') }}</span></div>
          <div class="language-options">
            <button
              v-for="item in LOCALE_OPTIONS"
              :key="item.value"
              type="button"
              :class="{ active: profile.language === item.value }"
              :aria-pressed="profile.language === item.value"
              @click="chooseLanguage(item.value)"
            >
              <b>{{ item.short }}</b>
              <span>{{ t(`languages.${item.value}`) }}</span>
              <el-icon v-if="profile.language === item.value"><Check /></el-icon>
            </button>
          </div>
        </div>

        <div class="setting-block">
          <div class="block-label"><strong>{{ t('profile.theme') }}</strong><span>{{ t('profile.themeHint') }}</span></div>
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
              <span>{{ t(`themes.${key}`) }}</span>
              <el-icon v-if="profile.theme === key" class="theme-check"><Check /></el-icon>
            </button>
          </div>
        </div>

        <div class="setting-block">
          <div class="block-label"><strong>{{ t('profile.fontScale') }}</strong><span>{{ t('profile.fontScaleHint') }}</span></div>
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
              <span>{{ t(`profile.${item.value}`) }}</span>
              <small>{{ item.scale }}</small>
            </button>
          </div>
        </div>

        <div class="motion-row">
          <div>
            <strong>{{ t('profile.reduceMotion') }}</strong>
            <span>{{ t('profile.reduceMotionHint') }}</span>
          </div>
          <el-switch v-model="profile.reduceMotion" @change="previewAppearance" />
        </div>
      </section>

      <section class="panel settings-panel security-panel">
        <div class="panel-heading">
          <div>
            <h2>{{ t('profile.security') }}</h2>
            <p>{{ t('profile.securityHint') }}</p>
          </div>
          <span class="panel-icon secure"><el-icon><Key /></el-icon></span>
        </div>
        <el-form class="settings-form password-form" label-position="top" @submit.prevent="submitPassword">
          <div class="form-grid">
            <el-form-item :label="t('profile.newPassword')">
              <el-input v-model="password.next" type="password" show-password maxlength="100" autocomplete="new-password" :placeholder="t('profile.newPasswordPlaceholder')" />
            </el-form-item>
            <el-form-item :label="t('profile.confirmPassword')">
              <el-input v-model="password.confirm" type="password" show-password maxlength="100" autocomplete="new-password" :placeholder="t('profile.confirmPasswordPlaceholder')" />
            </el-form-item>
          </div>
        </el-form>
        <div class="security-actions">
          <el-button class="soft-button" :loading="savingPassword" :disabled="savingPassword" @click="submitPassword">
            {{ t('profile.updatePassword') }}
          </el-button>
        </div>
      </section>
    </div>

    <div class="settings-actions">
      <span>{{ t('profile.syncHint') }}</span>
      <el-button class="primary-button" :loading="savingProfile" :disabled="savingProfile" @click="submitProfile">{{ t('profile.saveSettings') }}</el-button>
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
.language-options { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.language-options button { position: relative; display: grid; min-height: 56px; align-content: center; justify-items: center; gap: 3px; padding: 7px; border: 1px solid #e5eae6; border-radius: 6px; background: white; color: #68756e; font-family: inherit; }
.language-options button:hover { border-color: #cbd8d0; }
.language-options button.active { border-color: var(--green); background: var(--green-soft); color: var(--green-dark); }
.language-options b { font-size: 13px; }
.language-options span { font-size: 12px; font-weight: 600; }
.language-options .el-icon { position: absolute; top: 5px; right: 5px; color: var(--green); }
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
.password-form { margin-bottom: 14px; }
.security-actions { display: flex; justify-content: flex-end; }
.settings-actions { display: flex; align-items: center; justify-content: space-between; gap: 18px; margin-top: 18px; padding: 15px 2px 0; }
.settings-actions > span { color: #929c96; font-size: 12px; }
@media (max-width: 980px) {
  .settings-layout { grid-template-columns: 1fr; }
  .profile-panel { grid-row: auto; }
}
@media (max-width: 560px) {
  .settings-panel { padding: 18px 15px; }
  .form-grid { grid-template-columns: 1fr; }
  .settings-actions { align-items: stretch; flex-direction: column; }
  .settings-actions .primary-button { width: 100%; }
  .security-actions .el-button { width: 100%; }
}
</style>
