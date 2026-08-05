// 与后端 DTO 对齐的类型定义

/** 消息角色：user 用户，assistant 智能体 */
export type MessageRole = 'user' | 'assistant'

/** 单条对话消息 */
export interface ChatMessage {
  id: number
  role: MessageRole
  content: string
  createTime: string
}

/** 对话请求（对应后端 ChatRequest）；userId 可空，为空表示新建会话 */
export interface ChatRequest {
  userId?: string
  message: string
}

/** 后端统一返回结构（对应后端 ApiResult） */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 对话响应载荷（对应后端 ChatResponse） */
export interface ChatResponse {
  reply: string
  sessionId: string
}

/** 会话视图（对应后端 ChatSessionVo） */
export interface ChatSessionVo {
  userId: string
  title: string
  lastMessage: string
  createTime: string
  updateTime: string
}

/** 消息视图（对应后端 ChatMessageVo） */
export interface ChatMessageVo {
  id: number
  role: MessageRole
  content: string
  createTime: string
}

/** 重命名请求 */
export interface RenameSessionRequest {
  title: string
}
