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

const router = useRouter()

/** 跳转知识库管理页（关闭设置弹窗） */
function goKnowledge(): void {
  showSettingsModal.value = false
  router.push('/knowledge')
}

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

      <!-- 会话列表（置顶组 + 按日期归类：今天/昨天/7天内/更早） -->
      <div class="session-list">
        <template v-for="group in store.groupedSessions" :key="group.label">
          <div class="session-group-label">{{ group.label }}</div>
          <div
            v-for="session in group.items"
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
              <div v-else class="session-title">{{ session.title }}</div>
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
        </template>

        <div v-if="store.sessions.length === 0" class="empty-sessions">
          <CompassOutlined class="empty-icon" />
          <p>开启你的第一次旅行</p>
        </div>
      </div>

      <!-- 底部设置区：系统设置 + 知识库入口 -->
      <div class="sidebar-footer">
        <button class="footer-btn" @click="showSettingsModal = true">
          <SettingOutlined />
          <span>系统设置</span>
        </button>
        <button class="footer-btn" @click="goKnowledge">
          <BookOutlined />
          <span>知识库</span>
        </button>
      </div>

      <!-- 系统设置弹窗（主题设置；知识库管理见独立页） -->
      <a-modal
        v-model:open="showSettingsModal"
        title="系统设置"
        :footer="null"
        width="520px"
        :z-index="1001"
        class="settings-modal-root"
        :body-style="{ padding: 0 }"
      >
        <div class="settings-panel">
          <div class="panel-title">主题设置</div>
          <p class="panel-desc">选择适合你的界面主题，设置即时生效。</p>
          <div class="theme-cards">
            <button
              v-for="opt in themeModes"
              :key="opt"
              class="theme-card"
              :class="{ active: themeStore.mode === opt }"
              @click="themeStore.setMode(opt)"
            >
              <component :is="themeIconMap[opt]" :size="28" />
              <span class="theme-card-label">{{ themeLabels[opt] }}</span>
              <span class="theme-card-desc">
                {{ opt === 'light' ? '明亮清爽' : opt === 'dark' ? '柔和护眼' : '自动切换' }}
              </span>
            </button>
          </div>
        </div>
      </a-modal>
    </template>
  </aside>
</template>

<style scoped>
.sidebar {
  width: 300px;
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
  width: 64px;
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
  font-size: 24px;
  color: var(--app-primary);
  margin-bottom: 8px;
}

/* 通用图标按钮 */
.icon-btn {
  width: 40px;
  height: 40px;
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
  font-size: 14px;
  font-weight: 500;
  color: var(--app-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.session-group-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--app-text-muted);
  padding: 10px 10px 4px;
  letter-spacing: 0.3px;
}
.edit-wrap {
  width: 100%;
}

/* 更多按钮 */
.more-btn {
  width: 30px;
  height: 30px;
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
  padding: 10px 12px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
  width: 100%;
  text-align: left;
}
.footer-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}

/* ===== 系统设置弹窗（DeepSeek 左右分栏）===== */
:deep(.settings-modal-root .ant-modal-content) {
  border-radius: 16px;
  overflow: hidden;
  padding: 0;
}
:deep(.settings-modal-root .ant-modal-header) {
  border-bottom: 1px solid var(--app-border);
  padding: 20px 24px;
}
:deep(.settings-modal-root .ant-modal-title) {
  font-size: 17px;
  font-weight: 600;
}
:deep(.settings-modal-root .ant-modal-body) {
  padding: 0;
}

/* 主题设置面板 */
.settings-panel {
  padding: 28px 32px;
  display: flex;
  flex-direction: column;
}
.panel-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--app-text);
  margin-bottom: 6px;
}
.panel-desc {
  font-size: 13px;
  color: var(--app-text-muted);
  margin: 0 0 24px;
  line-height: 1.6;
}

/* 主题横向卡片 */
.theme-cards {
  display: flex;
  gap: 12px;
}
.theme-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 12px;
  border: 2px solid var(--app-border);
  border-radius: 14px;
  background: transparent;
  color: var(--app-text-secondary);
  cursor: pointer;
  transition: all 0.2s ease;
}
.theme-card:hover {
  border-color: var(--app-primary);
  background: var(--app-hover-bg);
}
.theme-card.active {
  border-color: var(--app-primary);
  background: var(--app-active-bg);
  color: var(--app-primary);
}
.theme-card-label {
  font-size: 14px;
  font-weight: 600;
}
.theme-card-desc {
  font-size: 11px;
  color: var(--app-text-muted);
}
</style>
