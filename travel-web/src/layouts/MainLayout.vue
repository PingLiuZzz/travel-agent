<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOutlined, CompassOutlined, MessageOutlined } from '@ant-design/icons-vue'
import ThemeSetting from '@/components/ThemeSetting.vue'
import SessionSidebar from '@/components/SessionSidebar.vue'

const route = useRoute()
const router = useRouter()

const isChat = computed(() => route.path === '/chat')

function navigate(path: string): void {
  router.push(path)
}
</script>

<template>
  <div class="app-shell">
    <!-- 顶部导航栏 -->
    <header class="app-header">
      <div class="header-left">
        <CompassOutlined class="header-logo" />
        <span class="header-brand">Travel</span>
        <span class="header-subtitle">旅游智能体</span>
      </div>

      <nav class="header-nav">
        <button
          class="nav-btn"
          :class="{ active: isChat }"
          @click="navigate('/chat')"
        >
          <MessageOutlined />
          <span>对话</span>
        </button>
        <button
          class="nav-btn"
          :class="{ active: !isChat }"
          @click="navigate('/knowledge')"
        >
          <BookOutlined />
          <span>知识库</span>
        </button>
      </nav>

      <div class="header-right">
        <ThemeSetting />
      </div>
    </header>

    <!-- 主体区域 -->
    <div class="app-body">
      <!-- 对话页：左侧会话栏 -->
      <SessionSidebar v-if="isChat" />

      <!-- 右侧内容区 -->
      <main class="app-content">
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

/* ===== Header ===== */
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
  transition: background 0.3s ease;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-right: 24px;
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
  font-weight: 400;
}

/* 导航按钮 */
.header-nav {
  display: flex;
  gap: 4px;
  flex: 1;
}
.nav-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--app-text-secondary);
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.nav-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-text);
}
.nav-btn.active {
  background: var(--app-active-bg);
  color: var(--app-primary);
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  margin-left: auto;
}

/* ===== Body ===== */
.app-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* 内容区 */
.app-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
</style>
