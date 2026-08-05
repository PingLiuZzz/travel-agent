// 与后端 DTO 对齐的类型定义

/** 消息角色：user 用户，assistant 智能体 */
export type MessageRole = 'user' | 'assistant'

/** 单条对话消息 */
export interface ChatMessage {
  role: MessageRole
  content: string
  createTime: string
}

/** 对话请求（对应后端 ChatRequest） */
export interface ChatRequest {
  userId: string
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
}

/** 会话（前端维护，userId 作为多用户隔离标识） */
export interface Session {
  userId: string
  title: string
  lastMessage: string
}
