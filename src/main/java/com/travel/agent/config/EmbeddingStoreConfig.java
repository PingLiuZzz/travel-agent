package com.travel.agent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量存储与检索器装配。
 *
 * <p>一期采用 InMemoryEmbeddingStore（进程内内存向量库，零外部依赖），
 * 便于无 Docker 环境快速跑通 RAG 端到端流程。
 *
 * <p>二期切换 Milvus：仅需将 embeddingStore() 实现替换为
 * MilvusEmbeddingStore.builder().host().port().collectionName().dimension().build()，
 * 其余代码（检索器、灌入服务）无需改动——依赖 EmbeddingStore 抽象。
 */
@Configuration
public class EmbeddingStoreConfig {

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public EmbeddingStoreContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel,
            TravelAiProperties properties) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(properties.getRag().getTopK())
                .build();
    }
}
