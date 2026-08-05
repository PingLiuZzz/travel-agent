package com.travel.agent.infrastructure.persistence;

import com.travel.agent.config.TravelAiProperties;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 落库版 ChatMemoryStore。
 *
 * <p>关键设计：getMessages 只读最近 maxMessages 条用于 LLM 上下文恢复（后端重启不失忆）； updateMessages 为 no-op——全量历史由
 * ChatSessionService 按条落库， 这里不二次写，避免滑窗截断污染全量历史、避免 user message 重复。
 */
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

  private final ChatMessageRepository repository;
  private final int maxMessages;

  @Autowired
  public PersistentChatMemoryStore(
      ChatMessageRepository repository, TravelAiProperties properties) {
    this.repository = repository;
    this.maxMessages = properties.getMemory().getMaxMessages();
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    String userId = String.valueOf(memoryId);
    return new java.util.ArrayList<>(
        repository.findRecentByUserId(userId, maxMessages).stream()
            .map(PersistentChatMemoryStore::toChatMessage)
            .toList());
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    // 故意 no-op，见类注释
  }

  @Override
  public void deleteMessages(Object memoryId) {
    repository.deleteByUserId(String.valueOf(memoryId));
  }

  private static ChatMessage toChatMessage(ChatMessageEntity entity) {
    if ("assistant".equals(entity.getRole())) {
      return AiMessage.from(entity.getContent());
    }
    return UserMessage.from(entity.getContent());
  }
}
