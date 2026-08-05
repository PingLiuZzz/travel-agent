import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  deleteSession,
  getSessionMessages,
  getSessions,
  renameSession,
  sendChat,
} from '@/api/chat'
import type { ChatMessage, ChatSessionVo } from '@/types/chat'

const CACHE_KEY = 'travel-agent:chat-state'
const PINNED_KEY = 'travel-agent:pinned'

/**
 * 对话状态管理（组合式 store）。
 *
 * 设计要点：
 * - 后端为唯一真相源：会话列表来自 getSessions()，消息来自 getSessionMessages()。
 * - localStorage 仅缓存已加载会话的消息，切换会话命中缓存免重复请求。
 * - 新建对话：activeUserId 为空即新建态，首条消息发出后才落库（对标 DeepSeek）。
 * - 不可变更新（展开运算符）。
 * - 置顶与会话搜索在本地进行。
 */
export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSessionVo[]>([])
  const activeUserId = ref<string>('')
  const messagesByUser = ref<Record<string, ChatMessage[]>>(loadCache())
  const loading = ref(false)

  /** 置顶会话 ID 列表（前端本地持久化） */
  const pinnedUserIds = ref<string[]>(loadPinned())
  /** 会话搜索关键词 */
  const searchQuery = ref('')
  /** 侧边栏折叠状态 */
  const sidebarCollapsed = ref(false)

  const activeMessages = computed<ChatMessage[]>(
    () => messagesByUser.value[activeUserId.value] ?? [],
  )

  /** 过滤+排序后的会话列表：搜索过滤 → 置顶优先 → 其余按 updateTime 倒序 */
  const displayedSessions = computed<ChatSessionVo[]>(() => {
    let list = sessions.value
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase()
      list = list.filter(
        s =>
          s.title.toLowerCase().includes(q) ||
          (s.lastMessage && s.lastMessage.toLowerCase().includes(q)),
      )
    }
    const pinned = list.filter(s => pinnedUserIds.value.includes(s.userId))
    const unpinned = list.filter(s => !pinnedUserIds.value.includes(s.userId))
    return [...pinned, ...unpinned]
  })

  /** 启动初始化：拉会话列表，默认选最近会话或进入新建态 */
  async function init(): Promise<void> {
    try {
      const list = await getSessions()
      sessions.value = list
      if (list.length > 0) {
        await selectSession(list[0].userId)
      } else {
        activeUserId.value = ''
      }
    } catch {
      // 后端不可用，保持空状态
    }
  }

  /** 新建对话：只切到新建态，不落库（首条消息才创建会话） */
  function newChat(): void {
    activeUserId.value = ''
  }

  /** 切换会话；命中缓存则免请求 */
  async function selectSession(userId: string): Promise<void> {
    activeUserId.value = userId
    if (messagesByUser.value[userId]) return
    const messages = await getSessionMessages(userId)
    messagesByUser.value = { ...messagesByUser.value, [userId]: messages }
    persistCache()
  }

  /** 发送消息；新建态下首次发消息会建会话 */
  async function sendMessage(content: string): Promise<void> {
    const isNewChat = !activeUserId.value
    const userId = activeUserId.value
    loading.value = true
    try {
      const reply = await sendChat(userId ? { userId, message: content } : { message: content })
      const sessionId = reply.sessionId
      if (isNewChat) {
        activeUserId.value = sessionId
        const newSession: ChatSessionVo = {
          userId: sessionId,
          title: content.slice(0, 12),
          lastMessage: reply.reply,
          createTime: now(),
          updateTime: now(),
        }
        sessions.value = [newSession, ...sessions.value]
      } else {
        sessions.value = sessions.value.map((session) =>
          session.userId === sessionId ? { ...session, lastMessage: reply.reply } : session,
        )
      }
      const list = messagesByUser.value[sessionId] ?? []
      const appended: ChatMessage[] = [
        ...list,
        { id: Date.now(), role: 'user', content, createTime: now() },
        { id: Date.now() + 1, role: 'assistant', content: reply.reply, createTime: now() },
      ]
      messagesByUser.value = { ...messagesByUser.value, [sessionId]: appended }
      persistCache()
    } finally {
      loading.value = false
    }
  }

  /** 重新生成最后一条 AI 回复：移除它，重发上一条用户消息 */
  async function regenerate(): Promise<void> {
    const userId = activeUserId.value
    if (!userId || loading.value) return
    const list = messagesByUser.value[userId] ?? []
    let lastUserContent = ''
    for (let index = list.length - 1; index >= 0; index--) {
      if (list[index].role === 'user') {
        lastUserContent = list[index].content
        break
      }
    }
    if (!lastUserContent) return
    const trimmed =
      list.length > 0 && list[list.length - 1].role === 'assistant' ? list.slice(0, -1) : list
    messagesByUser.value = { ...messagesByUser.value, [userId]: trimmed }

    loading.value = true
    try {
      const reply = await sendChat({ userId, message: lastUserContent })
      const appended = [
        ...trimmed,
        { id: Date.now(), role: 'assistant' as const, content: reply.reply, createTime: now() },
      ]
      messagesByUser.value = { ...messagesByUser.value, [userId]: appended }
      sessions.value = sessions.value.map((session) =>
        session.userId === userId ? { ...session, lastMessage: reply.reply } : session,
      )
      persistCache()
    } finally {
      loading.value = false
    }
  }

  /** 重命名会话 */
  async function renameSessionWithTitle(userId: string, title: string): Promise<void> {
    await renameSession(userId, title)
    sessions.value = sessions.value.map((session) =>
      session.userId === userId ? { ...session, title } : session,
    )
  }

  /** 删除会话及其消息 */
  async function removeSession(userId: string): Promise<void> {
    await deleteSession(userId)
    sessions.value = sessions.value.filter((session) => session.userId !== userId)
    const rest = { ...messagesByUser.value }
    delete rest[userId]
    messagesByUser.value = rest
    persistCache()
    if (activeUserId.value === userId) {
      activeUserId.value = sessions.value[0]?.userId ?? ''
    }
  }

  /** 置顶 / 取消置顶 */
  function pinSession(userId: string): void {
    if (pinnedUserIds.value.includes(userId)) {
      pinnedUserIds.value = pinnedUserIds.value.filter(id => id !== userId)
    } else {
      pinnedUserIds.value = [...pinnedUserIds.value, userId]
    }
    persistPinned()
  }

  /** 分享会话：复制链接到剪贴板 */
  function shareSession(userId: string): void {
    const url = `${window.location.origin}/chat?session=${userId}`
    navigator.clipboard.writeText(url).then(
      () => message.success('会话链接已复制'),
      () => message.error('复制失败'),
    )
  }

  /** 侧边栏折叠切换 */
  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function now(): string {
    return new Date().toLocaleTimeString('zh-CN', { hour12: false })
  }

  function persistPinned(): void {
    try {
      localStorage.setItem(PINNED_KEY, JSON.stringify(pinnedUserIds.value))
    } catch { /* ignore */ }
  }

  function loadPinned(): string[] {
    try {
      const raw = localStorage.getItem(PINNED_KEY)
      return raw ? (JSON.parse(raw) as string[]) : []
    } catch {
      return []
    }
  }

  function persistCache(): void {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(messagesByUser.value))
    } catch {
      // 存储满或不可用，忽略
    }
  }

  function loadCache(): Record<string, ChatMessage[]> {
    try {
      const raw = localStorage.getItem(CACHE_KEY)
      return raw ? (JSON.parse(raw) as Record<string, ChatMessage[]>) : {}
    } catch {
      return {}
    }
  }

  return {
    sessions,
    activeUserId,
    loading,
    activeMessages,
    displayedSessions,
    pinnedUserIds,
    searchQuery,
    sidebarCollapsed,
    init,
    newChat,
    selectSession,
    sendMessage,
    regenerate,
    renameSessionWithTitle,
    removeSession,
    pinSession,
    shareSession,
    toggleSidebar,
  }
})
