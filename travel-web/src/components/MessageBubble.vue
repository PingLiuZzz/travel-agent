<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import {
  CheckOutlined,
  CloseOutlined,
  CopyOutlined,
  EditOutlined,
  ReloadOutlined,
  SendOutlined,
} from '@ant-design/icons-vue'
import type { ChatMessage } from '@/types/chat'

const props = defineProps<{ message: ChatMessage }>()
const emit = defineEmits<{
  regenerate: []
  'edit-send': [newContent: string]
}>()

const markdown = new MarkdownIt({ html: false, linkify: true, breaks: true })

const isUser = computed(() => props.message.role === 'user')
const renderedHtml = computed(() => markdown.render(props.message.content))

const copied = ref(false)
async function handleCopy(text?: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text ?? props.message.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch { /* ignore */ }
}

// 内联编辑状态
const editing = ref(false)
const editText = ref('')

function startEdit(): void {
  editText.value = props.message.content
  editing.value = true
  nextTick(() => {
    const ta = document.querySelector('.inline-edit-area textarea') as HTMLTextAreaElement | null
    ta?.focus()
  })
}

function cancelEdit(): void {
  editing.value = false
  editText.value = ''
}

function confirmEdit(): void {
  const text = editText.value.trim()
  if (!text) return
  editing.value = false
  editText.value = ''
  emit('edit-send', text)
}
</script>

<template>
  <!-- 用户消息 -->
  <div v-if="isUser" class="msg-user-wrap">
    <!-- 正常显示 -->
    <template v-if="!editing">
      <div class="msg-user">{{ message.content }}</div>
      <div class="msg-actions user-actions">
        <button class="action-btn" :title="copied ? '已复制' : '复制'" @click="handleCopy()">
          <CheckOutlined v-if="copied" />
          <CopyOutlined v-else />
        </button>
        <button class="action-btn" title="编辑" @click="startEdit">
          <EditOutlined />
        </button>
      </div>
    </template>

    <!-- 内联编辑模式 -->
    <div v-else class="inline-edit">
      <div class="inline-edit-area">
        <a-textarea
          v-model:value="editText"
          :auto-size="{ minRows: 2, maxRows: 6 }"
          @press-enter="confirmEdit"
        />
      </div>
      <div class="inline-edit-actions">
        <button class="edit-cancel-btn" @click="cancelEdit">
          <CloseOutlined />
          <span>取消</span>
        </button>
        <button class="edit-send-btn" :disabled="!editText.trim()" @click="confirmEdit">
          <SendOutlined />
          <span>发送</span>
        </button>
      </div>
    </div>
  </div>

  <!-- AI 消息 -->
  <div v-else class="msg-ai">
    <!-- 空内容：流式前的"思考中"占位 -->
    <div v-if="!message.content" class="thinking">思考中<span class="cursor" /></div>
    <template v-else>
      <div class="markdown-body" v-html="renderedHtml" />
      <div class="msg-actions">
        <button class="action-btn" :title="copied ? '已复制' : '复制'" @click="handleCopy()">
          <CheckOutlined v-if="copied" />
          <CopyOutlined v-else />
        </button>
        <button class="action-btn" title="重新生成" @click="emit('regenerate')">
          <ReloadOutlined />
        </button>
      </div>
    </template>
  </div>
</template>

<style scoped>
/* ===== 用户消息 ===== */
.msg-user-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-bottom: 28px;
  padding: 0 24px;
}
.msg-user-wrap:hover .user-actions {
  opacity: 1;
}
.msg-user {
  max-width: 75%;
  padding: 12px 20px;
  border-radius: 16px;
  background: var(--app-hover-bg);
  color: var(--app-text);
  font-size: 16px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ===== AI 消息 ===== */
.msg-ai {
  max-width: 800px;
  margin: 0 auto 32px;
  padding: 0 24px;
}
.msg-ai:hover .msg-actions {
  opacity: 1;
}

/* 流式前的"思考中"占位 + 闪烁光标 */
.thinking {
  font-size: 16px;
  color: var(--app-text-muted);
  display: inline-flex;
  align-items: center;
}
.cursor {
  display: inline-block;
  width: 8px;
  height: 18px;
  margin-left: 4px;
  border-radius: 1px;
  background: var(--app-primary);
  animation: msg-cursor-blink 1s steps(2, start) infinite;
}
@keyframes msg-cursor-blink {
  to {
    visibility: hidden;
  }
}

/* 操作按钮 */
.msg-actions {
  display: flex;
  gap: 2px;
  margin-top: 6px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.user-actions {
  justify-content: flex-end;
}
.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  font-size: 14px;
  transition: all 0.15s ease;
}
.action-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}

/* ===== 内联编辑区 ===== */
.inline-edit {
  width: 100%;
  max-width: 640px;
}
.inline-edit-area {
  margin-bottom: 10px;
}
.inline-edit-area :deep(.ant-input) {
  font-size: 16px;
  line-height: 1.65;
  border-radius: 12px;
  padding: 12px 16px;
  resize: none;
}
.inline-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
.edit-cancel-btn,
.edit-send-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.edit-cancel-btn {
  background: transparent;
  color: var(--app-text-secondary);
  border: 1px solid var(--app-border);
}
.edit-cancel-btn:hover {
  background: var(--app-hover-bg);
}
.edit-send-btn {
  background: var(--app-primary);
  color: #fff;
}
.edit-send-btn:hover {
  background: var(--app-primary-hover);
}
.edit-send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ===== Markdown ===== */
.markdown-body {
  font-size: 16px;
  line-height: 1.75;
  color: var(--app-text);
}
.markdown-body :deep(h1) { font-size: 1.5em; font-weight: 700; margin: 24px 0 12px; }
.markdown-body :deep(h2) { font-size: 1.3em; font-weight: 600; margin: 20px 0 10px; padding-bottom: 6px; border-bottom: 1px solid var(--app-border); }
.markdown-body :deep(h3) { font-size: 1.15em; font-weight: 600; margin: 16px 0 8px; }
.markdown-body :deep(h4) { font-size: 1em; font-weight: 600; margin: 14px 0 6px; }
.markdown-body :deep(p) { margin: 8px 0; }
.markdown-body :deep(ul),
.markdown-body :deep(ol) { padding-left: 24px; margin: 10px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(li)::marker { color: var(--app-text-muted); }
.markdown-body :deep(strong) { font-weight: 600; }
.markdown-body :deep(code) {
  background: var(--app-hover-bg);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.88em;
  font-family: 'JetBrains Mono', 'Fira Code', 'Consolas', monospace;
  color: var(--app-accent);
}
.markdown-body :deep(pre) {
  background: var(--app-sider-bg);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 16px 20px;
  overflow-x: auto;
  margin: 14px 0;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: var(--app-text);
  font-size: 0.85em;
  line-height: 1.65;
}
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--app-primary);
  padding: 8px 16px;
  margin: 12px 0;
  background: var(--app-active-bg);
  border-radius: 0 8px 8px 0;
  color: var(--app-text-secondary);
}
.markdown-body :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 14px 0;
  font-size: 0.93em;
}
.markdown-body :deep(th) {
  background: var(--app-hover-bg);
  font-weight: 600;
  padding: 10px 14px;
  text-align: left;
  border-bottom: 2px solid var(--app-border);
}
.markdown-body :deep(td) {
  padding: 10px 14px;
  border-bottom: 1px solid var(--app-border);
}
.markdown-body :deep(tr:last-child td) { border-bottom: none; }
.markdown-body :deep(hr) { border: none; border-top: 1px solid var(--app-border); margin: 20px 0; }
.markdown-body :deep(a) { color: var(--app-primary); text-decoration: none; }
.markdown-body :deep(a:hover) { text-decoration: underline; }
</style>
