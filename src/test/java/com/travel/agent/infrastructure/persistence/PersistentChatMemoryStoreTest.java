package com.travel.agent.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.travel.agent.config.TravelAiProperties;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PersistentChatMemoryStoreTest {

  @Mock ChatMessageRepository repository;

  @Test
  void getMessages_returnsRecentInOrder_withCorrectTypes() {
    TravelAiProperties props = new TravelAiProperties(); // 默认 maxMessages=20
    when(repository.findRecentByUserId("u1", 20))
        .thenReturn(List.of(entity("user", "你好"), entity("assistant", "你好，想去哪？")));
    PersistentChatMemoryStore store = new PersistentChatMemoryStore(repository, props);

    List<ChatMessage> messages = store.getMessages("u1");

    assertThat(messages).hasSize(2);
    assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
    assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
    verify(repository).findRecentByUserId("u1", 20);
  }

  @Test
  void updateMessages_isNoOp_doesNotWrite() {
    PersistentChatMemoryStore store =
        new PersistentChatMemoryStore(repository, new TravelAiProperties());

    store.updateMessages("u1", List.of(new UserMessage("x")));

    verifyNoInteractions(repository);
  }

  @Test
  void deleteMessages_delegatesToRepository() {
    PersistentChatMemoryStore store =
        new PersistentChatMemoryStore(repository, new TravelAiProperties());

    store.deleteMessages("u1");

    verify(repository).deleteByUserId("u1");
  }

  private ChatMessageEntity entity(String role, String content) {
    ChatMessageEntity e = new ChatMessageEntity();
    e.setRole(role);
    e.setContent(content);
    return e;
  }
}
