package com.travel.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import com.travel.agent.infrastructure.persistence.mapper.ChatMessageMapper;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** 消息持久化封装。 */
@Repository
public class ChatMessageRepository {

  private final ChatMessageMapper mapper;

  @Autowired
  public ChatMessageRepository(ChatMessageMapper mapper) {
    this.mapper = mapper;
  }

  /** 最近 n 条，按时间正序返回（旧→新），供 LLM 上下文恢复。 */
  public List<ChatMessageEntity> findRecentByUserId(String userId, int n) {
    LambdaQueryWrapper<ChatMessageEntity> wrapper =
        new LambdaQueryWrapper<ChatMessageEntity>()
            .eq(ChatMessageEntity::getUserId, userId)
            .orderByDesc(ChatMessageEntity::getId)
            .last("LIMIT " + n);
    // copy-before-reverse：避免原地修改 mapper 返回值（MyBatis 返新 ArrayList 但非契约）
    List<ChatMessageEntity> desc = new java.util.ArrayList<>(mapper.selectList(wrapper));
    Collections.reverse(desc);
    return desc;
  }

  /** 全部消息，正序，供历史接口。 */
  public List<ChatMessageEntity> findAllByUserId(String userId) {
    return mapper.selectList(
        new LambdaQueryWrapper<ChatMessageEntity>()
            .eq(ChatMessageEntity::getUserId, userId)
            .orderByAsc(ChatMessageEntity::getId));
  }

  public void appendMessage(String userId, String role, String content) {
    ChatMessageEntity entity = new ChatMessageEntity();
    entity.setUserId(userId);
    entity.setRole(role);
    entity.setContent(content);
    mapper.insert(entity);
  }

  public void deleteByUserId(String userId) {
    mapper.delete(
        new LambdaQueryWrapper<ChatMessageEntity>().eq(ChatMessageEntity::getUserId, userId));
  }
}
