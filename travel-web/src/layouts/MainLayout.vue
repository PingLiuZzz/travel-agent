<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { CompassOutlined, MenuUnfoldOutlined } from '@ant-design/icons-vue'
import SessionSidebar from '@/components/SessionSidebar.vue'
import { useChatStore } from '@/stores/chat'

const route = useRoute()
const store = useChatStore()

const isChat = computed(() => route.path === '/chat')
</script>

<template>
  <div class="app-shell">
    <!-- 主体区域 -->
    <div class="app-body">
      <!-- 对话页：左侧会话栏 -->
      <SessionSidebar v-if="isChat" />

      <!-- 内容区 -->
      <main class="app-content">
        <!-- 非对话页（知识库等）显示小 header -->
        <header v-if="!isChat" class="app-header">
          <div class="header-left">
            <CompassOutlined class="header-logo" />
            <span class="header-brand">Travel</span>
            <span class="header-subtitle">旅游智能体</span>
          </div>
        </header>

        <!-- 对话页：折叠状态下显示展开按钮 -->
        <div v-if="isChat && store.sidebarCollapsed" class="floating-toggle">
          <button class="icon-btn" title="展开边栏" @click="store.toggleSidebar()">
            <MenuUnfoldOutlined />
          </button>
        </div>

        <router-view />
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.app-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* Header —— 仅非对话页使用 */
.app-header {
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background: var(--app-header-bg);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--app-border);
  z-index: 10;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-logo {
  font-size: 22px;
  color: var(--app-primary);
}
.header-brand {
  font-size: 17px;
  font-weight: 700;
  color: var(--app-text);
  letter-spacing: -0.3px;
}
.header-subtitle {
  font-size: 13px;
  color: var(--app-text-muted);
}

/* 折叠时的浮动展开按钮 */
.floating-toggle {
  position: absolute;
  top: 12px;
  left: 12px;
  z-index: 20;
}
.icon-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  background: var(--app-bubble-bg);
  color: var(--app-text-secondary);
  cursor: pointer;
  font-size: 16px;
  box-shadow: var(--app-shadow);
  transition: all 0.15s ease;
}
.icon-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}

/* 内容区 */
.app-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  position: relative;
}
</style>
