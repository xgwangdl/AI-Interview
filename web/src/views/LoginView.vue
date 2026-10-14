<script setup>
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { tryLogin, tryRegister } from '../services/api';

const route = useRoute();
const router = useRouter();
const mode = ref('login');
const form = reactive({ name: '', code: '', confirm: '', email: '' });
const busy = ref(false);

const isRegister = computed(() => mode.value === 'register');

function switchMode(next) {
  if (mode.value === next) return;
  mode.value = next;
  // 切换模式时清掉口令，避免把登录密码带进注册框（或反过来）。
  form.code = '';
  form.confirm = '';
}

async function submit() {
  const name = form.name.trim();
  if (!name || !form.code) {
    ElMessage.warning(isRegister.value ? '请填写用户名和密码。' : '请输入用户名和密码。');
    return;
  }
  if (isRegister.value) {
    if (form.code.length < 6) {
      ElMessage.warning('密码至少 6 位。');
      return;
    }
    if (form.code !== form.confirm) {
      ElMessage.warning('两次输入的密码不一致。');
      return;
    }
  }
  busy.value = true;
  try {
    const data = isRegister.value
      ? await tryRegister(name, form.code, form.email.trim())
      : await tryLogin(name, form.code);
    localStorage.setItem('token', data.token);
    localStorage.setItem('userId', String(data.userId));
    localStorage.setItem('userName', data.userName || name);
    await router.replace(String(route.query.redirect || '/'));
  } catch (error) {
    ElMessage.error(error.message || '无法连接服务，请确认后端正在运行。');
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand"><span class="login-mark"><span></span><span></span><span></span></span><div><strong>面试练习室</strong><small>INTERVIEW STUDIO</small></div></div>
    <div class="login-layout">
      <div class="login-intro"><div class="login-overline">PREPARE WITH PURPOSE</div><h1>把每一次回答<br />练习得更从容</h1><p>为目标岗位准备，完成视频模拟面试，在真实的表达中发现可以改进的细节。</p><div class="login-benefits"><span><el-icon><VideoCamera /></el-icon>逐题视频练习</span><span><el-icon><ChatLineRound /></el-icon>结构化复盘</span><span><el-icon><Lock /></el-icon>可随时删除数据</span></div><div class="login-visual"><div class="login-visual-window"><div class="visual-toolbar"><i></i><i></i><i></i><span>练习现场 · 预览</span></div><div class="visual-scene"><div class="visual-person"><i class="visual-hair"></i><i class="visual-face"></i><i class="visual-jacket"></i><i class="visual-shirt"></i></div><div class="visual-question"><small>QUESTION 01</small><span>请介绍一个<br />你引以为傲的项目</span><i>录制准备中</i></div><div class="visual-wave"><i v-for="n in 18" :key="n" :style="{ height: `${8 + (n * 19 % 28)}px` }"></i></div></div></div><div class="visual-note"><span class="visual-note-icon"><el-icon><CircleCheck /></el-icon></span><span>每一道问题，都可以慢慢准备<small>练习 · 录制 · 回看</small></span></div></div><div class="login-footer">© INTERVIEW STUDIO <span>你的练习由你掌控</span></div></div>
      <section class="login-form-side">
        <div class="login-form-header"><div class="eyebrow">{{ isRegister ? 'CREATE ACCOUNT' : 'WELCOME BACK' }}</div><h2>{{ isRegister ? '创建你的练习账号' : '登录你的练习空间' }}</h2><p>{{ isRegister ? '设置用户名与密码，立即开始第一次模拟面试。' : '使用你的用户名与密码继续。' }}</p></div>
        <div class="auth-switch" role="tablist">
          <button type="button" role="tab" :aria-selected="!isRegister" :class="{ active: !isRegister }" @click="switchMode('login')">登录</button>
          <button type="button" role="tab" :aria-selected="isRegister" :class="{ active: isRegister }" @click="switchMode('register')">注册</button>
        </div>
        <!-- 固定 label 宽度：否则「用户名」和「密码 / 邀请码」宽度不同，输入框左边缘参差不齐 -->
        <el-form class="login-form" label-position="left" label-width="88px" @submit.prevent="submit">
          <el-form-item label="用户名"><el-input v-model="form.name" size="large" maxlength="80" autocomplete="username" placeholder="2-80 位，支持中文" @keyup.enter="submit"><template #prefix><el-icon><User /></el-icon></template></el-input></el-form-item>
          <el-form-item :label="isRegister ? '设置密码' : '密码 / 邀请码'"><el-input v-model="form.code" size="large" type="password" maxlength="100" show-password :autocomplete="isRegister ? 'new-password' : 'current-password'" :placeholder="isRegister ? '至少 6 位' : '输入密码'" @keyup.enter="submit"><template #prefix><el-icon><Key /></el-icon></template></el-input></el-form-item>
          <template v-if="isRegister">
            <el-form-item label="确认密码"><el-input v-model="form.confirm" size="large" type="password" maxlength="100" show-password autocomplete="new-password" placeholder="再输入一次" @keyup.enter="submit"><template #prefix><el-icon><Key /></el-icon></template></el-input></el-form-item>
            <el-form-item label="邮箱（可选）"><el-input v-model="form.email" size="large" maxlength="255" placeholder="用于后续通知，可留空" @keyup.enter="submit"><template #prefix><el-icon><Message /></el-icon></template></el-input></el-form-item>
          </template>
          <el-button class="primary-button login-submit" :loading="busy" @click="submit">{{ isRegister ? '创建账号并进入' : '安全登录' }} <el-icon><ArrowRight /></el-icon></el-button>
        </el-form>
        <div class="login-help"><el-icon><InfoFilled /></el-icon><span>{{ isRegister ? '密码以 BCrypt 哈希保存，管理员也看不到原文。' : '忘记密码请联系管理员重置；请勿在公共设备上保存登录信息。' }}</span></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; padding: 25px clamp(20px, 6vw, 84px) 18px; background: #f4f6f2; }.login-brand { display: flex; align-items: center; gap: 10px; color: #27332e; }.login-brand > div { display: grid; gap: 4px; }.login-brand strong { font-size: 13px; }.login-brand small { color: #89958e; font-size: 8px; }.login-mark { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; gap: 3px; border-radius: 8px; background: var(--green); }.login-mark span { width: 3px; border-radius: 3px; background: white; }.login-mark span:nth-child(1) { height: 10px; }.login-mark span:nth-child(2) { height: 18px; }.login-mark span:nth-child(3) { height: 13px; }
.login-layout { display: grid; width: min(1040px,100%); min-height: min(640px,calc(100vh - 100px)); grid-template-columns: 1.1fr .9fr; margin: max(24px,calc((100vh - 750px)/2)) auto 0; overflow: hidden; border: 1px solid #e4e9e4; border-radius: 8px; background: white; box-shadow: 0 24px 64px rgba(30,45,37,.08); }.login-intro { position: relative; display: flex; min-height: 580px; flex-direction: column; overflow: hidden; padding: 45px clamp(29px,5vw,64px) 23px; background: #eaf2ed; }.login-overline { color: #458061; font-size: 9px; font-weight: 700; }.login-intro h1 { margin: 15px 0 10px; color: #2b4034; font-size: clamp(25px,3vw,35px); font-weight: 650; line-height: 1.48; }.login-intro > p { max-width: 380px; margin: 0; color: #71857a; font-size: 11px; line-height: 1.85; }.login-benefits { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 19px; color: #52735e; font-size: 9px; }.login-benefits span { display: flex; align-items: center; gap: 5px; }.login-benefits .el-icon { font-size: 13px; }
.login-visual { position: relative; flex: 1; min-height: 220px; margin-top: 25px; }.login-visual-window { position: absolute; top: 0; left: 4%; width: 83%; height: 83%; overflow: hidden; border: 1px solid rgba(255,255,255,.82); border-radius: 7px; background: rgba(255,255,255,.68); box-shadow: 0 14px 26px rgba(62,93,73,.1); transform: rotate(-2deg); }.visual-toolbar { display: flex; height: 26px; align-items: center; gap: 4px; padding: 0 9px; border-bottom: 1px solid #e8efea; }.visual-toolbar > i { width: 4px; height: 4px; border-radius: 50%; background: #d98d77; }.visual-toolbar > i:nth-child(2) { background: #debd70; }.visual-toolbar > i:nth-child(3) { background: #7fa88b; }.visual-toolbar > span { margin-left: 6px; color: #8e9b92; font-size: 7px; }.visual-scene { position: relative; height: calc(100% - 26px); background: linear-gradient(105deg,#e4eee8,#eff4f0 55%,#dcebe2); }.visual-person { position: absolute; bottom: 0; left: 17%; width: 98px; height: 145px; }.visual-hair { position: absolute; top: 9px; left: 23px; z-index: 3; width: 52px; height: 59px; border-radius: 50% 50% 36% 36%; background: #493d36; }.visual-face { position: absolute; top: 20px; left: 30px; z-index: 4; width: 40px; height: 50px; border-radius: 47%; background: #dfac8d; }.visual-jacket { position: absolute; bottom: 0; left: 5px; z-index: 2; width: 90px; height: 83px; border-radius: 40px 40px 0 0; background: #4b7864; }.visual-shirt { position: absolute; bottom: 43px; left: 42px; z-index: 3; width: 20px; height: 40px; clip-path: polygon(0 0,100% 0,76% 100%,48% 76%,22% 100%); background: #f4f1e9; }.visual-question { position: absolute; top: 23%; right: 8%; display: grid; width: 47%; gap: 7px; }.visual-question small { color: #59866a; font-size: 7px; }.visual-question span { color: #435b4c; font-size: 12px; font-weight: 600; line-height: 1.6; }.visual-question i { width: max-content; padding: 5px 7px; border-radius: 4px; background: rgba(255,255,255,.7); color: #778d7d; font-size: 7px; font-style: normal; }.visual-wave { position: absolute; right: 8%; bottom: 9px; display: flex; height: 33px; align-items: center; gap: 3px; }.visual-wave i { width: 3px; border-radius: 3px; background: #77a584; opacity: .7; }
.visual-note { position: absolute; right: 1%; bottom: 4px; display: flex; align-items: center; gap: 8px; padding: 10px 12px; border: 1px solid #e3ebe4; border-radius: 6px; background: rgba(255,255,255,.96); box-shadow: 0 8px 20px rgba(40,65,48,.1); color: #526258; font-size: 9px; }.visual-note-icon { display: grid; width: 24px; height: 24px; place-items: center; border-radius: 50%; background: #e9f4eb; color: #57906a; }.visual-note small { display: block; margin-top: 4px; color: #97a19b; font-size: 7px; }.login-footer { display: flex; justify-content: space-between; padding-top: 18px; color: #8a998f; font-size: 7px; }.login-form-side { display: flex; flex-direction: column; justify-content: center; padding: 56px clamp(28px,5vw,58px); }.login-form-header .eyebrow { font-size: 9px; }.login-form-header h2 { margin: 11px 0 7px; color: #303c35; font-size: 20px; font-weight: 650; }.login-form-header p { margin: 0; color: #849089; font-size: 10px; }.login-form { margin-top: 18px; }.login-form :deep(.el-form-item) { margin-bottom: 17px; }.login-form :deep(.el-form-item__label) { margin-bottom: 7px; color: #68756d; font-size: 10px; }.login-form :deep(.el-input__wrapper) { min-height: 43px; }.login-form :deep(.el-input__inner) { font-size: 12px; }.login-form :deep(.el-input__prefix-inner) { color: #92a097; }.login-submit { width: 100%; margin-top: 9px; }.login-help { display: flex; gap: 6px; margin-top: auto; padding-top: 22px; color: #939e97; font-size: 8px; line-height: 1.7; }.login-help .el-icon { flex: none; margin-top: 1px; color: #7995a2; }
.auth-switch { display: inline-flex; gap: 4px; margin-top: 20px; padding: 3px; border-radius: 7px; background: #f1f4f1; }.auth-switch button { min-width: 76px; height: 30px; border: 0; border-radius: 5px; background: transparent; color: #64716a; font-family: inherit; font-size: 12px; font-weight: 600; cursor: pointer; }.auth-switch button.active { background: #fff; color: var(--green-dark); box-shadow: 0 1px 3px rgba(31,95,77,.14); }.auth-switch button:focus-visible { outline: 2px solid var(--green); outline-offset: 2px; }
@media(max-width: 760px) { .login-page { padding: 17px 13px; }.login-layout { min-height: auto; grid-template-columns: 1fr; margin-top: 18px; }.login-intro { min-height: 320px; padding: 27px 23px 15px; }.login-intro h1 { margin: 10px 0 5px; font-size: 25px; }.login-intro > p { max-width: 430px; font-size: 9px; }.login-benefits { margin-top: 11px; }.login-visual { min-height: 150px; margin-top: 10px; }.login-visual-window { left: 4%; width: 72%; height: 87%; }.visual-person { transform: scale(.78); transform-origin: bottom left; }.visual-question span { font-size: 10px; }.visual-note { right: 0; }.login-footer { padding-top: 12px; }.login-form-side { padding: 33px 24px 23px; }.login-form { margin-top: 19px; }.login-help { margin-top: 19px; padding-top: 15px; } }
</style>
<style scoped>
/* 标签固定成一个左对齐的窄列，字号与输入框一致：
   否则「用户名」「密码 / 邀请码」「邮箱（可选）」宽度不同，下面几个输入框的左边缘会参差不齐。 */
.login-form :deep(.el-form-item__label) { justify-content: flex-start; padding-right: 10px; color: #68756d; font-size: 12px; line-height: 1.4; }
@media(max-width: 760px) { .login-form :deep(.el-form-item__label) { font-size: 11px; } }
</style>
