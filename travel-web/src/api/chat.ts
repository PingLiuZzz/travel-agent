import instance, { request } from './request'
import type {
  ApiResult,
  ChatMessageVo,
  ChatRequest,
  ChatResponse,
  ChatSessionVo,
  RenameSessionRequest,
} from '@/types/chat'

/** 发送对话消息，返回智能体回复 + 会话标识 */
export async function sendChat(payload: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>(instance.post<ApiResult<ChatResponse>>('/chat', payload))
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
  await request<null>(
    instance.patch<ApiResult<null>>(`/chat/sessions/${userId}`, payload),
  )
}

/** 删除会话及其消息 */
export async function deleteSession(userId: string): Promise<void> {
  await request<null>(instance.delete<ApiResult<null>>(`/chat/sessions/${userId}`))
}
