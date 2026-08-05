package com.travel.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 相关配置集中入口（前缀 travel）。
 *
 * <p>用嵌套静态类分组，避免散落的 @Value；一处看清所有可调参数。
 */
@Data
@ConfigurationProperties(prefix = "travel")
public class TravelAiProperties {

    /** 大语言模型配置 */
    private Llm llm = new Llm();

    /** 向量化模型配置 */
    private Embedding embedding = new Embedding();

    /** 会话记忆配置 */
    private Memory memory = new Memory();

    /** RAG 检索配置 */
    private Rag rag = new Rag();

    @Data
    public static class Llm {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private double temperature = 0.7;
        private int maxTokens = 2048;
    }

    @Data
    public static class Embedding {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private int dimension = 1024;
    }

    @Data
    public static class Memory {
        /** 单会话最大保留消息数（滑窗策略，超出后丢弃最早消息） */
        private int maxMessages = 20;
    }

    @Data
    public static class Rag {
        /** 检索召回条数 */
        private int topK = 3;
    }
}
