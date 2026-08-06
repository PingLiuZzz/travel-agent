import instance, { request } from './request'
import type { ApiResult } from '@/types/chat'
import type { KnowledgeDocument } from '@/types/knowledge'

/**
 * 上传文档并灌入向量库，返回灌入后的文档元数据。
 * 用 FormData 承载文件，axios 检测到 FormData 会自动设置 multipart/form-data 及 boundary。
 */
export async function uploadDocument(file: File): Promise<KnowledgeDocument> {
  const form = new FormData()
  form.append('file', file)
  return request<KnowledgeDocument>(
    instance.post<ApiResult<KnowledgeDocument>>('/knowledge/documents', form),
  )
}

/** 列出全部已灌入文档（按灌入时间倒序） */
export async function listDocuments(): Promise<KnowledgeDocument[]> {
  return request<KnowledgeDocument[]>(
    instance.get<ApiResult<KnowledgeDocument[]>>('/knowledge/documents'),
  )
}

/** 删除指定文档及其向量片段 */
export async function deleteDocument(documentId: string): Promise<void> {
  await request<null>(instance.delete<ApiResult<null>>(`/knowledge/documents/${documentId}`))
}
