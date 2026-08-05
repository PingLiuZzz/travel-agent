<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { SendOutlined } from '@ant-design/icons-vue'

const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ send: [content: string] }>()

const inputValue = ref('')
// 结构类型约束：只需 focus 方法，避免引入复杂组件 ref 类型
const inputRef = ref<{ focus: () => void } | null>(null)

const canSend = computed(() => inputValue.value.trim().length > 0 && !props.disabled)

function handleSend(): void {
  if (!canSend.value) return
  const text = inputValue.value.trim()
  inputValue.value = ''
  emit('send', text)
  // 发送后重置并自动聚焦，准备下一次输入
  nextTick(() => inputRef.value?.focus())
}
</script>

<template>
  <div class="chat-input">
    <a-input
      ref="inputRef"
      v-model:value="inputValue"
      :disabled="disabled"
      placeholder="输入你的问题，例如：帮我规划下周深圳到北京的三天旅行"
      size="large"
      @press-enter="handleSend"
    />
    <a-button type="primary" size="large" :disabled="!canSend" @click="handleSend">
      <template #icon><SendOutlined /></template>
      发送
    </a-button>
  </div>
</template>

<style scoped>
.chat-input {
  display: flex;
  gap: 12px;
  padding: 16px;
  border-top: 1px solid var(--app-border, #f0f0f0);
  background-color: var(--app-input-bg, #ffffff);
}
</style>
