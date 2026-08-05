package com.travel.agent.infrastructure.persistence;

import com.travel.agent.config.TravelAiProperties;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 落库版 ChatMemoryStore。
 *
 * <p>关键设计：
 *
 * <ul>
 *   <li><b>进程内缓存</b>：AiServices 在一次请求内可能多次创建 MessageWindowChatMemory
 *       （特别是触发工具调用循环时），每次 build() 都调 getMessages 初始化。 若仅依赖
 *       DB（updateMessages no-op → DB 无当前轮消息），第 2/3 次 getMessages 返回空，
 *       导致 {@code messages cannot be null or empty}。 进程内缓存使得同一 memoryId 的
 *       后续 getMessages 返回已累积消息（updateMessages 写入缓存）。
 *   <li><b>DB 兜底</b>：重启后缓存为空，getMessages 回退 DB 读最近 maxMessages 条，
 *       用于 LLM 上下文恢复（后端重启不失忆）。
 *   <li><b>不写 DB</b>：全量历史由 ChatSessionService 按条落库，这里不二次写，
 *       避免滑窗截断污染全量历史、避免 user message 重复。
 * </ul>
 */
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

  private final ChatMessageRepository repository;
  private final int maxMessages;

  /** 进程内缓存：AiServices 一次请求内多次 getMessages 返回一致结果 */
  private final ConcurrentMap<Object, List<ChatMessage>> cache = new ConcurrentHashMap<>();

  @Autowired
  public PersistentChatMemoryStore(
      ChatMessageRepository repository, TravelAiProperties properties) {
    this.repository = repository;
    this.maxMessages = properties.getMemory().getMaxMessages();
  }

  @Override
  public List<ChatMessage> getMessages(Object memoryId) {
    // 优先读缓存（同一次请求内后续 MessageWindowChatMemory 实例可获取已累积消息）
    List<ChatMessage> cached = cache.get(memoryId);
    if (cached != null) {
      return new ArrayList<>(cached);
    }
    // 缓存未命中：从 DB 加载（首次对话或重启后恢复）
    String userId = String.valueOf(memoryId);
    List<ChatMessage> fromDb = new ArrayList<>(
        repository.findRecentByUserId(userId, maxMessages).stream()
            .map(PersistentChatMemoryStore::toChatMessage)
            .toList());
    cache.put(memoryId, fromDb);
    return new ArrayList<>(fromDb);
  }

  @Override
  public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    // 写入进程内缓存（不写 DB，见类注释）
    cache.put(memoryId, messages);
  }

  @Override
  public void deleteMessages(Object memoryId) {
    cache.remove(memoryId);
    repository.deleteByUserId(String.valueOf(memoryId));
  }

  private static ChatMessage toChatMessage(ChatMessageEntity entity) {
    if ("assistant".equals(entity.getRole())) {
      return AiMessage.from(entity.getContent());
    }
    return UserMessage.from(entity.getContent());
  }
}
