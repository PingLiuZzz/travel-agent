package com.travel.agent.interfaces.rest;

import com.travel.agent.application.session.ChatResult;
import com.travel.agent.application.session.ChatSessionService;
import com.travel.agent.common.result.ApiResult;
import com.travel.agent.interfaces.rest.dto.ChatMessageVo;
import com.travel.agent.interfaces.rest.dto.ChatRequest;
import com.travel.agent.interfaces.rest.dto.ChatResponse;
import com.travel.agent.interfaces.rest.dto.ChatSessionVo;
import com.travel.agent.interfaces.rest.dto.RenameSessionRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 对话入口接口。
 *
 * <p>Web/小程序前端统一通过该接口与智能体交互。 支持多会话：userId 为空时新建，会话与消息均落 MySQL。
 *
 * <p>提供同步 {@code POST /api/chat} 与流式 {@code POST /api/chat/stream} 两套对话入口； 前者整段返回（兜底），后者逐 token
 * 下发（打字机效果，主用）。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

  private static final Logger log = LoggerFactory.getLogger(ChatController.class);

  private final ChatSessionService chatSessionService;

  @Autowired
  public ChatController(ChatSessionService chatSessionService) {
    this.chatSessionService = chatSessionService;
  }

  @PostMapping
  public ApiResult<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
    ChatResult result = chatSessionService.chat(request.getUserId(), request.getMessage());
    return ApiResult.success(new ChatResponse(result.reply(), result.sessionId()));
  }

  /**
   * 流式对话：返回 SSE 流。事件约定：
   *
   * <ul>
   *   <li>{@code event: token} {@code data: {"delta":"…"}} —— 增量文本
   *   <li>{@code event: done} —— 正常结束
   *   <li>{@code event: error} {@code data: {"message":"…"}} —— 异常
   * </ul>
   *
   * <p>客户端断连（含用户主动停止）由 service 层捕获并停止上游生成。
   */
  @PostMapping("/stream")
  public SseEmitter stream(@Valid @RequestBody ChatRequest request) {
    // LLM 生成 + 多轮工具调用可能较久，超时放宽到 5 分钟
    SseEmitter emitter = new SseEmitter(5L * 60 * 1000);
    emitter.onCompletion(() -> log.debug("sse-completed"));
    emitter.onTimeout(
        () -> {
          log.warn("sse-timeout");
          emitter.complete();
        });
    emitter.onError(ex -> log.warn("sse-error", ex));
    chatSessionService.streamChat(request.getUserId(), request.getMessage(), emitter);
    return emitter;
  }

  @GetMapping("/sessions")
  public ApiResult<List<ChatSessionVo>> listSessions() {
    return ApiResult.success(chatSessionService.listSessions());
  }

  @GetMapping("/sessions/{userId}/messages")
  public ApiResult<List<ChatMessageVo>> listMessages(@PathVariable String userId) {
    return ApiResult.success(chatSessionService.listMessages(userId));
  }

  @PatchMapping("/sessions/{userId}")
  public ApiResult<Void> rename(
      @PathVariable String userId, @Valid @RequestBody RenameSessionRequest request) {
    chatSessionService.renameSession(userId, request.getTitle());
    return ApiResult.success(null);
  }

  @DeleteMapping("/sessions/{userId}")
  public ApiResult<Void> delete(@PathVariable String userId) {
    chatSessionService.deleteSession(userId);
    return ApiResult.success(null);
  }
}
