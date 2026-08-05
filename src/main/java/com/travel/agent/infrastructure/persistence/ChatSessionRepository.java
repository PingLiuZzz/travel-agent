package com.travel.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.infrastructure.persistence.entity.ChatSessionEntity;
import com.travel.agent.infrastructure.persistence.mapper.ChatSessionMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** 会话持久化封装。upsertMeta 返回是否为新建。 */
@Repository
public class ChatSessionRepository {

  private final ChatSessionMapper mapper;

  @Autowired
  public ChatSessionRepository(ChatSessionMapper mapper) {
    this.mapper = mapper;
  }

  public boolean upsertMeta(String userId, String title, String lastMessage) {
    ChatSessionEntity existing = mapper.selectById(userId);
    if (existing == null) {
      ChatSessionEntity entity = new ChatSessionEntity();
      entity.setUserId(userId);
      entity.setTitle(title);
      entity.setLastMessage(lastMessage);
      mapper.insert(entity);
      return true;
    }
    existing.setLastMessage(lastMessage);
    mapper.updateById(existing);
    return false;
  }

  public List<ChatSessionEntity> listAll() {
    return mapper.selectList(
        new LambdaQueryWrapper<ChatSessionEntity>().orderByDesc(ChatSessionEntity::getUpdateTime));
  }

  public void renameTitle(String userId, String title) {
    ChatSessionEntity entity = mapper.selectById(userId);
    if (entity != null) {
      entity.setTitle(title);
      mapper.updateById(entity);
    }
  }

  public void deleteByUserId(String userId) {
    mapper.deleteById(userId);
  }
}
