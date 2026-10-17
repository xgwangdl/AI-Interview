<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  loadNotificationPreferences, saveNotificationPreferences, sendTestNotification,
} from '../services/progress';

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
    ElMessage.error(error?.message || '无法读取提醒设置');
  } finally {
    loading.value = false;
  }
}

async function save() {
  saving.value = true;
  try {
    Object.assign(form, await saveNotificationPreferences({ ...form }));
    ElMessage.success('提醒设置已保存');
  } catch (error) {
    ElMessage.error(error?.message || '保存失败');
  } finally {
    saving.value = false;
  }
}

async function test() {
  testing.value = true;
  try {
    const result = await sendTestNotification();
    if (result?.sent) ElMessage.success('测试邮件已发送，请检查收件箱');
    else ElMessage.warning('账号还没有邮箱，请先在个人资料里补充邮箱');
  } catch (error) {
    ElMessage.error(error?.message || '测试邮件发送失败');
  } finally {
    testing.value = false;
  }
}
</script>

<template>
  <section v-loading="loading" class="notification-page">
    <div class="page-heading">
      <div>
        <div class="eyebrow">NOTIFICATIONS</div>
        <h1>提醒设置</h1>
        <p>用邮件提醒你每天训练、每周复盘，以及面试前的倒计时。所有提醒都可以关闭。</p>
      </div>
    </div>

    <section class="panel notification-card">
      <div class="setting-row master">
        <div><strong>邮件提醒</strong><small>关闭后，下面的每日/周报/倒计时提醒都不会发送</small></div>
        <el-switch v-model="form.emailEnabled" />
      </div>
      <div class="setting-row" :class="{ disabled: !form.emailEnabled }">
        <div><strong>每天提醒我练题</strong><small>当天完成 2 题后不再打扰；提醒时间按下面的设置</small></div>
        <el-switch v-model="form.dailyReminder" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-row" :class="{ disabled: !form.emailEnabled }">
        <div><strong>每周一发送周报</strong><small>计划完成率、练习场次、维度变化与下周建议</small></div>
        <el-switch v-model="form.weeklyReport" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-row" :class="{ disabled: !form.emailEnabled }">
        <div><strong>面试倒计时</strong><small>面试前 7 / 3 / 1 天提醒，并附上当天计划</small></div>
        <el-switch v-model="form.interviewCountdown" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-row">
        <div><strong>每天提醒时间</strong><small>按 Asia/Shanghai 时区计算</small></div>
        <el-time-picker v-model="form.reminderTime" format="HH:mm" value-format="HH:mm" :disabled="!form.emailEnabled" />
      </div>
      <div class="setting-actions">
        <el-button class="soft-button" :loading="testing" :disabled="!form.emailEnabled" @click="test">发送测试邮件</el-button>
        <el-button class="primary-button" :loading="saving" @click="save">保存设置</el-button>
      </div>
      <p class="setting-note">测试邮件需要账号已填写邮箱；邮箱在注册时填写，或由管理员在账号资料中补充。</p>
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
