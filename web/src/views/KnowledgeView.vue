<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { ingestDocument } from '@/api/knowledge'

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
    <a-card title="知识库灌入" class="card">
      <a-typography-paragraph type="secondary">
        输入服务器上的文档路径（一期支持纯文本，后续扩展 PDF/Word），文档将被切分、向量化后存入 Milvus，
        供智能体在回答景点历史、小众路线等问题时检索引用。
      </a-typography-paragraph>
      <a-input
        v-model:value="filePath"
        placeholder="如：docs/beijing-guide.txt"
        @press-enter="handleIngest"
      />
      <div class="actions">
        <a-button type="primary" :loading="loading" @click="handleIngest">
          开始灌入
        </a-button>
      </div>
    </a-card>
  </div>
</template>

<style scoped>
.knowledge-view {
  padding: 24px;
}

.card {
  max-width: 640px;
}

.actions {
  margin-top: 16px;
}
</style>
