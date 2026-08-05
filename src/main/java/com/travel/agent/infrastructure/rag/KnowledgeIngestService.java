package com.travel.agent.infrastructure.rag;

import com.travel.agent.common.exception.BizException;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * 知识库灌入服务。
 *
 * <p>M3 阶段核心流程：文档切分 → 向量化 → 存入 Milvus。
 * 灌入后即可被 Agent 的 ContentRetriever 自动检索召回。
 */
@Service
public class KnowledgeIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

    private final TravelDocumentLoader documentLoader;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    public KnowledgeIngestService(TravelDocumentLoader documentLoader,
                                  EmbeddingModel embeddingModel,
                                  EmbeddingStore<TextSegment> embeddingStore) {
        this.documentLoader = documentLoader;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 灌入文档到向量库。
     *
     * @return 灌入的文本片段数
     */
    public int ingest(String filePath) {
        try {
            List<TextSegment> segments = documentLoader.loadAndSplit(filePath);
            // 逐段向量化并存储；生产环境应改为批量 embedding 以减少 API 调用
            for (TextSegment segment : segments) {
                var embedding = embeddingModel.embed(segment).content();
                embeddingStore.add(embedding, segment);
            }
            log.info("知识库灌入完成：{} 个片段", segments.size());
            return segments.size();
        } catch (IOException e) {
            // 基础设施异常转业务异常，避免向上泄漏 IOException
            throw new BizException(5001, "知识库文档加载失败：" + e.getMessage());
        }
    }
}
