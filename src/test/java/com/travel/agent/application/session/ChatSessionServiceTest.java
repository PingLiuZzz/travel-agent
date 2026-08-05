package com.travel.agent.application.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.travel.agent.application.agent.TravelAgent;
import com.travel.agent.infrastructure.persistence.ChatMessageRepository;
import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

  @Mock TravelAgent agent;
  @Mock ChatMessageRepository messageRepo;
  @Mock ChatSessionRepository sessionRepo;
  @Mock SessionTitleSummarizer summarizer;

  @Test
  void chat_newSession_appendsBothMessages_triggersTitle_returnsNewId() {
    when(agent.chat(anyString(), eq("去东京"))).thenReturn("好的");
    when(sessionRepo.upsertMeta(anyString(), eq("新对话"), eq("好的"))).thenReturn(true);
    ChatSessionService svc = new ChatSessionService(agent, messageRepo, sessionRepo, summarizer);

    ChatResult result = svc.chat(null, "去东京");

    assertThat(result.sessionId()).isNotBlank();
    assertThat(result.reply()).isEqualTo("好的");
    verify(messageRepo).appendMessage(result.sessionId(), "user", "去东京");
    verify(messageRepo).appendMessage(result.sessionId(), "assistant", "好的");
    verify(summarizer).summarize(result.sessionId(), "去东京");
  }

  @Test
  void chat_existingSession_doesNotTriggerTitle() {
    when(agent.chat("u1", "继续")).thenReturn("ok");
    when(sessionRepo.upsertMeta("u1", "新对话", "ok")).thenReturn(false);
    ChatSessionService svc = new ChatSessionService(agent, messageRepo, sessionRepo, summarizer);

    ChatResult result = svc.chat("u1", "继续");

    assertThat(result.sessionId()).isEqualTo("u1");
    verify(summarizer, never()).summarize(anyString(), anyString());
  }
}
