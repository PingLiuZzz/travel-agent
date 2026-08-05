<script setup lang="ts">
import { computed } from 'vue'
import { theme as antdTheme } from 'ant-design-vue'
import { useThemeStore } from '@/stores/theme'
import MainLayout from '@/layouts/MainLayout.vue'

const themeStore = useThemeStore()

// 旅游主题：天空蓝 primary，圆角 8px；深浅色 algorithm 随主题切换
const themeConfig = computed(() => ({
  algorithm: themeStore.isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
  token: {
    colorPrimary: '#0EA5E9',
    colorInfo: '#0EA5E9',
    borderRadius: 8,
  },
}))
</script>

<template>
  <a-config-provider :theme="themeConfig">
    <MainLayout />
  </a-config-provider>
</template>
