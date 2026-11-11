<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useI18n } from 'vue-i18n';
import { extractSkillProfile, getSkillProfile, updateSkillProfile } from '../services/jobRadar';

const router = useRouter();
const { t } = useI18n();
const loading = ref(true);
const saving = ref(false);
const extracting = ref(false);
const preview = ref(false);
const resumeText = ref('');
const profile = reactive({
  skills: [], years: null, city: '', expectedMinK: null, expectedMaxK: null, configured: false, profileHash: '',
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    Object.assign(profile, await getSkillProfile());
    profile.skills = (profile.skills || []).map(skill => ({ ...skill, weight: Number(skill.weight) || 1 }));
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.profilePage.loadFailed'));
  } finally {
    loading.value = false;
  }
}

function addSkill() {
  profile.skills.push({ name: '', level: 3, weight: 1 });
}

function removeSkill(index) {
  profile.skills.splice(index, 1);
}

async function extract() {
  if (resumeText.value.trim().length < 10) return ElMessage.warning(t('jobRadar.profilePage.resumeRequired'));
  extracting.value = true;
  try {
    const result = await extractSkillProfile(resumeText.value.trim());
    profile.skills = (result.skills || []).map(skill => ({ ...skill, weight: Number(skill.weight) || 1 }));
    profile.years = result.years;
    profile.city = result.city || '';
    preview.value = true;
    ElMessage.success(t('jobRadar.profilePage.previewReady'));
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.profilePage.extractFailed'));
  } finally {
    extracting.value = false;
  }
}

async function save() {
  const normalized = [];
  const seen = new Set();
  for (const skill of profile.skills) {
    const name = skill.name.trim();
    if (!name) continue;
    const key = name.toLowerCase().replace(/[^\p{L}\p{N}]+/gu, '');
    if (seen.has(key)) return ElMessage.warning(t('jobRadar.profilePage.duplicateSkill', { name }));
    seen.add(key);
    normalized.push({ name, level: skill.level, weight: skill.weight });
  }
  if (profile.expectedMinK != null && profile.expectedMaxK != null && profile.expectedMinK > profile.expectedMaxK) {
    return ElMessage.warning(t('jobRadar.validation.salary'));
  }
  saving.value = true;
  try {
    Object.assign(profile, await updateSkillProfile({
      skills: normalized, years: profile.years, city: profile.city.trim() || null,
      expectedMinK: profile.expectedMinK, expectedMaxK: profile.expectedMaxK,
    }));
    profile.skills = (profile.skills || []).map(skill => ({ ...skill, weight: Number(skill.weight) || 1 }));
    preview.value = false;
    ElMessage.success(t('jobRadar.profilePage.saved'));
  } catch (error) {
    ElMessage.error(error?.message || t('jobRadar.profilePage.saveFailed'));
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <section class="profile-page" v-loading="loading">
    <div class="page-heading">
      <div>
        <button class="text-back" @click="router.push('/jobs')"><el-icon><ArrowLeft /></el-icon>{{ t('jobRadar.backToJobs') }}</button>
        <h1>{{ t('jobRadar.profilePage.title') }}</h1>
        <p>{{ t('jobRadar.profilePage.subtitle') }}</p>
      </div>
      <el-button class="primary-button" :loading="saving" :disabled="saving || extracting" @click="save"><el-icon><Check /></el-icon>{{ t('jobRadar.profilePage.confirmSave') }}</el-button>
    </div>

    <div class="profile-layout">
      <section class="panel extract-panel">
        <div class="section-title">{{ t('jobRadar.profilePage.importTitle') }}<small>{{ t('jobRadar.profilePage.importHint') }}</small></div>
        <el-input v-model="resumeText" type="textarea" :rows="12" maxlength="30000" show-word-limit :placeholder="t('jobRadar.profilePage.resumePlaceholder')" />
        <el-button class="soft-button extract-button" :loading="extracting" :disabled="extracting || saving" @click="extract">
          <el-icon><MagicStick /></el-icon>{{ t('jobRadar.profilePage.extract') }}
        </el-button>
        <div class="privacy-box"><el-icon><Lock /></el-icon><span>{{ t('jobRadar.profilePage.privacy') }}</span></div>
      </section>

      <section class="panel profile-editor">
        <div class="section-title">{{ t('jobRadar.profilePage.editorTitle') }}<el-tag v-if="profile.configured" size="small" type="success">{{ t('jobRadar.profilePage.confirmed') }}</el-tag></div>
        <div v-if="preview" class="preview-note"><el-icon><Warning /></el-icon>{{ t('jobRadar.profilePage.previewNotice') }}</div>
        <div class="basic-grid">
          <label>{{ t('jobRadar.profilePage.years') }}<el-input-number v-model="profile.years" :min="0" :max="60" controls-position="right" /></label>
          <label>{{ t('jobRadar.profilePage.city') }}<el-input v-model="profile.city" maxlength="60" /></label>
          <label>{{ t('jobRadar.profilePage.expectedMin') }}<el-input-number v-model="profile.expectedMinK" :min="0" :max="500" controls-position="right" /></label>
          <label>{{ t('jobRadar.profilePage.expectedMax') }}<el-input-number v-model="profile.expectedMaxK" :min="0" :max="500" controls-position="right" /></label>
        </div>

        <div class="skill-head"><strong>{{ t('jobRadar.profilePage.skills') }}</strong><el-button text @click="addSkill"><el-icon><Plus /></el-icon>{{ t('jobRadar.profilePage.addSkill') }}</el-button></div>
        <div v-for="(skill, index) in profile.skills" :key="`${index}-${skill.name}`" class="skill-row">
          <el-input v-model="skill.name" maxlength="80" :placeholder="t('jobRadar.profilePage.skillName')" />
          <el-select v-model="skill.level" :aria-label="t('jobRadar.profilePage.level')">
            <el-option v-for="level in 5" :key="level" :label="t(`jobRadar.profilePage.level${level}`)" :value="level" />
          </el-select>
          <el-input-number v-model="skill.weight" :min="0.1" :max="10" :step="0.1" controls-position="right" :aria-label="t('jobRadar.profilePage.weight')" />
          <el-button text type="danger" :aria-label="t('common.delete')" @click="removeSkill(index)"><el-icon><Delete /></el-icon></el-button>
        </div>
        <div v-if="!profile.skills.length" class="empty-skills">{{ t('jobRadar.profilePage.emptySkills') }}</div>
        <p v-if="profile.profileHash" class="hash-note">{{ t('jobRadar.profilePage.hash') }} {{ profile.profileHash.slice(0, 12) }}</p>
      </section>
    </div>
  </section>
</template>

<style scoped>
.profile-page { max-width: 1140px; min-height: 420px; }
.text-back { display: inline-flex; align-items: center; gap: 5px; margin-bottom: 8px; padding: 0; border: 0; background: transparent; color: #7d8982; font-size: 13px; }
.profile-layout { display: grid; grid-template-columns: minmax(280px, .78fr) minmax(0, 1.42fr); gap: 20px; align-items: start; }
.extract-panel, .profile-editor { padding: 20px; }
.extract-button { width: 100%; margin-top: 12px; }
.privacy-box, .preview-note { display: flex; gap: 8px; align-items: flex-start; margin-top: 12px; padding: 10px 11px; border-radius: 6px; font-size: 12px; line-height: 1.6; }
.privacy-box { background: var(--green-soft); color: #547064; }
.preview-note { background: var(--yellow-soft); color: #8a6a2b; }
.basic-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 13px; margin-bottom: 22px; }
.basic-grid label { display: grid; gap: 7px; color: #66716b; font-size: 13px; }
:deep(.el-input-number) { width: 100%; }
.skill-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 9px; color: #35413b; font-size: 14px; }
.skill-row { display: grid; grid-template-columns: minmax(140px, 1fr) 150px 122px 34px; gap: 8px; align-items: center; margin-bottom: 8px; }
.empty-skills { padding: 22px; border: 1px dashed var(--line); border-radius: 6px; color: #929b97; text-align: center; font-size: 13px; }
.hash-note { margin: 15px 0 0; color: #a0a8a4; font-family: monospace; font-size: 11px; }
@media (max-width: 900px) { .profile-layout { grid-template-columns: 1fr; } }
@media (max-width: 600px) {
  .basic-grid { grid-template-columns: 1fr; }
  .skill-row { grid-template-columns: minmax(0, 1fr) 34px; }
  .skill-row > :nth-child(2), .skill-row > :nth-child(3) { grid-column: span 1; }
}
</style>
