package com.travel.agent.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel.agent.infrastructure.persistence.entity.ChatSessionEntity;
import com.travel.agent.infrastructure.persistence.mapper.ChatSessionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ChatSessionRepository 单元测试：验证 upsert 新建/更新分支、rename 存在/不存在分支。 */
@ExtendWith(MockitoExtension.class)
class ChatSessionRepositoryTest {

  @Mock private ChatSessionMapper mapper;

  @InjectMocks private ChatSessionRepository repository;

  @Test
  void upsertMeta_不存在则新建返回true() {
    when(mapper.selectById("u1")).thenReturn(null);

    boolean created = repository.upsertMeta("u1", "新对话", "你好");

    assertThat(created).isTrue();
    verify(mapper).insert(any(ChatSessionEntity.class));
    // any() 返回 null，BaseMapper 的 updateById(T)/updateById(Collection) 重载均匹配 → 必须指定类型令牌
    verify(mapper, never()).updateById(any(ChatSessionEntity.class));
  }

  @Test
  void upsertMeta_存在则更新lastMessage返回false() {
    ChatSessionEntity existing = new ChatSessionEntity();
    existing.setUserId("u1");
    existing.setTitle("旧标题");
    when(mapper.selectById("u1")).thenReturn(existing);

    boolean created = repository.upsertMeta("u1", "旧标题", "新消息");

    assertThat(created).isFalse();
    assertThat(existing.getLastMessage()).isEqualTo("新消息");
    verify(mapper).updateById(existing);
    verify(mapper, never()).insert(any(ChatSessionEntity.class));
  }

  @Test
  void renameTitle_存在则更新标题() {
    ChatSessionEntity existing = new ChatSessionEntity();
    when(mapper.selectById("u1")).thenReturn(existing);

    repository.renameTitle("u1", "新标题");

    assertThat(existing.getTitle()).isEqualTo("新标题");
    verify(mapper).updateById(existing);
  }

  @Test
  void renameTitle_不存在则不操作() {
    when(mapper.selectById("u1")).thenReturn(null);

    repository.renameTitle("u1", "新标题");

    verify(mapper, never()).updateById(any(ChatSessionEntity.class));
  }

  @Test
  void deleteByUserId_委托Mapper删除() {
    repository.deleteByUserId("u1");
    verify(mapper).deleteById("u1");
  }
}
