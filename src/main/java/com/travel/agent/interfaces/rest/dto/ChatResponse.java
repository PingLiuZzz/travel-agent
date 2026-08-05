package com.travel.agent.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 对话响应 DTO。 */
@Data
@AllArgsConstructor
public class ChatResponse {

  /** 智能体回复内容 */
  private String reply;
}
