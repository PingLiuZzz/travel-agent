package com.travel.agent.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 重命名会话请求。 */
@Data
public class RenameSessionRequest {
  @NotBlank(message = "title 不能为空")
  private String title;
}
