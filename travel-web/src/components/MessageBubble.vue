<script setup lang="ts">
import { computed, ref } from 'vue'
import MarkdownIt from 'markdown-it'
import { CheckOutlined, CopyOutlined, EditOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import type { ChatMessage } from '@/types/chat'

const props = defineProps<{ message: ChatMessage }>()
const emit = defineEmits<{
  regenerate: []
  edit: [content: string]
}>()

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

const isUser = computed(() => props.message.role === 'user')
const renderedHtml = computed(() => markdown.render(props.message.content))

const copied = ref(false)
async function handleCopy(): Promise<void> {
  try {
    await navigator.clipboard.writeText(props.message.content)
    copied.value = true
    setTimeout(() => (copied.value = false), 2000)
  } catch {
    /* 剪贴板不可用 */
  }
}
</script>

<template>
  <!-- 用户消息 -->
  <div v-if="isUser" class="msg-user-wrap">
    <div class="msg-user">{{ message.content }}</div>
    <div class="msg-actions user-actions">
      <button class="action-btn" :title="copied ? '已复制' : '复制'" @click="handleCopy">
        <CheckOutlined v-if="copied" />
        <CopyOutlined v-else />
      </button>
      <button class="action-btn" title="编辑" @click="emit('edit', message.content)">
        <EditOutlined />
      </button>
    </div>
  </div>

  <!-- AI 消息 -->
  <div v-else class="msg-ai">
    <div class="markdown-body" v-html="renderedHtml" />
    <div class="msg-actions">
      <button class="action-btn" :title="copied ? '已复制' : '复制'" @click="handleCopy">
        <CheckOutlined v-if="copied" />
        <CopyOutlined v-else />
      </button>
      <button class="action-btn" title="重新生成" @click="emit('regenerate')">
        <ReloadOutlined />
      </button>
    </div>
  </div>
</template>

<style scoped>
/* ===== 用户消息：右对齐，简洁底纹 ===== */
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

/* ===== AI 消息：全宽 Markdown 文档流 ===== */
.msg-ai {
  max-width: 800px;
  margin: 0 auto 32px;
  padding: 0 24px;
}
.msg-ai:hover .msg-actions {
  opacity: 1;
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

/* ===== Markdown 渲染（DeepSeek 风格） ===== */
.markdown-body {
  font-size: 16px;
  line-height: 1.75;
  color: var(--app-text);
}

/* 标题 */
.markdown-body :deep(h1) { font-size: 1.5em; font-weight: 700; margin: 24px 0 12px; }
.markdown-body :deep(h2) { font-size: 1.3em; font-weight: 600; margin: 20px 0 10px; padding-bottom: 6px; border-bottom: 1px solid var(--app-border); }
.markdown-body :deep(h3) { font-size: 1.15em; font-weight: 600; margin: 16px 0 8px; }
.markdown-body :deep(h4) { font-size: 1em; font-weight: 600; margin: 14px 0 6px; }

/* 段落 */
.markdown-body :deep(p) { margin: 8px 0; }

/* 列表 */
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
  padding-left: 24px;
  margin: 10px 0;
}
.markdown-body :deep(li) {
  margin: 4px 0;
}
.markdown-body :deep(li)::marker {
  color: var(--app-text-muted);
}

/* 粗体 / 斜体 */
.markdown-body :deep(strong) { font-weight: 600; color: var(--app-text); }
.markdown-body :deep(em) { font-style: italic; }

/* 代码 */
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

/* 引用 */
.markdown-body :deep(blockquote) {
  border-left: 3px solid var(--app-primary);
  padding: 8px 16px;
  margin: 12px 0;
  background: var(--app-active-bg);
  border-radius: 0 8px 8px 0;
  color: var(--app-text-secondary);
}

/* 表格 */
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
.markdown-body :deep(tr:last-child td) {
  border-bottom: none;
}

/* 分割线 */
.markdown-body :deep(hr) {
  border: none;
  border-top: 1px solid var(--app-border);
  margin: 20px 0;
}

/* 链接 */
.markdown-body :deep(a) {
  color: var(--app-primary);
  text-decoration: none;
}
.markdown-body :deep(a:hover) {
  text-decoration: underline;
}
</style>
