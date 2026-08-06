import instance, { request } from './request'
import type {
  ApiResult,
  ChatMessageVo,
  ChatRequest,
  ChatSessionVo,
  RenameSessionRequest,
} from '@/types/chat'

/** 流式增量回调集合 */
export interface StreamHandlers {
  onToken: (delta: string) => void
  /** 可选：正常完成时触发（多数调用方靠 await 返回 + finally 收尾，无需实现） */
  onDone?: () => void
  onError: (message: string) => void
  /** 传入 AbortController.signal 以支持「停止生成」 */
  signal?: AbortSignal
}

/**
 * 解析单个 SSE 事件块。
 *
 * 按 SSE 规范：每行 field:value，value 前导空格剥离；多行 data 以 \n 拼接； 以 ':' 开头的行为注释，跳过。
 */
function parseSseEvent(raw: string): { event: string; data: string } {
  let event = 'message'
  const dataParts: string[] = []
  for (const line of raw.split('\n')) {
    if (!line || line.startsWith(':')) continue
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) value = value.slice(1)
    if (field === 'event') event = value
    else if (field === 'data') dataParts.push(value)
  }
  return { event, data: dataParts.join('\n') }
}

/**
 * 流式发送对话：POST /api/chat/stream，用 fetch + ReadableStream 逐块解析 SSE。
 *
 * EventSource 仅支持 GET、不能携带 body，故不用。AbortError（用户主动停止）向上抛出， 由调用方识别并静默处理。
 *
 * 事件约定：event:token {delta} / event:done / event:error {message}。
 */
export async function streamChat(payload: ChatRequest, handlers: StreamHandlers): Promise<void> {
  const resp = await fetch('/api/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: handlers.signal,
  })
  if (!resp.ok || !resp.body) {
    handlers.onError(`请求失败（${resp.status}）`)
    return
  }

  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let finished = false
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // 以空行（\n\n）切分完整事件块
      let sep = buffer.indexOf('\n\n')
      while (sep !== -1) {
        const raw = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        const { event, data } = parseSseEvent(raw)
        if (event === 'token') {
          try {
            handlers.onToken((JSON.parse(data) as { delta: string }).delta)
          } catch {
            // 跳过解析失败的增量
          }
        } else if (event === 'done') {
          finished = true
          handlers.onDone?.()
          return
        } else if (event === 'error') {
          finished = true
          let msg = '生成失败'
          try {
            msg = (JSON.parse(data) as { message: string }).message || msg
          } catch {
            // 保留默认错误文案
          }
          handlers.onError(msg)
          return
        }
        sep = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock()
  }
  // 流自然结束但未收到 done/error：视为连接异常
  if (!finished) handlers.onError('连接已断开')
}

/** 拉取全部会话列表（启动填左侧栏） */
export async function getSessions(): Promise<ChatSessionVo[]> {
  return request<ChatSessionVo[]>(instance.get<ApiResult<ChatSessionVo[]>>('/chat/sessions'))
}

/** 拉取某会话全部消息 */
export async function getSessionMessages(userId: string): Promise<ChatMessageVo[]> {
  return request<ChatMessageVo[]>(
    instance.get<ApiResult<ChatMessageVo[]>>(`/chat/sessions/${userId}/messages`),
  )
}

/** 重命名会话 */
export async function renameSession(userId: string, title: string): Promise<void> {
  const payload: RenameSessionRequest = { title }
  await request<null>(instance.patch<ApiResult<null>>(`/chat/sessions/${userId}`, payload))
}

/** 删除会话及其消息 */
export async function deleteSession(userId: string): Promise<void> {
  await request<null>(instance.delete<ApiResult<null>>(`/chat/sessions/${userId}`))
}
