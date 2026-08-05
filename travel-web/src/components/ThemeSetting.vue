<script setup lang="ts">
import type { Component } from 'vue'
import { SettingOutlined } from '@ant-design/icons-vue'
import { Monitor, Moon, Sun } from 'lucide-vue-next'
import { useThemeStore } from '@/stores/theme'
import type { ThemeMode } from '@/stores/theme'

const themeStore = useThemeStore()

// 主题 → 图标：Ant 没有 Sun/Moon，用 lucide（Sun/Moon/Monitor）
const iconMap: Record<ThemeMode, Component> = {
  light: Sun,
  dark: Moon,
  system: Monitor,
}

const optionList: { mode: ThemeMode; label: string }[] = [
  { mode: 'light', label: '浅色' },
  { mode: 'dark', label: '深色' },
  { mode: 'system', label: '跟随系统' },
]
</script>

<template>
  <a-dropdown placement="topRight">
    <a-button type="text" shape="circle" aria-label="主题设置">
      <template #icon><SettingOutlined /></template>
    </a-button>
    <template #overlay>
      <a-menu :selected-keys="[themeStore.mode]">
        <a-menu-item
          v-for="opt in optionList"
          :key="opt.mode"
          @click="themeStore.setMode(opt.mode)"
        >
          <component :is="iconMap[opt.mode]" :size="14" />
          <span class="label">{{ opt.label }}</span>
        </a-menu-item>
      </a-menu>
    </template>
  </a-dropdown>
</template>

<style scoped>
.label {
  margin-left: 8px;
}
</style>
