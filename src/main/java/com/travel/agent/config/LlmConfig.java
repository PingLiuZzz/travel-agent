package com.travel.agent.config;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 大模型装配。
 *
 * <p>显式装配 ChatModel 与 EmbeddingModel，DeepSeek/Qwen 等国内模型均兼容 OpenAI 协议，
 * 切换只需改 application.yml 的 base-url / model-name / api-key。
 *
 * <p>M1 阶段：先保证基础对话可用。
 */
@Configuration
public class LlmConfig {

    /**
     * 对话大模型：负责理解意图、调用工具、生成行程。
     */
    @Bean
    public OpenAiChatModel chatModel(TravelAiProperties properties) {
        TravelAiProperties.Llm llm = properties.getLlm();
        return OpenAiChatModel.builder()
                .baseUrl(llm.getBaseUrl())
                .apiKey(llm.getApiKey())
                .modelName(llm.getModelName())
                .temperature(llm.getTemperature())
                .maxTokens(llm.getMaxTokens())
                .logRequests(true)
                .logResponses(false)
                .build();
    }

    /**
     * 向量化模型：RAG 阶段把文档/查询转为向量。
     *
     * <p>DeepSeek 暂无 embedding 接口，默认走 千问 的 qwen3.7-text-embedding向量模型。
     */
    @Bean
    public OpenAiEmbeddingModel embeddingModel(TravelAiProperties properties) {
        TravelAiProperties.Embedding emb = properties.getEmbedding();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(emb.getBaseUrl())
                .apiKey(emb.getApiKey())
                .modelName(emb.getModelName())
                .build();
    }
}
