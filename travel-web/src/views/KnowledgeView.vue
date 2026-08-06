<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import type { TableColumnsType, UploadProps } from 'ant-design-vue'
import {
  ArrowLeftOutlined,
  DeleteOutlined,
  FileTextOutlined,
  InboxOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { deleteDocument, listDocuments, uploadDocument } from '@/api/knowledge'
import type { KnowledgeDocument } from '@/types/knowledge'

const router = useRouter()
const documents = ref<KnowledgeDocument[]>([])
const loading = ref(false)
const uploading = ref(false)

const columns: TableColumnsType = [
  { title: '文件名', dataIndex: 'fileName', key: 'fileName', ellipsis: true },
  { title: '片段数', dataIndex: 'segmentCount', key: 'segmentCount', width: 90, align: 'center' },
  { title: '大小', key: 'fileSize', width: 110, align: 'right' },
  { title: '灌入时间', dataIndex: 'ingestTime', key: 'ingestTime', width: 180 },
  { title: '操作', key: 'action', width: 90, align: 'center', fixed: 'right' },
]

/** 字节 → 友好尺寸 */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

async function refresh(): Promise<void> {
  loading.value = true
  try {
    documents.value = await listDocuments()
  } catch {
    // request 封装已统一弹错
  } finally {
    loading.value = false
  }
}

/** before-upload：覆盖默认上传，改走统一的 request 封装；返回 false 阻止 antd 自动上传 */
const beforeUpload: NonNullable<UploadProps['beforeUpload']> = async (file) => {
  uploading.value = true
  try {
    const doc = await uploadDocument(file)
    message.success(`已灌入：${doc.fileName}（${doc.segmentCount} 个片段）`)
    await refresh()
  } catch {
    // request 封装已统一弹错
  } finally {
    uploading.value = false
  }
  return false
}

async function handleDelete(record: KnowledgeDocument): Promise<void> {
  try {
    await deleteDocument(record.id)
    message.success(`已删除：${record.fileName}`)
    await refresh()
  } catch {
    // request 封装已统一弹错
  }
}

onMounted(refresh)
</script>

<template>
  <div class="knowledge-view">
    <div class="knowledge-header">
      <a-button type="text" @click="router.push('/chat')">
        <template #icon><ArrowLeftOutlined /></template>
        返回对话
      </a-button>
      <div class="header-title">
        <FileTextOutlined />
        <span>知识库管理</span>
        <button class="refresh-btn" :disabled="loading" title="刷新" @click="refresh">
          <ReloadOutlined :spin="loading" />
        </button>
      </div>
    </div>

    <div class="knowledge-body">
      <!-- 上传区 -->
      <a-upload-dragger
        :before-upload="beforeUpload"
        :show-upload-list="false"
        :disabled="uploading"
        :multiple="false"
        accept=".txt,.md,.markdown,.pdf,.doc,.docx,.html,.csv"
        class="uploader"
      >
        <p class="upload-icon"><InboxOutlined /></p>
        <p class="upload-text">{{ uploading ? '正在灌入…' : '点击或拖拽文件到此处上传' }}</p>
        <p class="upload-hint">支持 PDF / Word / 纯文本 / Markdown / HTML，灌入后供智能体检索引用</p>
      </a-upload-dragger>

      <!-- 文档清单 -->
      <a-table
        :columns="columns"
        :data-source="documents"
        :loading="loading"
        row-key="id"
        :pagination="false"
        size="middle"
        class="doc-table"
      >
        <template #bodyCell="{ column, record }: { column: { key?: string }, record: KnowledgeDocument }">
          <template v-if="column.key === 'fileSize'">
            {{ formatSize(record.fileSize) }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-popconfirm
              title="确定删除该文档？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="handleDelete(record)"
            >
              <button class="row-del-btn" title="删除"><DeleteOutlined /></button>
            </a-popconfirm>
          </template>
        </template>

        <template #emptyText>
          <div class="empty-tip">
            <FileTextOutlined class="empty-icon" />
            <p>知识库还是空的，上传第一个文档吧</p>
          </div>
        </template>
      </a-table>
    </div>
  </div>
</template>

<style scoped>
.knowledge-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.knowledge-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border);
}
.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text);
}
.refresh-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}
.refresh-btn:hover:not(:disabled) {
  background: var(--app-hover-bg);
  color: var(--app-text);
}
.refresh-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.knowledge-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 上传区 */
.uploader {
  border-radius: 12px;
}
.upload-icon {
  font-size: 40px;
  color: var(--app-primary);
  margin: 8px 0;
}
.upload-text {
  font-size: 14px;
  color: var(--app-text);
  margin: 0 0 6px;
}
.upload-hint {
  font-size: 12px;
  color: var(--app-text-muted);
  margin: 0;
}

/* 文档表 */
.doc-table {
  background: var(--app-bubble-bg, transparent);
  border-radius: 10px;
}
.row-del-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  transition: all 0.15s ease;
}
.row-del-btn:hover {
  background: var(--app-hover-bg);
  color: var(--app-danger, #ff4d4f);
}

/* 空状态 */
.empty-tip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 0;
  color: var(--app-text-muted);
}
.empty-icon {
  font-size: 32px;
  opacity: 0.4;
}
</style>
