package com.travel.agent.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 会话视图（左侧列表）。时间已格式化为字符串。 */
@Data
@AllArgsConstructor
public class ChatSessionVo {
  private String userId;
  private String title;
  private String lastMessage;
  private String createTime;
  private String updateTime;
}
