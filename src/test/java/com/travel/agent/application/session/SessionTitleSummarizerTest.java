package com.travel.agent.application.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionTitleSummarizerTest {

  @Mock ChatModel chatModel;
  @Mock ChatSessionRepository sessionRepo;

  @Test
  void summarize_callsLlm_andUpdatesTitle() {
    when(chatModel.chat(anyString())).thenReturn("东京三日游");
    SessionTitleSummarizer summarizer = new SessionTitleSummarizer(chatModel, sessionRepo);

    summarizer.summarize("u1", "帮我规划东京三日游");

    verify(sessionRepo).renameTitle("u1", "东京三日游");
  }

  @Test
  void summarize_llmFailure_doesNotThrow_doesNotRename() {
    when(chatModel.chat(anyString())).thenThrow(new RuntimeException("api down"));
    SessionTitleSummarizer summarizer = new SessionTitleSummarizer(chatModel, sessionRepo);

    assertThatCode(() -> summarizer.summarize("u1", "x")).doesNotThrowAnyException();

    verify(sessionRepo, never()).renameTitle(anyString(), anyString());
  }
}
