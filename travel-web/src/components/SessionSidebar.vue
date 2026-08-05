<script setup lang="ts">
import { CompassOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons-vue'
import ThemeSetting from '@/components/ThemeSetting.vue'
import { useChatStore } from '@/stores/chat'
import { ref } from 'vue'

const store = useChatStore()

const editingId = ref<string | null>(null)
const editTitle = ref('')

function startRename(userId: string, currentTitle: string): void {
  editingId.value = userId
  editTitle.value = currentTitle
}

function confirmRename(userId: string): void {
  const title = editTitle.value.trim()
  if (title && title !== store.sessions.find(s => s.userId === userId)?.title) {
    store.renameSessionWithTitle(userId, title)
  }
  editingId.value = null
}

function cancelRename(): void {
  editingId.value = null
}
</script>

<template>
  <aside class="sidebar">
    <!-- 新建会话按钮 -->
    <button class="new-chat-btn" @click="store.newChat()">
      <CompassOutlined class="new-chat-icon" />
      <span>新旅程</span>
    </button>

    <!-- 会话列表 -->
    <div class="session-list">
      <div
        v-for="session in store.sessions"
        :key="session.userId"
        class="session-item"
        :class="{ active: session.userId === store.activeUserId }"
        @click="store.selectSession(session.userId)"
      >
        <div class="session-main">
          <!-- 可编辑标题 -->
          <div v-if="editingId === session.userId" class="edit-title-wrap" @click.stop>
            <a-input
              v-model:value="editTitle"
              size="small"
              @press-enter="confirmRename(session.userId)"
              @blur="cancelRename"
            />
          </div>
          <template v-else>
            <div class="session-title">{{ session.title }}</div>
            <div class="session-preview">{{ session.lastMessage || '开始一段新的旅程...' }}</div>
          </template>
        </div>

        <!-- 操作按钮（hover 显示） -->
        <div class="session-actions">
          <a-tooltip title="重命名">
            <a-button
              size="small"
              type="text"
              class="action-btn"
              @click.stop="startRename(session.userId, session.title)"
            >
              <EditOutlined />
            </a-button>
          </a-tooltip>
          <a-popconfirm
            title="确定删除该会话？"
            ok-text="删除"
            cancel-text="取消"
            @confirm="store.removeSession(session.userId)"
          >
            <a-tooltip title="删除">
              <a-button size="small" type="text" class="action-btn" @click.stop>
                <DeleteOutlined />
              </a-button>
            </a-tooltip>
          </a-popconfirm>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="store.sessions.length === 0" class="empty-sessions">
        <CompassOutlined class="empty-icon" />
        <p>开启你的第一次旅行</p>
      </div>
    </div>

    <!-- 底部：主题切换 -->
    <div class="sidebar-footer">
      <ThemeSetting />
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 280px;
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--app-sider-bg);
  border-right: 1px solid var(--app-border);
  transition: background 0.3s ease;
}

/* 新建会话按钮 */
.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 16px;
  padding: 10px 0;
  border: 1px dashed var(--app-border);
  border-radius: 10px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.new-chat-btn:hover {
  border-color: var(--app-primary);
  color: var(--app-primary);
  background: var(--app-active-bg);
}
.new-chat-icon {
  font-size: 16px;
}

/* 会话列表 */
.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
}
.session-item {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: all 0.15s ease;
  position: relative;
}
.session-item:hover {
  background: var(--app-sider-hover);
}
.session-item.active {
  background: var(--app-active-bg);
  box-shadow: inset 3px 0 0 var(--app-primary);
}
.session-main {
  flex: 1;
  min-width: 0;
}
.session-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 2px;
}
.session-preview {
  font-size: 12px;
  color: var(--app-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.edit-title-wrap {
  width: 100%;
}

/* 操作按钮 */
.session-actions {
  display: flex;
  gap: 0;
  opacity: 0;
  transition: opacity 0.15s ease;
  flex-shrink: 0;
}
.session-item:hover .session-actions {
  opacity: 1;
}
.action-btn {
  color: var(--app-text-muted) !important;
  font-size: 13px;
  padding: 2px;
}
.action-btn:hover {
  color: var(--app-text) !important;
}

/* 空状态 */
.empty-sessions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 40px 16px;
  color: var(--app-text-muted);
}
.empty-icon {
  font-size: 36px;
  opacity: 0.4;
}

/* 底部 */
.sidebar-footer {
  padding: 12px 16px;
  border-top: 1px solid var(--app-border);
  display: flex;
  justify-content: center;
}
</style>
