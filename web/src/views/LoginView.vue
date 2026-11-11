<script setup>
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { ElMessage } from 'element-plus';
import { tryLogin, tryRegister } from '../services/api';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
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
    ElMessage.warning(t('login.missingCredentials'));
    return;
  }
  if (isRegister.value) {
    if (form.code.length < 6) {
      ElMessage.warning(t('login.shortPassword'));
      return;
    }
    if (form.code !== form.confirm) {
      ElMessage.warning(t('login.mismatchPassword'));
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
    ElMessage.error(error.message || t('login.connectionError'));
  } finally {
    busy.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand"><span class="login-mark"><span></span><span></span><span></span></span><div><strong>{{ t('app.name') }}</strong><small>{{ t('app.subtitle') }}</small></div></div>
    <div class="login-layout">
      <div class="login-intro">
        <div class="intro-rule" aria-hidden="true"></div>
        <h1 v-html="t('login.introTitle')"></h1>
        <p>{{ t('login.introText') }}</p>
        <div class="login-benefits">
          <span><el-icon><VideoCamera /></el-icon>{{ t('login.benefitVideo') }}</span>
          <span><el-icon><ChatLineRound /></el-icon>{{ t('login.benefitReview') }}</span>
          <span><el-icon><Lock /></el-icon>{{ t('login.benefitDelete') }}</span>
        </div>
        <div class="login-preview" aria-hidden="true">
          <div class="preview-head"><span>{{ t('login.previewTitle') }}</span><i>{{ t('login.previewRecording') }}</i></div>
          <div class="preview-body">
            <div class="preview-question">
              <small>QUESTION 01</small>
              <strong v-html="t('login.previewQuestion')"></strong>
              <div class="preview-wave"><i v-for="n in 18" :key="n" :style="{ height: `${7 + (n * 19 % 24)}px` }"></i></div>
            </div>
            <img class="preview-portrait" src="/assets/avatar/portrait.png" alt="" />
          </div>
          <div class="preview-note"><el-icon><CircleCheck /></el-icon><span>{{ t('login.previewNote') }}<small>{{ t('login.previewNoteHint') }}</small></span></div>
        </div>
        <div class="login-footer">© INTERVIEW STUDIO <span>{{ t('login.privacy') }}</span></div>
      </div>
      <section class="login-form-side">
        <div class="login-form-header"><h2>{{ isRegister ? t('login.createAccount') : t('login.loginTitle') }}</h2><p>{{ isRegister ? t('login.createHint') : t('login.loginHint') }}</p></div>
        <div class="auth-switch" role="tablist">
          <button type="button" role="tab" :aria-selected="!isRegister" :class="{ active: !isRegister }" @click="switchMode('login')">{{ t('login.loginTab') }}</button>
          <button type="button" role="tab" :aria-selected="isRegister" :class="{ active: isRegister }" @click="switchMode('register')">{{ t('login.registerTab') }}</button>
        </div>
        <!-- 固定 label 宽度：否则「用户名」和「密码 / 邀请码」宽度不同，输入框左边缘参差不齐 -->
        <el-form class="login-form" label-position="left" label-width="88px" @submit.prevent="submit">
          <el-form-item :label="t('login.username')"><el-input v-model="form.name" size="large" maxlength="80" autocomplete="username" :placeholder="t('login.usernamePlaceholder')" @keyup.enter="submit"><template #prefix><el-icon><User /></el-icon></template></el-input></el-form-item>
          <el-form-item :label="isRegister ? t('login.setPassword') : t('login.password')"><el-input v-model="form.code" size="large" type="password" maxlength="100" show-password :autocomplete="isRegister ? 'new-password' : 'current-password'" :placeholder="isRegister ? t('login.passwordMinPlaceholder') : t('login.passwordPlaceholder')" @keyup.enter="submit"><template #prefix><el-icon><Key /></el-icon></template></el-input></el-form-item>
          <template v-if="isRegister">
            <el-form-item :label="t('login.confirmPassword')"><el-input v-model="form.confirm" size="large" type="password" maxlength="100" show-password autocomplete="new-password" :placeholder="t('login.confirmPlaceholder')" @keyup.enter="submit"><template #prefix><el-icon><Key /></el-icon></template></el-input></el-form-item>
            <el-form-item :label="t('login.email')"><el-input v-model="form.email" size="large" maxlength="255" :placeholder="t('login.emailPlaceholder')" @keyup.enter="submit"><template #prefix><el-icon><Message /></el-icon></template></el-input></el-form-item>
          </template>
          <el-button class="primary-button login-submit" :loading="busy" @click="submit">{{ isRegister ? t('login.createSubmit') : t('login.loginSubmit') }} <el-icon><ArrowRight /></el-icon></el-button>
        </el-form>
        <div class="login-help"><el-icon><InfoFilled /></el-icon><span>{{ isRegister ? t('login.createSecurity') : t('login.loginSecurity') }}</span></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; padding: 25px clamp(20px, 6vw, 84px) 18px; background: #f4f6f2; }.login-brand { display: flex; align-items: center; gap: 10px; color: #27332e; }.login-brand > div { display: grid; gap: 4px; }.login-brand strong { font-size: 15px; }.login-brand small { color: #89958e; font-size: 12px; }.login-mark { display: flex; width: 32px; height: 32px; align-items: center; justify-content: center; gap: 3px; border-radius: 8px; background: var(--green); }.login-mark span { width: 3px; border-radius: 3px; background: white; }.login-mark span:nth-child(1) { height: 10px; }.login-mark span:nth-child(2) { height: 18px; }.login-mark span:nth-child(3) { height: 13px; }
.login-layout { display: grid; width: min(1040px,100%); min-height: min(640px,calc(100vh - 100px)); grid-template-columns: 1.1fr .9fr; margin: max(24px,calc((100vh - 750px)/2)) auto 0; overflow: hidden; border: 1px solid #e4e9e4; border-radius: 8px; background: white; box-shadow: 0 24px 64px rgba(30,45,37,.08); }.login-intro { position: relative; display: flex; min-height: 580px; flex-direction: column; overflow: hidden; padding: 45px clamp(29px,5vw,64px) 23px; background: #eaf2ed; }.login-overline { color: #458061; font-size: 13px; font-weight: 700; }.login-intro h1 { margin: 15px 0 10px; color: #2b4034; font-size: clamp(25px,3vw,35px); font-weight: 650; line-height: 1.48; }.login-intro > p { max-width: 380px; margin: 0; color: #71857a; font-size: 14px; line-height: 1.85; }.login-benefits { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 19px; color: #52735e; font-size: 13px; }.login-benefits span { display: flex; align-items: center; gap: 5px; }.login-benefits .el-icon { font-size: 15px; }
.login-visual { position: relative; flex: 1; min-height: 220px; margin-top: 25px; }.login-visual-window { position: absolute; top: 0; left: 4%; width: 83%; height: 83%; overflow: hidden; border: 1px solid rgba(255,255,255,.82); border-radius: 7px; background: rgba(255,255,255,.68); box-shadow: 0 14px 26px rgba(62,93,73,.1); transform: rotate(-2deg); }.visual-toolbar { display: flex; height: 26px; align-items: center; gap: 4px; padding: 0 9px; border-bottom: 1px solid #e8efea; }.visual-toolbar > i { width: 4px; height: 4px; border-radius: 50%; background: #d98d77; }.visual-toolbar > i:nth-child(2) { background: #debd70; }.visual-toolbar > i:nth-child(3) { background: #7fa88b; }.visual-toolbar > span { margin-left: 6px; color: #8e9b92; font-size: 11px; }.visual-scene { position: relative; height: calc(100% - 26px); background: linear-gradient(105deg,#e4eee8,#eff4f0 55%,#dcebe2); }.visual-person { position: absolute; bottom: 0; left: 17%; width: 98px; height: 145px; }.visual-hair { position: absolute; top: 9px; left: 23px; z-index: 3; width: 52px; height: 59px; border-radius: 50% 50% 36% 36%; background: #493d36; }.visual-face { position: absolute; top: 20px; left: 30px; z-index: 4; width: 40px; height: 50px; border-radius: 47%; background: #dfac8d; }.visual-jacket { position: absolute; bottom: 0; left: 5px; z-index: 2; width: 90px; height: 83px; border-radius: 40px 40px 0 0; background: #4b7864; }.visual-shirt { position: absolute; bottom: 43px; left: 42px; z-index: 3; width: 20px; height: 40px; clip-path: polygon(0 0,100% 0,76% 100%,48% 76%,22% 100%); background: #f4f1e9; }.visual-question { position: absolute; top: 23%; right: 8%; display: grid; width: 47%; gap: 7px; }.visual-question small { color: #59866a; font-size: 11px; }.visual-question span { color: #435b4c; font-size: 14px; font-weight: 600; line-height: 1.6; }.visual-question i { width: max-content; padding: 5px 7px; border-radius: 4px; background: rgba(255,255,255,.7); color: #778d7d; font-size: 11px; font-style: normal; }.visual-wave { position: absolute; right: 8%; bottom: 9px; display: flex; height: 33px; align-items: center; gap: 3px; }.visual-wave i { width: 3px; border-radius: 3px; background: #77a584; opacity: .7; }
.visual-note { position: absolute; right: 1%; bottom: 4px; display: flex; align-items: center; gap: 8px; padding: 10px 12px; border: 1px solid #e3ebe4; border-radius: 6px; background: rgba(255,255,255,.96); box-shadow: 0 8px 20px rgba(40,65,48,.1); color: #526258; font-size: 13px; }.visual-note-icon { display: grid; width: 24px; height: 24px; place-items: center; border-radius: 50%; background: #e9f4eb; color: #57906a; }.visual-note small { display: block; margin-top: 4px; color: #97a19b; font-size: 11px; }.login-footer { display: flex; justify-content: space-between; padding-top: 18px; color: #8a998f; font-size: 11px; }.login-form-side { display: flex; flex-direction: column; justify-content: center; padding: 56px clamp(28px,5vw,58px); }.login-form-header .eyebrow { font-size: 13px; }.login-form-header h2 { margin: 11px 0 7px; color: #303c35; font-size: 21px; font-weight: 650; }.login-form-header p { margin: 0; color: #849089; font-size: 13px; }.login-form { margin-top: 18px; }.login-form :deep(.el-form-item) { margin-bottom: 17px; }.login-form :deep(.el-form-item__label) { margin-bottom: 7px; color: #68756d; font-size: 13px; }.login-form :deep(.el-input__wrapper) { min-height: 43px; }.login-form :deep(.el-input__inner) { font-size: 14px; }.login-form :deep(.el-input__prefix-inner) { color: #92a097; }.login-submit { width: 100%; margin-top: 9px; }.login-help { display: flex; gap: 6px; margin-top: auto; padding-top: 22px; color: #939e97; font-size: 12px; line-height: 1.7; }.login-help .el-icon { flex: none; margin-top: 1px; color: #7995a2; }
.auth-switch { display: inline-flex; gap: 4px; margin-top: 20px; padding: 3px; border-radius: 7px; background: #f1f4f1; }.auth-switch button { min-width: 76px; height: 30px; border: 0; border-radius: 5px; background: transparent; color: #64716a; font-family: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }.auth-switch button.active { background: #fff; color: var(--green-dark); box-shadow: 0 1px 3px rgba(31,95,77,.14); }.auth-switch button:focus-visible { outline: 2px solid var(--green); outline-offset: 2px; }
@media(max-width: 760px) { .login-page { padding: 17px 13px; }.login-layout { min-height: auto; grid-template-columns: 1fr; margin-top: 18px; }.login-intro { min-height: 320px; padding: 27px 23px 15px; }.login-intro h1 { margin: 10px 0 5px; font-size: 25px; }.login-intro > p { max-width: 430px; font-size: 13px; }.login-benefits { margin-top: 11px; }.login-visual { min-height: 150px; margin-top: 10px; }.login-visual-window { left: 4%; width: 72%; height: 87%; }.visual-person { transform: scale(.78); transform-origin: bottom left; }.visual-question span { font-size: 13px; }.visual-note { right: 0; }.login-footer { padding-top: 12px; }.login-form-side { padding: 33px 24px 23px; }.login-form { margin-top: 19px; }.login-help { margin-top: 19px; padding-top: 15px; } }
</style>
<style scoped>
/* 标签固定成一个左对齐的窄列，字号与输入框一致：
   否则「用户名」「密码 / 邀请码」「邮箱（可选）」宽度不同，下面几个输入框的左边缘会参差不齐。 */
.login-form :deep(.el-form-item__label) { justify-content: flex-start; padding-right: 10px; color: #68756d; font-size: 14px; line-height: 1.4; }
@media(max-width: 760px) { .login-form :deep(.el-form-item__label) { font-size: 14px; } }
</style>

<style scoped>
/* 登录页改为“面试练习台”而非左右宣传卡：左侧保留真实教练与问题，右侧只承担进入产品。 */
.login-page { min-height: 100svh; padding: 22px clamp(24px, 5vw, 72px) 28px; background: var(--canvas); color: var(--ink); }
.login-brand { width: min(1280px, 100%); margin: 0 auto; color: var(--ink); }
.login-brand strong { font-size: 17px; }
.login-brand small { color: var(--muted); font-size: 11px; letter-spacing: .06em; }
.login-mark { width: 38px; height: 38px; border-radius: 50%; background: var(--green); }
.login-layout { display: grid; width: min(1280px, 100%); min-height: calc(100svh - 98px); grid-template-columns: minmax(0, 1.45fr) minmax(370px, .75fr); align-items: stretch; gap: clamp(46px, 7vw, 100px); margin: 34px auto 0; border: 0; border-radius: 0; background: transparent; box-shadow: none; overflow: visible; }
.login-intro { display: flex; min-width: 0; flex-direction: column; padding: 34px 0 10px; background: transparent; color: var(--ink); }
.intro-rule { width: 58px; height: 2px; margin-bottom: 29px; background: var(--coral); }
.login-intro h1 { max-width: 12ch; margin: 0; color: var(--ink); font-size: clamp(38px, 4.4vw, 62px); font-weight: 700; line-height: 1.2; }
.login-intro h1 :deep(strong) { color: var(--green-dark); font-weight: inherit; }
.login-intro > p { max-width: 62ch; margin: 18px 0 0; color: var(--muted); font-size: 16px; line-height: 1.8; }
.login-benefits { display: flex; flex-wrap: wrap; gap: 12px 26px; margin-top: 28px; padding: 17px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); color: #4f5c56; font-size: 13px; }
.login-benefits span { display: inline-flex; align-items: center; gap: 7px; }
.login-benefits .el-icon { color: var(--green); font-size: 16px; }
.login-preview { width: min(720px, 100%); margin-top: 30px; overflow: hidden; border: 1px solid #cfd9d1; border-radius: 16px; background: #e5ece6; }
.preview-head { display: flex; min-height: 42px; align-items: center; justify-content: space-between; gap: 12px; padding: 0 16px; border-bottom: 1px solid #cbd7cf; color: #52605a; font-size: 12px; }
.preview-head i { display: inline-flex; align-items: center; gap: 6px; color: var(--green-dark); font-style: normal; }
.preview-head i::before { width: 6px; height: 6px; border-radius: 50%; background: var(--coral); content: ''; }
.preview-body { display: grid; min-height: 178px; grid-template-columns: minmax(0, 1fr) 190px; align-items: end; gap: 16px; padding: 24px 20px 0 24px; }
.preview-question { display: grid; align-self: center; gap: 10px; padding-bottom: 16px; }
.preview-question small { color: #6b7c73; font-size: 10px; font-weight: 700; letter-spacing: .12em; }
.preview-question strong { max-width: 25ch; color: #273a31; font-size: 21px; font-weight: 650; line-height: 1.5; }
.preview-wave { display: flex; height: 31px; align-items: center; gap: 3px; margin-top: 4px; }
.preview-wave i { width: 3px; border-radius: 99px; background: var(--green); opacity: .46; }
.preview-portrait { align-self: end; width: 190px; height: 165px; object-fit: contain; object-position: center bottom; mix-blend-mode: multiply; filter: saturate(.82) contrast(1.04); }
.preview-note { display: flex; align-items: center; gap: 9px; margin: 0 18px 16px; padding: 11px 12px; border: 1px solid #ced9d1; border-radius: 10px; background: rgba(251,250,246,.8); color: #43534b; font-size: 12px; }
.preview-note > .el-icon { flex: none; color: var(--green); font-size: 17px; }
.preview-note small { display: block; margin-top: 3px; color: #7b877f; font-size: 11px; }
.login-footer { margin-top: auto; padding-top: 19px; color: #818b85; font-size: 11px; }
.login-form-side { align-self: center; padding: clamp(34px, 4.5vw, 54px) clamp(28px, 4vw, 46px); border: 1px solid var(--line); border-radius: 16px; background: var(--paper); box-shadow: none; }
.login-form-header h2 { margin: 0 0 8px; color: var(--ink); font-size: clamp(24px, 2.2vw, 30px); font-weight: 680; line-height: 1.35; }
.login-form-header p { margin: 0; color: var(--muted); font-size: 14px; line-height: 1.65; }
.auth-switch { display: flex; gap: 24px; margin-top: 26px; padding: 0; border: 0; border-bottom: 1px solid var(--line); border-radius: 0; background: transparent; }
.auth-switch button { position: relative; min-width: 0; height: 40px; padding: 0 2px; border: 0; border-radius: 0; background: transparent; color: #7a837e; font-size: 14px; font-weight: 600; box-shadow: none; }
.auth-switch button.active { background: transparent; color: var(--green-dark); box-shadow: none; }
.auth-switch button.active::after { position: absolute; right: 0; bottom: -1px; left: 0; height: 2px; background: var(--green); content: ''; }
.login-form { margin-top: 25px; }
.login-form :deep(.el-form-item) { margin-bottom: 18px; }
.login-form :deep(.el-form-item__label) { color: #53615a; font-size: 14px; font-weight: 600; }
.login-form :deep(.el-input__wrapper) { min-height: 45px; }
.login-form :deep(.el-input__inner) { font-size: 14px; }
.login-submit { height: 44px; margin-top: 12px; }
.login-help { margin-top: 22px; padding-top: 0; color: #79847e; font-size: 12px; }
@media(max-width: 980px) {
  .login-layout { gap: 38px; grid-template-columns: minmax(0, 1fr) minmax(340px, .9fr); }
  .preview-body { grid-template-columns: minmax(0, 1fr) 150px; }
  .preview-portrait { width: 150px; height: 154px; }
  .preview-question strong { font-size: 18px; }
}
@media(max-width: 760px) {
  .login-page { padding: 16px 14px 24px; }
  .login-layout { min-height: auto; grid-template-columns: 1fr; gap: 30px; margin-top: 22px; }
  .login-form-side { order: -1; align-self: stretch; padding: 28px 20px; }
  .login-intro { padding: 12px 4px 0; }
  .intro-rule { margin-bottom: 20px; }
  .login-intro h1 { max-width: 15ch; font-size: 32px; }
  .login-intro > p { margin-top: 12px; font-size: 14px; }
  .login-benefits, .login-preview, .login-footer { display: none; }
}
@media(max-width: 460px) {
  .login-form-side { padding: 22px 0 0; border: 0; background: transparent; }
  .login-form-header h2 { font-size: 25px; }
  .login-form { margin-top: 20px; }
}
</style>
