package com.travel.agent.config;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 *
 * <p>关键设计：按 userId 维护独立 Memory（多用户隔离），避免上下文串台。 采用 MessageWindowChatMemory 滑窗策略，防止上下文无限增长撑爆 token。
 *
 * <p>M2 阶段：解决"上下文失忆"问题。
 */
@Configuration
public class ChatMemoryConfig {

  @Bean
  public ChatMemoryProvider chatMemoryProvider(TravelAiProperties properties) {
    int maxMessages = properties.getMemory().getMaxMessages();
    // ChatMemoryProvider 为函数式接口，每个 memoryId（即 userId）对应独立滑窗记忆
    return memoryId ->
        MessageWindowChatMemory.builder().id(memoryId).maxMessages(maxMessages).build();
  }
}
