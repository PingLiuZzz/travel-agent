import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  deleteSession,
  getSessionMessages,
  getSessions,
  renameSession,
  sendChat,
} from '@/api/chat'
import type { ChatMessage, ChatSessionVo } from '@/types/chat'

const CACHE_KEY = 'travel-agent:chat-state'

/**
 * 对话状态管理（组合式 store）。
 *
 * 设计要点：
 * - 后端为唯一真相源：会话列表来自 getSessions()，消息来自 getSessionMessages()。
 * - localStorage 仅缓存已加载会话的消息，切换会话命中缓存免重复请求。
 * - 新建对话：activeUserId 为空即新建态，首条消息发出后才落库（对标 DeepSeek）。
 * - 不可变更新（展开运算符）。
 */
export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSessionVo[]>([])
  const activeUserId = ref<string>('')
  const messagesByUser = ref<Record<string, ChatMessage[]>>(loadCache())
  const loading = ref(false)

  const activeMessages = computed<ChatMessage[]>(
    () => messagesByUser.value[activeUserId.value] ?? [],
  )

  /** 启动初始化：拉会话列表，默认选最近会话或进入新建态 */
  async function init(): Promise<void> {
    const list = await getSessions()
    sessions.value = list
    if (list.length > 0) {
      await selectSession(list[0].userId)
    } else {
      activeUserId.value = ''
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

  function now(): string {
    return new Date().toLocaleTimeString('zh-CN', { hour12: false })
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
    init,
    newChat,
    selectSession,
    sendMessage,
    regenerate,
    renameSessionWithTitle,
    removeSession,
  }
})
