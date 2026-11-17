<script setup>
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  consent: { type: Object, default: null },
  loading: { type: Boolean, default: false },
});
const emit = defineEmits(['update:modelValue', 'confirm']);
const { t } = useI18n();
const checked = ref(false);

watch(() => props.modelValue, (open) => {
  if (open) checked.value = false;
});

function confirm() {
  if (!checked.value || props.loading || !props.consent?.version) return;
  emit('confirm', props.consent.version);
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    class="consent-dialog"
    width="min(520px, calc(100vw - 32px))"
    :title="t('deviceCheck.consentTitle')"
    :close-on-click-modal="false"
    :show-close="false"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="consent-intro">{{ t('deviceCheck.consentIntro') }}</p>
    <ul class="consent-points">
      <li>{{ t('deviceCheck.consentPurpose') }}</li>
      <li>{{ t('deviceCheck.consentNoBiometric') }}</li>
      <li>{{ t('deviceCheck.consentRetention') }}</li>
    </ul>
    <el-checkbox v-model="checked">{{ t('deviceCheck.consentCheckbox') }}</el-checkbox>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">{{ t('deviceCheck.consentCancel') }}</el-button>
      <el-button class="primary-button" :loading="loading" :disabled="!checked || loading" @click="confirm">
        {{ t('deviceCheck.consentConfirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.consent-intro { margin: 0 0 12px; color: #56635c; font-size: 14px; line-height: 1.75; }
.consent-points { display: grid; gap: 8px; margin: 0 0 18px; padding: 13px 14px 13px 30px; border: 1px solid #e4ebe6; border-radius: 6px; background: #f8faf9; color: #66736c; font-size: 13px; line-height: 1.7; }
.consent-points li { padding-left: 2px; }
</style>
