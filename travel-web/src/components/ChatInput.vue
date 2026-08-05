<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { SendOutlined } from '@ant-design/icons-vue'

const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ send: [content: string] }>()

const inputValue = ref('')
const inputRef = ref<{ focus: () => void } | null>(null)

const canSend = computed(() => inputValue.value.trim().length > 0 && !props.disabled)

function handleSend(): void {
  if (!canSend.value) return
  const text = inputValue.value.trim()
  inputValue.value = ''
  emit('send', text)
  nextTick(() => inputRef.value?.focus())
}
</script>

<template>
  <div class="chat-input">
    <div class="input-wrapper">
      <a-textarea
        ref="inputRef"
        v-model:value="inputValue"
        :disabled="disabled"
        :auto-size="{ minRows: 1, maxRows: 5 }"
        placeholder="输入你的旅行计划，例如：帮我规划下周深圳到北京的三天旅行"
        @press-enter="handleSend"
      />
      <button
        class="send-btn"
        :class="{ active: canSend }"
        :disabled="!canSend"
        @click="handleSend"
      >
        <SendOutlined />
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-input {
  padding: 16px 24px;
  border-top: 1px solid var(--app-border);
  background: var(--app-header-bg);
  backdrop-filter: blur(12px);
  transition: background 0.3s ease;
}

.input-wrapper {
  max-width: 768px;
  margin: 0 auto;
  position: relative;
  display: flex;
  align-items: flex-end;
  background: var(--app-input-bg);
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 8px 12px;
  box-shadow: var(--app-shadow);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.input-wrapper:focus-within {
  border-color: var(--app-primary);
  box-shadow: 0 0 0 3px rgba(14, 165, 233, 0.1);
}

/* 覆盖 Ant Design Textarea 默认样式 */
:deep(.ant-input) {
  border: none !important;
  box-shadow: none !important;
  background: transparent !important;
  padding: 4px 0;
  font-size: 15px;
  line-height: 1.6;
  resize: none;
  color: var(--app-text);
}
:deep(.ant-input::placeholder) {
  color: var(--app-text-muted);
}

.send-btn {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: var(--app-border);
  color: var(--app-text-muted);
  cursor: pointer;
  transition: all 0.2s ease;
  margin-left: 8px;
  font-size: 15px;
}
.send-btn.active {
  background: var(--app-primary);
  color: #fff;
}
.send-btn.active:hover {
  background: var(--app-primary-hover);
}
.send-btn:disabled {
  cursor: not-allowed;
}
</style>
