package com.travel.agent.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 对话请求 DTO。
 */
@Data
public class ChatRequest {

    /** 会话标识，用于多用户记忆隔离 */
    @NotBlank(message = "userId 不能为空")
    private String userId;

    /** 用户消息内容 */
    @NotBlank(message = "message 不能为空")
    private String message;
}
