<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  CheckOutlined,
  CopyOutlined,
  ReloadOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons-vue'
import type { ChatMessage } from '@/types/chat'

const props = defineProps<{ message: ChatMessage }>()
// 仅 AI 消息触发重新生成
const emit = defineEmits<{ regenerate: [] }>()

// 共享一个 markdown-it 实例；禁用原始 HTML 防 XSS，开启链接识别与换行
const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })

const isUser = computed(() => props.message.role === 'user')
const renderedHtml = computed(() => markdown.render(props.message.content))

const copied = ref(false)
async function handleCopy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.message.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    // 剪贴板权限被拒时静默忽略
  }
}
</script>

<template>
  <div class="message" :class="isUser ? 'message--user' : 'message--assistant'">
    <a-avatar class="avatar" :class="{ 'avatar--user': isUser }">
      <UserOutlined v-if="isUser" />
      <RobotOutlined v-else />
    </a-avatar>
    <div class="bubble-wrap">
      <!-- 用户消息纯文本，AI 消息渲染 Markdown -->
      <div v-if="isUser" class="bubble bubble--user">{{ message.content }}</div>
      <div v-else class="bubble bubble--ai markdown-body" v-html="renderedHtml"></div>
      <!-- AI 消息操作栏：复制 + 重新生成 -->
      <div v-if="!isUser" class="actions">
        <a-tooltip :title="copied ? '已复制' : '复制'">
          <a-button size="small" type="text" @click="handleCopy">
            <CheckOutlined v-if="copied" />
            <CopyOutlined v-else />
          </a-button>
        </a-tooltip>
        <a-tooltip title="重新生成">
          <a-button size="small" type="text" @click="emit('regenerate')">
            <ReloadOutlined />
          </a-button>
        </a-tooltip>
      </div>
    </div>
  </div>
</template>

<style scoped>
.message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  align-items: flex-start;
}
.message--user {
  flex-direction: row-reverse;
}
.avatar {
  flex-shrink: 0;
  background-color: #1677ff;
}
.avatar--user {
  background-color: #52c41a;
}
.bubble-wrap {
  max-width: 70%;
  display: flex;
  flex-direction: column;
}
.message--user .bubble-wrap {
  align-items: flex-end;
}
.bubble {
  padding: 10px 16px;
  border-radius: 8px;
  line-height: 1.6;
  word-break: break-word;
}
.bubble--ai {
  background-color: var(--app-bubble-bg, #ffffff);
  border: 1px solid var(--app-border, #f0f0f0);
  color: var(--app-text, rgba(0, 0, 0, 0.88));
}
.bubble--user {
  background-color: #1677ff;
  color: #ffffff;
  white-space: pre-wrap;
}
.actions {
  display: flex;
  gap: 4px;
  margin-top: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
.message:hover .actions {
  opacity: 1;
}
/* Markdown 渲染样式 */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
  margin: 12px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}
.markdown-body :deep(p) {
  margin: 6px 0;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}
.markdown-body :deep(li) {
  margin: 2px 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid var(--app-border, #e8e8e8);
  padding: 6px 10px;
  text-align: left;
}
.markdown-body :deep(code) {
  background: rgba(0, 0, 0, 0.06);
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 0.9em;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--app-border, #d9d9d9);
  padding-left: 12px;
  margin: 8px 0;
  color: var(--app-text-secondary, rgba(0, 0, 0, 0.45));
}
</style>
