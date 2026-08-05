package com.travel.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 会话实体。user_id 由应用生成（非自增），故 IdType.INPUT。 */
@Data
@TableName("chat_session")
public class ChatSessionEntity {
  @TableId(type = IdType.INPUT)
  private String userId;

  private String title;
  private String lastMessage;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;
}
