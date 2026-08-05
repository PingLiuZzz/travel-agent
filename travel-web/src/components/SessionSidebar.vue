<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BookOutlined,
  CompassOutlined,
  DeleteOutlined,
  EditOutlined,
  EllipsisOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PushpinFilled,
  PushpinOutlined,
  SearchOutlined,
  SettingOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { Monitor, Moon, Sun } from 'lucide-vue-next'
import type { Component } from 'vue'
import { useChatStore } from '@/stores/chat'
import { useThemeStore } from '@/stores/theme'
import type { ThemeMode } from '@/stores/theme'

const store = useChatStore()
const themeStore = useThemeStore()
const router = useRouter()

const editingId = ref<string | null>(null)
const editTitle = ref('')
const themeIconMap: Record<ThemeMode, Component> = {
  light: Sun,
  dark: Moon,
  system: Monitor,
}
const themeLabels: Record<ThemeMode, string> = {
  light: '浅色',
  dark: '深色',
  system: '跟随系统',
}

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

function handleDelete(userId: string): void {
  store.removeSession(userId)
}

const themeModes: ThemeMode[] = ['light', 'dark', 'system']

const showSettingsModal = ref(false)

/** 折叠态点击搜索：展开边栏并聚焦搜索框 */
function openSearchFromCollapsed(): void {
  store.sidebarCollapsed = false
  // 等 sidebar 展开后聚焦 search input
  setTimeout(() => {
    const input = document.querySelector('.search-wrap input') as HTMLInputElement | null
    input?.focus()
  }, 300)
}
</script>

<template>
  <aside class="sidebar" :class="{ collapsed: store.sidebarCollapsed }">
    <!-- 折叠时：仅图标模式（logo + 展开 + 搜索 + 新旅程） -->
    <template v-if="store.sidebarCollapsed">
      <div class="collapsed-bar">
        <CompassOutlined class="collapsed-logo" />
        <button class="icon-btn" title="展开边栏" @click="store.toggleSidebar()">
          <MenuUnfoldOutlined />
        </button>
        <button class="icon-btn" title="搜索会话" @click="openSearchFromCollapsed()">
          <SearchOutlined />
        </button>
        <button class="icon-btn" title="新旅程" @click="store.newChat()">
          <CompassOutlined />
        </button>
      </div>
    </template>

    <!-- 展开时：完整侧边栏 -->
    <template v-else>
      <!-- 顶部：Logo + 收起按钮 -->
      <div class="sidebar-header">
        <div class="header-brand" @click="store.newChat()">
          <CompassOutlined class="brand-icon" />
          <span class="brand-name">Travel</span>
        </div>
        <button class="icon-btn" title="收起边栏" @click="store.toggleSidebar()">
          <MenuFoldOutlined />
        </button>
      </div>

      <!-- 新建会话 -->
      <button class="new-chat-btn" @click="store.newChat()">
        <CompassOutlined />
        <span>新旅程</span>
      </button>

      <!-- 搜索框 -->
      <div class="search-wrap">
        <a-input
          v-model:value="store.searchQuery"
          placeholder="搜索会话..."
          size="small"
          allow-clear
        >
          <template #prefix><SearchOutlined class="search-icon" /></template>
        </a-input>
      </div>

      <!-- 会话列表 -->
      <div class="session-list">
        <div
          v-for="session in store.displayedSessions"
          :key="session.userId"
          class="session-item"
          :class="{ active: session.userId === store.activeUserId }"
          @click="store.selectSession(session.userId)"
        >
          <!-- 置顶标记 -->
          <PushpinFilled v-if="store.pinnedUserIds.includes(session.userId)" class="pin-icon" />

          <div class="session-main">
            <div v-if="editingId === session.userId" class="edit-wrap" @click.stop>
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

          <!-- 操作菜单（⋮ 下拉） -->
          <a-dropdown trigger="click" @click.stop>
            <button class="more-btn" @click.stop><EllipsisOutlined /></button>
            <template #overlay>
              <a-menu>
                <a-menu-item key="rename" @click.stop="startRename(session.userId, session.title)">
                  <EditOutlined /><span>重命名</span>
                </a-menu-item>
                <a-menu-item
                  v-if="store.pinnedUserIds.includes(session.userId)"
                  key="unpin"
                  @click.stop="store.pinSession(session.userId)"
                >
                  <PushpinFilled /><span>取消置顶</span>
                </a-menu-item>
                <a-menu-item v-else key="pin" @click.stop="store.pinSession(session.userId)">
                  <PushpinOutlined /><span>置顶</span>
                </a-menu-item>
                <a-menu-item key="share" @click.stop="store.shareSession(session.userId)">
                  <ShareAltOutlined /><span>分享</span>
                </a-menu-item>
                <a-menu-divider />
                <a-menu-item key="delete" danger @click.stop="handleDelete(session.userId)">
                  <DeleteOutlined /><span>删除</span>
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>

        <div v-if="store.sessions.length === 0" class="empty-sessions">
          <CompassOutlined class="empty-icon" />
          <p>开启你的第一次旅行</p>
        </div>
      </div>

      <!-- 底部设置区：系统设置按钮 -->
      <div class="sidebar-footer">
        <button class="footer-btn" @click="showSettingsModal = true">
          <SettingOutlined />
          <span>系统设置</span>
        </button>
      </div>

      <!-- 系统设置弹窗 -->
      <a-modal
        v-model:open="showSettingsModal"
        title="系统设置"
        :footer="null"
        width="360px"
        :z-index="1001"
      >
        <div class="settings-modal">
          <!-- 主题设置 -->
          <div class="settings-section">
            <div class="settings-label">主题设置</div>
            <div class="theme-options">
              <button
                v-for="opt in themeModes"
                :key="opt"
                class="theme-option"
                :class="{ active: themeStore.mode === opt }"
                @click="themeStore.setMode(opt)"
              >
                <component :is="themeIconMap[opt]" :size="18" />
                <span>{{ themeLabels[opt] }}</span>
              </button>
            </div>
          </div>

          <a-divider />

          <!-- 知识库管理 -->
          <div class="settings-section">
            <div class="settings-label">文档管理</div>
            <button class="settings-link" @click="showSettingsModal = false; router.push('/knowledge')">
              <BookOutlined />
              <span>知识库管理</span>
              <span class="settings-arrow">→</span>
            </button>
          </div>
        </div>
      </a-modal>
    </template>
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
  transition: width 0.25s ease, background 0.3s ease;
  overflow: hidden;
}
.sidebar.collapsed {
  width: 56px;
}

/* ===== 折叠模式 ===== */
.collapsed-bar {
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
  padding: 14px 0;
  gap: 10px;
}
.collapsed-logo {
  font-size: 22px;
  color: var(--app-primary);
  margin-bottom: 6px;
}

/* 通用图标按钮 */
.icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  cursor: pointer;
  transition: all 0.15s ease;
  font-size: 16px;
  flex-shrink: 0;
}
.icon-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}

/* ===== 展开模式 ===== */
.sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px 16px;
}
.header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}
.brand-icon {
  font-size: 20px;
  color: var(--app-primary);
}
.brand-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--app-text);
  letter-spacing: -0.3px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin: 0 16px 12px;
  padding: 8px 0;
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

/* 搜索 */
.search-wrap {
  padding: 0 16px 10px;
}
.search-icon {
  color: var(--app-text-muted);
  font-size: 13px;
}
:deep(.search-wrap .ant-input) {
  border-radius: 8px;
  background: var(--app-hover-bg);
  border-color: transparent;
}
:deep(.search-wrap .ant-input:focus) {
  border-color: var(--app-primary);
  background: var(--app-input-bg);
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
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  margin-bottom: 2px;
  transition: all 0.15s ease;
  gap: 4px;
}
.session-item:hover {
  background: var(--app-sider-hover);
}
.session-item.active {
  background: var(--app-active-bg);
  box-shadow: inset 3px 0 0 var(--app-primary);
}
.pin-icon {
  font-size: 10px;
  color: var(--app-text-muted);
  flex-shrink: 0;
  margin-right: 2px;
}
.session-main {
  flex: 1;
  min-width: 0;
}
.session-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 1px;
}
.session-preview {
  font-size: 11px;
  color: var(--app-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.edit-wrap {
  width: 100%;
}

/* 更多按钮 */
.more-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s ease;
  font-size: 14px;
  flex-shrink: 0;
}
.session-item:hover .more-btn {
  opacity: 1;
}
.more-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}

/* 下拉菜单项样式 */
:deep(.ant-dropdown-menu-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
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
  padding: 10px 16px;
  border-top: 1px solid var(--app-border);
}
.footer-row {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.footer-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  width: 100%;
  text-align: left;
}
.footer-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}

/* ===== 系统设置弹窗 ===== */
.settings-modal {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.settings-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.settings-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-secondary);
}
.theme-options {
  display: flex;
  gap: 8px;
}
.theme-option {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 8px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.theme-option:hover {
  border-color: var(--app-primary);
  color: var(--app-text);
}
.theme-option.active {
  border-color: var(--app-primary);
  background: var(--app-active-bg);
  color: var(--app-primary);
  font-weight: 500;
}
.settings-link {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
  width: 100%;
  text-align: left;
}
.settings-link:hover {
  border-color: var(--app-primary);
  color: var(--app-text);
}
.settings-arrow {
  margin-left: auto;
  color: var(--app-text-muted);
}
</style>
