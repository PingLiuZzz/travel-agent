package com.travel.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 消息实体。id 自增。 */
@Data
@TableName("chat_message")
public class ChatMessageEntity {
  @TableId(type = IdType.AUTO)
  private Long id;

  private String userId;
  private String role;
  private String content;
  private LocalDateTime createTime;
}
