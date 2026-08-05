<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { BookOutlined, CompassOutlined, MessageOutlined } from '@ant-design/icons-vue'
import ThemeSetting from '@/components/ThemeSetting.vue'

const route = useRoute()
const router = useRouter()

const selectedKeys = computed<string[]>(() => [route.path])

function navigate(path: string): void {
  router.push(path)
}
</script>

<template>
  <a-layout class="layout">
    <a-layout-sider class="sider" width="220">
      <div class="brand">
        <CompassOutlined class="brand-icon" />
        <span class="brand-text">旅游智能体</span>
      </div>

      <a-menu mode="inline" :selected-keys="selectedKeys" class="nav-menu">
        <a-menu-item key="/chat" @click="navigate('/chat')">
          <template #icon><MessageOutlined /></template>
          <span>对话</span>
        </a-menu-item>
        <a-menu-item key="/knowledge" @click="navigate('/knowledge')">
          <template #icon><BookOutlined /></template>
          <span>知识库</span>
        </a-menu-item>
      </a-menu>

      <div class="sider-footer">
        <ThemeSetting />
        <span class="footer-text">主题设置</span>
      </div>
    </a-layout-sider>

    <a-layout class="main">
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.layout {
  height: 100%;
}
.sider {
  background-color: var(--app-sider-bg);
  border-right: 1px solid var(--app-border);
  display: flex;
  flex-direction: column;
}
.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 20px;
  font-size: 17px;
  font-weight: 700;
  color: var(--app-text);
  border-bottom: 1px solid var(--app-border);
}
.brand-icon {
  color: var(--app-primary);
  font-size: 22px;
}
.nav-menu {
  flex: 1;
  border-inline-end: none !important;
  background-color: var(--app-sider-bg);
}
.sider-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--app-border);
  color: var(--app-text-secondary);
}
.footer-text {
  font-size: 14px;
}
.main,
.content {
  height: 100%;
}
.content {
  background-color: var(--app-bg);
}
</style>
