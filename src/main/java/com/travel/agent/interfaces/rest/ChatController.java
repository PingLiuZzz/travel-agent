package com.travel.agent.interfaces.rest;

import com.travel.agent.application.session.ChatSessionService;
import com.travel.agent.common.result.ApiResult;
import com.travel.agent.interfaces.rest.dto.ChatRequest;
import com.travel.agent.interfaces.rest.dto.ChatResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话入口接口。
 *
 * <p>Web/小程序前端统一通过该接口与智能体交互。
 * 用 userId 区分会话，支持多轮上下文。
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
        String reply = chatSessionService.chat(request.getUserId(), request.getMessage());
        return ApiResult.success(new ChatResponse(reply));
    }
}
