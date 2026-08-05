import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'travel-theme-mode'

/**
 * 主题状态：浅色 / 深色 / 跟随系统。
 *
 * - mode 持久化到 localStorage。
 * - 跟随系统时监听 prefers-color-scheme 变化。
 * - isDark 同步到 <html data-theme>，驱动全局 CSS 变量切换。
 */
export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem(STORAGE_KEY) as ThemeMode) || 'system')

  // 跟随系统：监听系统主题变化
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  const systemDark = ref(mediaQuery.matches)
  mediaQuery.addEventListener('change', (event) => {
    systemDark.value = event.matches
  })

  const isDark = computed(() => {
    if (mode.value === 'dark') return true
    if (mode.value === 'light') return false
    return systemDark.value
  })

  // 同步到 <html data-theme>，驱动 style.css 中的 CSS 变量
  watch(
    isDark,
    (dark) => {
      document.documentElement.dataset.theme = dark ? 'dark' : 'light'
    },
    { immediate: true },
  )

  function setMode(next: ThemeMode): void {
    mode.value = next
    localStorage.setItem(STORAGE_KEY, next)
  }

  return { mode, isDark, setMode }
})
