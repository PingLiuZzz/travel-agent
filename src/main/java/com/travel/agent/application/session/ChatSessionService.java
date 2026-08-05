package com.travel.agent.application.session;

import com.travel.agent.application.agent.TravelAgent;
import com.travel.agent.infrastructure.persistence.ChatMessageRepository;
import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 对话应用服务。
 *
 * <p>职责：调 Agent、按条落库全量历史、首条消息建会话并触发 AI 标题。 userId 为空则后端生成（对标 DeepSeek：首条消息才落库）。
 */
@Service
public class ChatSessionService {

  private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

  private final TravelAgent travelAgent;
  private final ChatMessageRepository messageRepo;
  private final ChatSessionRepository sessionRepo;
  private final SessionTitleSummarizer titleSummarizer;

  @Autowired
  public ChatSessionService(
      TravelAgent travelAgent,
      ChatMessageRepository messageRepo,
      ChatSessionRepository sessionRepo,
      SessionTitleSummarizer titleSummarizer) {
    this.travelAgent = travelAgent;
    this.messageRepo = messageRepo;
    this.sessionRepo = sessionRepo;
    this.titleSummarizer = titleSummarizer;
  }

  /** 处理一次对话；userId 为空表示新建会话。 */
  public ChatResult chat(String userId, String message) {
    String sessionId = (userId == null || userId.isBlank()) ? UUID.randomUUID().toString() : userId;
    log.info("user-input sessionId={} message={}", sessionId, message);

    String reply = travelAgent.chat(sessionId, message);

    // 全量历史按条落库（不受滑窗截断）
    messageRepo.appendMessage(sessionId, "user", message);
    messageRepo.appendMessage(sessionId, "assistant", reply);

    boolean isNew = sessionRepo.upsertMeta(sessionId, "新对话", reply);
    if (isNew) {
      titleSummarizer.summarize(sessionId, message);
    }
    log.info("agent-reply sessionId={} reply={}", sessionId, reply);
    return new ChatResult(reply, sessionId);
  }
}
