import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { sendChat } from '@/api/chat'
import type { ChatMessage, Session } from '@/types/chat'

/**
 * 对话状态管理（组合式 store）。
 *
 * 设计要点：
 * - 按 userId 存储消息（多会话隔离，切换不丢上下文）。
 * - 使用不可变更新（展开运算符）符合不可变数据偏好。
 */
export const useChatStore = defineStore('chat', () => {
  const sessions = ref<Session[]>([])
  const activeUserId = ref<string>('')
  // 按 userId 分组存储消息，切换会话不丢历史
  const messagesByUser = ref<Record<string, ChatMessage[]>>({})
  const loading = ref(false)

  const activeMessages = computed<ChatMessage[]>(
    () => messagesByUser.value[activeUserId.value] ?? [],
  )

  /** 新建会话，返回生成的 userId */
  function createSession(): string {
    const userId = `u-${Date.now()}`
    const newSession: Session = { userId, title: '新对话', lastMessage: '' }
    sessions.value = [newSession, ...sessions.value]
    activeUserId.value = userId
    messagesByUser.value = { ...messagesByUser.value, [userId]: [] }
    return userId
  }

  function selectSession(userId: string): void {
    activeUserId.value = userId
  }

  /** 删除会话及其消息；若删的是当前会话，切到第一个或新建空会话 */
  function removeSession(userId: string): void {
    sessions.value = sessions.value.filter((session) => session.userId !== userId)
    const rest = { ...messagesByUser.value }
    delete rest[userId]
    messagesByUser.value = rest
    if (activeUserId.value === userId) {
      activeUserId.value = sessions.value[0]?.userId ?? ''
      if (sessions.value.length === 0) {
        createSession()
      }
    }
  }

  /** 发送消息并接收智能体回复 */
  async function sendMessage(content: string): Promise<void> {
    if (!activeUserId.value) {
      createSession()
    }
    const userId = activeUserId.value
    appendMessage(userId, { role: 'user', content, createTime: now() })

    loading.value = true
    try {
      const reply = await sendChat({ userId, message: content })
      appendMessage(userId, { role: 'assistant', content: reply, createTime: now() })
      updateSessionMeta(userId, content, reply)
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
    // 移除末尾的 assistant 消息（若有），保留历史
    const trimmed =
      list.length > 0 && list[list.length - 1].role === 'assistant'
        ? list.slice(0, -1)
        : list
    messagesByUser.value = { ...messagesByUser.value, [userId]: trimmed }

    loading.value = true
    try {
      const reply = await sendChat({ userId, message: lastUserContent })
      appendMessage(userId, { role: 'assistant', content: reply, createTime: now() })
      updateSessionMeta(userId, lastUserContent, reply)
    } finally {
      loading.value = false
    }
  }

  /** 不可变追加消息 */
  function appendMessage(userId: string, message: ChatMessage): void {
    const list = messagesByUser.value[userId] ?? []
    messagesByUser.value = { ...messagesByUser.value, [userId]: [...list, message] }
  }

  /** 更新会话标题（首条消息截断）与最后消息预览 */
  function updateSessionMeta(userId: string, firstContent: string, lastReply: string): void {
    sessions.value = sessions.value.map((session) => {
      if (session.userId !== userId) return session
      return {
        ...session,
        title: session.title === '新对话' ? firstContent.slice(0, 12) : session.title,
        lastMessage: lastReply,
      }
    })
  }

  function now(): string {
    return new Date().toLocaleTimeString('zh-CN', { hour12: false })
  }

  return {
    sessions,
    activeUserId,
    loading,
    activeMessages,
    createSession,
    selectSession,
    removeSession,
    sendMessage,
    regenerate,
  }
})
