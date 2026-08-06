<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
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

/* 内容区 */
.app-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}
</style>
