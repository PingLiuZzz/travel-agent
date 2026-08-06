<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { CompassOutlined } from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import ChatInput from '@/components/ChatInput.vue'
import MessageBubble from '@/components/MessageBubble.vue'

const store = useChatStore()
const scrollRef = ref<HTMLElement | null>(null)

/** 是否已滚到接近底部（用于流式时仅在用户未上滑的情况下自动跟随） */
function isNearBottom(): boolean {
  const el = scrollRef.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 120
}

function scrollToBottom(force = false): void {
  nextTick(() => {
    const el = scrollRef.value
    if (!el) return
    if (force || isNearBottom()) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// 最后一条消息的内容长度（流式逐 token 增长时会变化）
const lastMsgLen = computed(() => {
  const arr = store.activeMessages
  const last = arr[arr.length - 1]
  return last ? last.content.length : 0
})

// 新消息：强制滚到底；流式内容增长：仅当用户已在底部附近时跟随，避免打断上滑阅读
watch(() => store.activeMessages.length, () => scrollToBottom(true))
watch(lastMsgLen, () => scrollToBottom(false))

function handleSend(content: string): void {
  store.sendMessage(content)
}

/** 内联编辑发送：用编辑后的内容发起新消息 */
function handleEditSend(newContent: string): void {
  store.sendMessage(newContent)
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
        @edit-send="handleEditSend"
      />
    </div>

    <!-- 输入区（始终在底部） -->
    <ChatInput
      :disabled="store.loading"
      :streaming="store.loading"
      @send="handleSend"
      @stop="store.stopGenerating"
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
</style>
