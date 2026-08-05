import instance, { request } from './request'
import type { ApiResult } from '@/types/chat'

/** 灌入知识库，返回后端提示文案 */
export async function ingestDocument(filePath: string): Promise<string> {
  return request<string>(instance.post<ApiResult<string>>('/knowledge/ingest', { filePath }))
}
