<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { CompassOutlined } from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import ChatInput from '@/components/ChatInput.vue'
import MessageBubble from '@/components/MessageBubble.vue'

const store = useChatStore()
const scrollRef = ref<HTMLElement | null>(null)

/** 暴露给子组件：将文本填入输入框供用户编辑 */
const editContent = ref('')

function scrollToBottom(): void {
  nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    }
  })
}

watch(() => store.activeMessages.length, scrollToBottom)

function handleSend(content: string): void {
  editContent.value = ''
  store.sendMessage(content)
}

/** 编辑用户消息：将内容放回输入框 */
function handleEdit(content: string): void {
  editContent.value = content
}

onMounted(() => {
  store.init()
})
</script>

<template>
  <div class="chat-view">
    <!-- 新建会话引导（无 activeUserId 且无消息时） -->
    <div v-if="!store.activeUserId && store.activeMessages.length === 0" class="welcome">
      <div class="welcome-inner">
        <CompassOutlined class="welcome-icon" />
        <h1 class="welcome-title">去哪儿？</h1>
        <p class="welcome-desc">
          告诉我你的目的地和日期，我来帮你规划完美的旅程
        </p>
        <div class="welcome-hints">
          <button
            v-for="hint in ['帮我规划东京三日游', '下周去上海，天气怎么样？', '推荐大理的小众景点']"
            :key="hint"
            class="hint-chip"
            @click="handleSend(hint)"
          >
            {{ hint }}
          </button>
        </div>
      </div>
    </div>

    <!-- 对话消息 -->
    <div
      v-else
      ref="scrollRef"
      class="messages"
      :class="{ 'messages--centered': store.activeMessages.length === 0 }"
    >
      <MessageBubble
        v-for="msg in store.activeMessages"
        :key="msg.id"
        :message="msg"
        @regenerate="store.regenerate"
        @edit="handleEdit"
      />
      <div v-if="store.loading" class="loading">
        <a-spin tip="规划中..." />
      </div>
    </div>

    <!-- 输入区（始终在底部） -->
    <ChatInput
      :disabled="store.loading"
      :model-value="editContent"
      @update:model-value="editContent = $event"
      @send="handleSend"
    />
  </div>
</template>

<style scoped>
.chat-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 欢迎页 */
.welcome {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}
.welcome-inner {
  text-align: center;
  max-width: 520px;
}
.welcome-icon {
  font-size: 56px;
  color: var(--app-primary);
  margin-bottom: 20px;
  opacity: 0.8;
}
.welcome-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--app-text);
  margin: 0 0 8px;
  letter-spacing: -0.5px;
}
.welcome-desc {
  font-size: 15px;
  color: var(--app-text-secondary);
  margin: 0 0 28px;
  line-height: 1.6;
}
.welcome-hints {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
}
.hint-chip {
  padding: 8px 16px;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-bubble-bg);
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}
.hint-chip:hover {
  border-color: var(--app-primary);
  color: var(--app-primary);
  background: var(--app-active-bg);
}

/* 消息区 */
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 32px 0;
}
.messages--centered {
  display: flex;
  align-items: center;
  justify-content: center;
}

.loading {
  padding: 12px 24px;
  text-align: center;
}
</style>
