<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeftOutlined, CloudUploadOutlined, FileTextOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { ingestDocument } from '@/api/knowledge'

const router = useRouter()
const filePath = ref('')
const loading = ref(false)

async function handleIngest(): Promise<void> {
  const path = filePath.value.trim()
  if (!path) {
    message.warning('请输入文档路径')
    return
  }
  loading.value = true
  try {
    const result = await ingestDocument(path)
    message.success(result)
    filePath.value = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="knowledge-view">
    <div class="knowledge-header">
      <a-button type="text" @click="router.push('/chat')">
        <template #icon><ArrowLeftOutlined /></template>
        返回对话
      </a-button>
    </div>
    <div class="knowledge-body">
      <div class="knowledge-card">
        <h3 class="card-title">知识库管理</h3>
        <p class="card-desc">
          输入服务器上的文档路径（支持纯文本），文档将被切分、向量化后存入向量库，供智能体检索引用。
        </p>
        <div class="ingest-form">
          <a-input
            v-model:value="filePath"
            placeholder="例如：docs/beijing-guide.txt"
            :disabled="loading"
            @press-enter="handleIngest"
          >
            <template #prefix><FileTextOutlined /></template>
          </a-input>
          <a-button type="primary" :loading="loading" @click="handleIngest">
            <template #icon><CloudUploadOutlined /></template>
            灌入
          </a-button>
        </div>
      </div>
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
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border);
}
.knowledge-body {
  flex: 1;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 24px;
  overflow-y: auto;
}
.knowledge-card {
  width: 100%;
  max-width: 520px;
  padding: 28px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  background: var(--app-bubble-bg);
}
.card-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--app-text);
  margin: 0 0 10px;
}
.card-desc {
  font-size: 13px;
  color: var(--app-text-muted);
  margin: 0 0 20px;
  line-height: 1.7;
}
.ingest-form {
  display: flex;
  gap: 10px;
}
</style>
