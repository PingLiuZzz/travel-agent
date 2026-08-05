<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { CompassOutlined } from '@ant-design/icons-vue'
import SessionSidebar from '@/components/SessionSidebar.vue'

const route = useRoute()
const isChat = computed(() => route.path === '/chat')
</script>

<template>
  <div class="app-shell">
    <div class="app-body">
      <!-- 对话页：左侧会话栏（含折叠态所有操作入口） -->
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

/* Header —— 仅非对话页（知识库）使用 */
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

/* 内容区 */
.app-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
</style>
