package com.travel.agent.application.session;

import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 会话标题 AI 总结（对标 DeepSeek 网页端：首条消息后自动生成主题）。
 *
 * <p>异步执行，失败仅记日志，不影响主对话。
 */
@Component
public class SessionTitleSummarizer {

  private static final Logger log = LoggerFactory.getLogger(SessionTitleSummarizer.class);

  private final ChatModel chatModel;
  private final ChatSessionRepository sessionRepo;

  @Autowired
  public SessionTitleSummarizer(ChatModel chatModel, ChatSessionRepository sessionRepo) {
    this.chatModel = chatModel;
    this.sessionRepo = sessionRepo;
  }

  @Async
  public void summarize(String userId, String firstUserMessage) {
    try {
      String prompt = "请用不超过 10 个字概括以下用户提问的主题，只输出标题文本，不要标点和引号：\n" + firstUserMessage;
      String title = chatModel.chat(prompt).trim();
      if (!title.isEmpty()) {
        sessionRepo.renameTitle(userId, title);
        log.info("session-title-updated userId={} title={}", userId, title);
      }
    } catch (Exception e) {
      log.warn("标题总结失败 userId={}", userId, e);
    }
  }
}
