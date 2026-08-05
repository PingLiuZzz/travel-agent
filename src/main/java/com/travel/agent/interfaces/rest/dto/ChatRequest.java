package com.travel.agent.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 对话请求 DTO。userId 可空：为空表示新建会话。 */
@Data
public class ChatRequest {

  /** 会话标识，用于多用户记忆隔离；为空则后端新建会话 */
  private String userId;

  /** 用户消息内容 */
  @NotBlank(message = "message 不能为空")
  private String message;
}
