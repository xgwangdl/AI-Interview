<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { loadMonaco, monacoLanguageId } from '../services/monaco';

const props = defineProps({
  modelValue: { type: String, default: '' },
  language: { type: String, default: 'plaintext' },
  readonly: { type: Boolean, default: false },
  height: { type: String, default: '320px' },
});
const emit = defineEmits(['update:modelValue']);

const host = ref();
let monaco;
let editor;
let subscription;

onMounted(async () => {
  monaco = await loadMonaco();
  editor = monaco.editor.create(host.value, {
    value: props.modelValue || '',
    language: monacoLanguageId(props.language),
    theme: 'vs',
    readOnly: props.readonly,
    automaticLayout: true,
    minimap: { enabled: false },
    fontSize: 13,
    tabSize: 4,
    scrollBeyondLastLine: false,
    renderWhitespace: 'selection',
    fixedOverflowWidgets: true,
  });
  subscription = editor.onDidChangeModelContent(() => emit('update:modelValue', editor.getValue()));
});

watch(() => props.modelValue, (value) => {
  if (editor && value !== editor.getValue()) editor.setValue(value ?? '');
});

watch(() => props.language, (language) => {
  if (editor && monaco) monaco.editor.setModelLanguage(editor.getModel(), monacoLanguageId(language));
});

onBeforeUnmount(() => {
  subscription?.dispose();
  editor?.dispose();
});
</script>

<template>
  <div ref="host" class="code-editor" :style="{ height }"></div>
</template>

<style scoped>
.code-editor { width: 100%; overflow: hidden; border: 1px solid #e2e7e3; border-radius: 6px; }
</style>
