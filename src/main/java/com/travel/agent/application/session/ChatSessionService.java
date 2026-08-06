package com.travel.agent.application.session;

import com.travel.agent.application.agent.TravelAgent;
import com.travel.agent.infrastructure.persistence.ChatMessageRepository;
import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import com.travel.agent.infrastructure.persistence.entity.ChatSessionEntity;
import com.travel.agent.interfaces.rest.dto.ChatMessageVo;
import com.travel.agent.interfaces.rest.dto.ChatSessionVo;
import com.travel.agent.interfaces.rest.dto.ChatStreamError;
import com.travel.agent.interfaces.rest.dto.ChatStreamToken;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话应用服务。
 *
 * <p>职责：调 Agent、按条落库全量历史、首条消息建会话并触发 AI 标题。 userId 为空则后端生成（对标 DeepSeek：首条消息才落库）。
 *
 * <p>流式（{@link #streamChat}）：逐 token 通过 SseEmitter 下发，用户消息即时落库、助手回复在流结束时落库； 停止/断连保留已生成部分。
 */
@Service
public class ChatSessionService {

  private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

  /** 时间格式化（VO 展示用）。 */
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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

  /**
   * 流式处理一次对话：逐 token 通过 SseEmitter 下发，流结束（正常完成或异常）时落库。
   *
   * <p>对标 DeepSeek：用户消息即时落库；助手回复在完成/异常时落库。
   *
   * <p>停止/断连：客户端断开后下一次 emitter.send 抛 IOException，在 onPartialResponse 里置位并转为 unchecked 异常向上传播，
   * 促使上游停止生成；已累积的部分文本仍随 onError 落库。
   */
  public void streamChat(String userId, String message, SseEmitter emitter) {
    String sessionId = (userId == null || userId.isBlank()) ? UUID.randomUUID().toString() : userId;
    log.info("stream-start sessionId={} message={}", sessionId, message);

    // 用户消息即时落库；upsertMeta 顺便建会话并判定是否新会话
    messageRepo.appendMessage(sessionId, "user", message);
    boolean isNew = sessionRepo.upsertMeta(sessionId, "新对话", message);

    StringBuilder accumulated = new StringBuilder();
    AtomicBoolean stopped = new AtomicBoolean(false);

    travelAgent
        .chatStream(sessionId, message)
        .onPartialResponse(
            token -> {
              accumulated.append(token);
              if (stopped.get()) {
                return;
              }
              try {
                emitter.send(
                    SseEmitter.event()
                        .name("token")
                        .data(new ChatStreamToken(token), MediaType.APPLICATION_JSON));
              } catch (IOException e) {
                // 客户端断开（含用户主动停止）：置位并抛出，传播以停止上游生成
                stopped.set(true);
                throw new IllegalStateException("client disconnected", e);
              }
            })
        .onCompleteResponse(
            response -> {
              String reply = accumulated.toString();
              messageRepo.appendMessage(sessionId, "assistant", reply);
              sessionRepo.upsertMeta(sessionId, "新对话", reply);
              sendDone(emitter);
              emitter.complete();
              log.info("stream-complete sessionId={} replyLen={}", sessionId, reply.length());
              if (isNew) {
                // 新会话异步生成标题，避免占用流式线程
                CompletableFuture.runAsync(() -> titleSummarizer.summarize(sessionId, message));
              }
            })
        .onError(
            err -> {
              // 保留已生成的部分文本（用户停止 / 上游异常均走此分支）
              String partial = accumulated.toString();
              if (!partial.isBlank()) {
                messageRepo.appendMessage(sessionId, "assistant", partial);
                sessionRepo.upsertMeta(sessionId, "新对话", partial);
              }
              sendError(emitter, reasonOf(err));
              emitter.complete();
              log.warn("stream-error sessionId={} reason={}", sessionId, err.toString());
            })
        .start();
  }

  /** 发送结束事件（best-effort：客户端可能已断开）。 */
  private static void sendDone(SseEmitter emitter) {
    try {
      emitter.send(SseEmitter.event().name("done").data("{}", MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      // 客户端已断开，忽略
    }
  }

  /** 发送错误事件（best-effort）。 */
  private static void sendError(SseEmitter emitter, String message) {
    try {
      emitter.send(
          SseEmitter.event()
              .name("error")
              .data(new ChatStreamError(message), MediaType.APPLICATION_JSON));
    } catch (IOException e) {
      // 客户端已断开，忽略
    }
  }

  /** 取异常的可读原因，兜底类名。 */
  private static String reasonOf(Throwable err) {
    String msg = err.getMessage();
    return (msg == null || msg.isBlank()) ? err.getClass().getSimpleName() : msg;
  }

  /** 列出所有会话（按 updateTime 倒序）。 */
  public List<ChatSessionVo> listSessions() {
    return sessionRepo.listAll().stream().map(this::toSessionVo).toList();
  }

  /** 列出某会话的全部消息（按 id 正序）。 */
  public List<ChatMessageVo> listMessages(String userId) {
    return messageRepo.findAllByUserId(userId).stream().map(this::toMessageVo).toList();
  }

  /** 重命名会话标题。 */
  public void renameSession(String userId, String title) {
    sessionRepo.renameTitle(userId, title);
  }

  /** 删除会话（连同消息）。 */
  public void deleteSession(String userId) {
    messageRepo.deleteByUserId(userId);
    sessionRepo.deleteByUserId(userId);
  }

  private ChatSessionVo toSessionVo(ChatSessionEntity e) {
    return new ChatSessionVo(
        e.getUserId(),
        e.getTitle(),
        e.getLastMessage(),
        e.getCreateTime() == null ? null : e.getCreateTime().format(FMT),
        e.getUpdateTime() == null ? null : e.getUpdateTime().format(FMT));
  }

  private ChatMessageVo toMessageVo(ChatMessageEntity e) {
    return new ChatMessageVo(
        e.getId(),
        e.getRole(),
        e.getContent(),
        e.getCreateTime() == null ? null : e.getCreateTime().format(FMT));
  }
}
