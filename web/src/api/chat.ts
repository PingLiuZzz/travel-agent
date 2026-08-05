import instance, { request } from './request'
import type { ApiResult, ChatRequest, ChatResponse } from '@/types/chat'

/** 发送对话消息，返回智能体回复 */
export async function sendChat(payload: ChatRequest): Promise<string> {
  // 后端 data 是 ChatResponse({ reply }) 对象，需解包取 reply 字符串；
  // 若直接当 string 用，markdown 渲染对象会抛错并冻结 UI
  const data = await request<ChatResponse>(
    instance.post<ApiResult<ChatResponse>>('/chat', payload),
  )
  return data.reply
}
