package com.travel.agent.config;

import com.travel.agent.infrastructure.persistence.PersistentChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 *
 * <p>按 userId 维护独立 Memory；注入 PersistentChatMemoryStore 使上下文落 MySQL，重启可恢复。
 */
@Configuration
public class ChatMemoryConfig {

  private final PersistentChatMemoryStore chatMemoryStore;

  @Autowired
  public ChatMemoryConfig(PersistentChatMemoryStore chatMemoryStore) {
    this.chatMemoryStore = chatMemoryStore;
  }

  @Bean
  public ChatMemoryProvider chatMemoryProvider(TravelAiProperties properties) {
    int maxMessages = properties.getMemory().getMaxMessages();
    return memoryId ->
        MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(maxMessages)
            .chatMemoryStore(chatMemoryStore)
            .build();
  }
}
