<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  loadNotificationPreferences, saveNotificationPreferences, sendTestNotification,
} from '../services/progress';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const testing = ref(false);
const form = reactive({
  emailEnabled: true,
  dailyReminder: true,
  weeklyReport: true,
  interviewCountdown: true,
  reminderTime: '20:00',
  timezone: 'Asia/Shanghai',
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    Object.assign(form, await loadNotificationPreferences());
  } catch (error) {
    ElMessage.error(error?.message || t('notifications.loadFailed'));
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  try {
    Object.assign(form, await saveNotificationPreferences({ ...form }));
    ElMessage.success(t('notifications.saved'));
  } catch (error) {
    ElMessage.error(error?.message || t('notifications.saveFailed'));
  } finally {
    saving.value = false;
  }
}

async function test() {
  testing.value = true;
  try {
    const result = await sendTestNotification();
    if (result?.sent) ElMessage.success(t('notifications.testSent'));
    else ElMessage.warning(t('notifications.noEmail'));
  } catch (error) {
    ElMessage.error(error?.message || t('notifications.testFailed'));
  } finally {
    testing.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="notification-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">{{ t('notifications.eyebrow') }}</div>
        <h1>{{ t('notifications.title') }}</h1>
        <p>{{ t('notifications.subtitle') }}</p>
      </div>
    </div>

    <section class="panel notification-card">
      <div class="setting-row master">
        <div><strong>{{ t('notifications.email') }}</strong><small>{{ t('notifications.emailHint') }}</small></div>
        <el-switch v-model="form.emailEnabled" />
      </div>
      <div class="setting-row" :class="{ disabled: !form.emailEnabled }">
        <div><strong>{{ t('notifications.daily') }}</strong><small>{{ t('notifications.dailyHint') }}</small></div>
        <el-switch v-model="form.dailyReminder" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-row" :class="{ disabled: !form.emailEnabled }">
        <div><strong>{{ t('notifications.weekly') }}</strong><small>{{ t('notifications.weeklyHint') }}</small></div>
        <el-switch v-model="form.weeklyReport" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-row" :class="{ disabled: !form.emailEnabled }">
        <div><strong>{{ t('notifications.countdown') }}</strong><small>{{ t('notifications.countdownHint') }}</small></div>
        <el-switch v-model="form.interviewCountdown" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-row">
        <div><strong>{{ t('notifications.time') }}</strong><small>{{ t('notifications.timeHint') }}</small></div>
        <el-time-picker v-model="form.reminderTime" format="HH:mm" value-format="HH:mm" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-actions">
        <el-button class="soft-button" :loading="testing" :disabled="!form.emailEnabled" @click="test">{{ t('notifications.sendTest') }}</el-button>
        <el-button class="primary-button" :loading="saving" @click="save">{{ t('notifications.save') }}</el-button>
      </div>
      <p class="setting-note">{{ t('notifications.note') }}</p>
    </section>
  </section>
</template>

<style scoped>
.notification-page { max-width: 760px; }
.notification-card { display: grid; gap: 0; padding: 6px 20px 18px; box-shadow: none; }
.setting-row { display: flex; align-items: center; justify-content: space-between; gap: 18px; padding: 16px 0; border-bottom: 1px solid #eef1ee; }
.setting-row.master { padding-top: 12px; }
.setting-row.disabled { opacity: .55; }
.setting-row > div { display: grid; gap: 5px; }
.setting-row strong { color: #46524c; font-size: 14px; }
.setting-row small { color: #98a29d; font-size: 12px; line-height: 1.6; }
.setting-actions { display: flex; justify-content: flex-end; gap: 9px; padding-top: 16px; }
.setting-note { margin: 12px 0 0; color: #a9722f; font-size: 12px; line-height: 1.7; }
@media (max-width: 560px) { .setting-row { align-items: flex-start; }.setting-actions { flex-direction: column; }.setting-actions .el-button { width: 100%; } }
</style>
