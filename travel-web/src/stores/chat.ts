import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  deleteSession,
  getSessionMessages,
  getSessions,
  renameSession,
  streamChat,
} from '@/api/chat'
import type { ChatMessage, ChatSessionVo } from '@/types/chat'

const CACHE_KEY = 'travel-agent:chat-state'
const PINNED_KEY = 'travel-agent:pinned'

const DAY_MS = 24 * 60 * 60 * 1000

/** 会话日期分组桶（前端展示归类用） */
type SessionDateBucket = 'today' | 'yesterday' | 'week' | 'earlier'

/** 侧边栏会话分组：置顶组 + 按日期归类的多个组 */
export interface SessionGroup {
  label: string
  items: ChatSessionVo[]
}

/**
 * 解析会话时间字符串（后端 "yyyy-MM-dd HH:mm:ss"）。
 * 空格分隔替换为 ISO 'T' 以兼容各浏览器；无法解析返回 null。
 */
function parseSessionDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const d = new Date(value.replace(' ', 'T'))
  return Number.isNaN(d.getTime()) ? null : d
}

/** 取毫秒时间戳；无法解析返回 0（排序时沉底） */
function tsOf(value: string | null | undefined): number {
  return parseSessionDate(value)?.getTime() ?? 0
}

/** 按日期归类到展示桶：今天 / 昨天 / 7天内 / 更早（无法解析归入更早） */
function bucketOf(value: string | null | undefined): SessionDateBucket {
  const date = parseSessionDate(value)
  if (!date) return 'earlier'
  const midnight = (d: Date) =>
    new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime()
  const diff = midnight(new Date()) - midnight(date)
  if (diff <= 0) return 'today'
  if (diff < DAY_MS) return 'yesterday'
  if (diff < DAY_MS * 7) return 'week'
  return 'earlier'
}

/**
 * 对话状态管理（组合式 store）。
 *
 * 设计要点：
 * - 后端为唯一真相源：会话列表来自 getSessions()，消息来自 getSessionMessages()。
 * - localStorage 仅缓存已加载会话的消息，切换会话命中缓存免重复请求。
 * - 新建对话：首条消息发出时客户端即生成 userId，使前端在流式前就持有会话标识。
 * - 发送/重新生成均走流式：立即追加用户消息 + 空助手占位，逐 token 填充。
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

  /** 当前流式请求的中断器（非响应式，仅内部控制） */
  let activeController: AbortController | null = null

  const activeMessages = computed<ChatMessage[]>(
    () => messagesByUser.value[activeUserId.value] ?? [],
  )

  /** 会话列表分组：搜索过滤 → 按 updateTime 倒序 → 置顶成组 → 其余按日期归类 */
  const groupedSessions = computed<SessionGroup[]>(() => {
    // 搜索过滤（标题 + 最后一条消息）
    let list = sessions.value
    if (searchQuery.value) {
      const q = searchQuery.value.toLowerCase()
      list = list.filter(
        s =>
          s.title.toLowerCase().includes(q) ||
          (s.lastMessage && s.lastMessage.toLowerCase().includes(q)),
      )
    }
    // 按 updateTime 倒序（无法解析的时间戳记 0，沉底）
    const sorted = [...list].sort(
      (a, b) => tsOf(b.updateTime) - tsOf(a.updateTime),
    )
    // 置顶单独成组（置顶区内同样按时间倒序）
    const pinned = sorted.filter(s => pinnedUserIds.value.includes(s.userId))
    const unpinned = sorted.filter(
      s => !pinnedUserIds.value.includes(s.userId),
    )
    // 非置顶按日期归类
    const buckets: Record<SessionDateBucket, ChatSessionVo[]> = {
      today: [],
      yesterday: [],
      week: [],
      earlier: [],
    }
    for (const s of unpinned) {
      buckets[bucketOf(s.updateTime)].push(s)
    }
    const groups: SessionGroup[] = []
    if (pinned.length) groups.push({ label: '置顶', items: pinned })
    if (buckets.today.length) groups.push({ label: '今天', items: buckets.today })
    if (buckets.yesterday.length) groups.push({ label: '昨天', items: buckets.yesterday })
    if (buckets.week.length) groups.push({ label: '7天内', items: buckets.week })
    if (buckets.earlier.length) groups.push({ label: '更早', items: buckets.earlier })
    return groups
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

  /** 停止当前生成：中断进行中的流式请求，保留已生成的部分文本 */
  function stopGenerating(): void {
    activeController?.abort()
  }

  /** 客户端生成会话标识，使前端在首条消息发送前即持有 sessionId，便于流式挂载 */
  function genUserId(): string {
    return `u-${Date.now()}`
  }

  /** 向流式助手消息追加增量文本（不可变更新） */
  function appendDelta(userId: string, aiId: number, delta: string): void {
    const list = messagesByUser.value[userId] ?? []
    messagesByUser.value = {
      ...messagesByUser.value,
      [userId]: list.map(m => (m.id === aiId ? { ...m, content: m.content + delta } : m)),
    }
  }

  /** 用最终（或停止时的部分）助手内容刷新会话最后一条消息 */
  function finalizeAssistant(userId: string, aiId: number): void {
    const list = messagesByUser.value[userId] ?? []
    const ai = list.find(m => m.id === aiId)
    const reply = ai?.content ?? ''
    sessions.value = sessions.value.map(s =>
      s.userId === userId ? { ...s, lastMessage: reply, updateTime: now() } : s,
    )
  }

  /**
   * 流式执行一次生成：逐 token 填充指定助手消息。
   * 用户主动停止（AbortError）静默、保留部分文本；其余异常弹错。两者均在 finally 收尾。
   */
  async function runStream(userId: string, text: string, aiId: number): Promise<void> {
    loading.value = true
    const controller = new AbortController()
    activeController = controller
    try {
      await streamChat({ userId, message: text }, {
        onToken: delta => appendDelta(userId, aiId, delta),
        onError: msg => message.error(msg),
        signal: controller.signal,
      })
    } catch (err) {
      if (!(err instanceof DOMException && err.name === 'AbortError')) {
        message.error('生成失败，请重试')
      }
    } finally {
      finalizeAssistant(userId, aiId)
      loading.value = false
      activeController = null
      persistCache()
    }
  }

  /** 发送消息；新建态下首次发送会生成 userId 并建会话 */
  async function sendMessage(content: string): Promise<void> {
    if (loading.value) return
    const isNewChat = !activeUserId.value
    if (isNewChat) {
      activeUserId.value = genUserId()
      sessions.value = [
        {
          userId: activeUserId.value,
          title: content.slice(0, 12),
          lastMessage: content,
          createTime: now(),
          updateTime: now(),
        },
        ...sessions.value,
      ]
    }
    const userId = activeUserId.value
    // 立即追加用户消息 + 空助手占位（占位气泡显示"思考中"）
    const userMsg: ChatMessage = { id: Date.now(), role: 'user', content, createTime: now() }
    const aiId = Date.now() + 1
    const aiMsg: ChatMessage = { id: aiId, role: 'assistant', content: '', createTime: now() }
    messagesByUser.value = {
      ...messagesByUser.value,
      [userId]: [...(messagesByUser.value[userId] ?? []), userMsg, aiMsg],
    }
    sessions.value = sessions.value.map(s =>
      s.userId === userId ? { ...s, lastMessage: content, updateTime: now() } : s,
    )
    await runStream(userId, content, aiId)
  }

  /** 重新生成最后一条 AI 回复：移除它，重发上一条用户消息（流式） */
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
    const aiId = Date.now() + 1
    const aiMsg: ChatMessage = { id: aiId, role: 'assistant', content: '', createTime: now() }
    messagesByUser.value = {
      ...messagesByUser.value,
      [userId]: [...trimmed, aiMsg],
    }
    await runStream(userId, lastUserContent, aiId)
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
    const d = new Date()
    const pad = (n: number) => String(n).padStart(2, '0')
    // 与后端 VO 时间格式对齐：yyyy-MM-dd HH:mm:ss（本地时间），便于按日期归类
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
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
    groupedSessions,
    pinnedUserIds,
    searchQuery,
    sidebarCollapsed,
    init,
    newChat,
    selectSession,
    sendMessage,
    regenerate,
    stopGenerating,
    renameSessionWithTitle,
    removeSession,
    pinSession,
    shareSession,
    toggleSidebar,
  }
})
