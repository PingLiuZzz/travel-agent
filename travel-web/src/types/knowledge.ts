// 与后端 domain.knowledge.KnowledgeDocument record 对齐

/** 知识库文档（灌入记录，兼作列表项） */
export interface KnowledgeDocument {
  id: string
  fileName: string
  fileSize: number
  segmentCount: number
  contentType: string
  ingestTime: string
}
