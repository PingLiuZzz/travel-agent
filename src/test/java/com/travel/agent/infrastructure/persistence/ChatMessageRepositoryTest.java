package com.travel.agent.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import com.travel.agent.infrastructure.persistence.mapper.ChatMessageMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** ChatMessageRepository 单元测试：验证最近 N 条倒序→正序反转、append/delete 委托。 */
@ExtendWith(MockitoExtension.class)
class ChatMessageRepositoryTest {

  @Mock private ChatMessageMapper mapper;

  @InjectMocks private ChatMessageRepository repository;

  @Test
  void findRecentByUserId_倒序结果反转为正序() {
    // mapper 按 id desc 返回最近 2 条（m3 在前）。生产环境 MyBatis 返回可变 ArrayList，
    // 故此处用 ArrayList 而非 List.of（后者不可变，会让 Collections.reverse 抛异常）
    ChatMessageEntity m3 = entity("m3");
    ChatMessageEntity m2 = entity("m2");
    when(mapper.selectList(any())).thenReturn(new ArrayList<>(List.of(m3, m2)));

    List<ChatMessageEntity> recent = repository.findRecentByUserId("u1", 2);

    // reverse 后应为时间正序：m2, m3
    assertThat(recent).extracting(ChatMessageEntity::getContent).containsExactly("m2", "m3");
  }

  @Test
  void appendMessage_构造并插入实体() {
    repository.appendMessage("u1", "user", "你好");

    ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
    verify(mapper).insert(captor.capture());
    ChatMessageEntity saved = captor.getValue();
    assertThat(saved.getUserId()).isEqualTo("u1");
    assertThat(saved.getRole()).isEqualTo("user");
    assertThat(saved.getContent()).isEqualTo("你好");
  }

  @Test
  void deleteByUserId_委托Mapper删除() {
    repository.deleteByUserId("u1");
    verify(mapper).delete(any());
  }

  private ChatMessageEntity entity(String content) {
    ChatMessageEntity e = new ChatMessageEntity();
    e.setContent(content);
    return e;
  }
}
