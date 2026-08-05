<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons-vue'
import { useChatStore } from '@/stores/chat'
import ChatInput from '@/components/ChatInput.vue'
import MessageBubble from '@/components/MessageBubble.vue'

const store = useChatStore()
const scrollRef = ref<HTMLElement | null>(null)

// 消息变化时自动滚动到底部
function scrollToBottom(): void {
  nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    }
  })
}

watch(() => store.activeMessages.length, scrollToBottom)

function handleSend(content: string): void {
  store.sendMessage(content)
}

onMounted(() => {
  store.init()
})
</script>

<template>
  <div class="chat-view">
    <!-- 左：会话列表 -->
    <aside class="sessions">
      <a-button type="primary" block @click="store.newChat()">
        <template #icon><PlusOutlined /></template>
        新建会话
      </a-button>
      <div class="session-list">
        <div
          v-for="session in store.sessions"
          :key="session.userId"
          class="session-item"
          :class="{ active: session.userId === store.activeUserId }"
        >
          <div class="session-content" @click="store.selectSession(session.userId)">
            <div class="session-title">{{ session.title }}</div>
            <div class="session-preview">{{ session.lastMessage || '暂无消息' }}</div>
          </div>
          <a-popconfirm
            title="确定删除该会话？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="store.removeSession(session.userId)"
          >
            <DeleteOutlined class="session-delete" @click.stop />
          </a-popconfirm>
        </div>
      </div>
    </aside>

    <!-- 右：对话区 -->
    <section class="conversation">
      <div ref="scrollRef" class="messages">
        <a-empty
          v-if="store.activeMessages.length === 0 && !store.loading"
          description="开始你的第一次旅行规划吧"
          class="empty"
        />
        <MessageBubble
          v-for="msg in store.activeMessages"
          :key="msg.id"
          :message="msg"
          @regenerate="store.regenerate"
        />
        <div v-if="store.loading" class="loading">
          <a-spin tip="智能体思考中..." />
        </div>
      </div>
      <ChatInput :disabled="store.loading" @send="handleSend" />
    </section>
  </div>
</template>

<style scoped>
.chat-view {
  display: flex;
  height: 100%;
}
.sessions {
  width: 260px;
  flex-shrink: 0;
  padding: 16px;
  border-right: 1px solid var(--app-border, #f0f0f0);
  background-color: var(--app-sider-bg, #ffffff);
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.session-list {
  overflow-y: auto;
  flex: 1;
}
.session-item {
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.session-item:hover {
  background-color: var(--app-hover-bg, #f5f5f5);
}
.session-item.active {
  background-color: var(--app-active-bg, #e6f4ff);
}
.session-content {
  flex: 1;
  min-width: 0;
}
.session-title {
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-preview {
  font-size: 12px;
  color: var(--app-text-secondary, #999);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 4px;
}
.session-delete {
  opacity: 0;
  color: var(--app-text-secondary, #999);
  flex-shrink: 0;
  padding: 4px;
  transition: opacity 0.2s, color 0.2s;
}
.session-item:hover .session-delete {
  opacity: 1;
}
.session-delete:hover {
  color: #ff4d4f;
}
.conversation {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
.empty {
  margin-top: 80px;
}
.loading {
  padding: 16px 0;
}
</style>
