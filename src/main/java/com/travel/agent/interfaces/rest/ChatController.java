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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话入口接口。
 *
 * <p>Web/小程序前端统一通过该接口与智能体交互。 支持多会话：userId 为空时新建，会话与消息均落 MySQL。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

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
