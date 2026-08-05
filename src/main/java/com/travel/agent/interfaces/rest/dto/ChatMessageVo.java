package com.travel.agent.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 消息视图（历史记录）。 */
@Data
@AllArgsConstructor
public class ChatMessageVo {
  private Long id;
  private String role;
  private String content;
  private String createTime;
}
