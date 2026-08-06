package com.travel.agent.application.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

/**
 * 旅游出行智能体接口（由 AiServices 在运行时动态实现）。
 *
 * <p>开发者只需声明接口，框架负责：上下文记忆管理、工具自主调用、知识库检索。 通过 {@link MemoryId} 绑定 userId 实现多用户会话隔离。
 */
public interface TravelAgent {

  /**
   * 与智能体对话（整段返回）。
   *
   * @param userId 会话标识（用于多用户记忆隔离）
   * @param message 用户输入
   * @return 智能体回复（可能包含工具调用结果的综合输出）
   */
  String chat(@MemoryId String userId, @UserMessage String message);

  /**
   * 与智能体流式对话：逐 token 返回，前端可做打字机效果。
   *
   * <p>由 AiServices 在运行时实现：需在 builder 注入 {@code streamingChatModel}。 memory / tools / RAG 与 {@link
   * #chat} 共享同一套装配，仅模型换为流式。
   *
   * @param userId 会话标识（用于多用户记忆隔离）
   * @param message 用户输入
   * @return 流式 token 流，调用方注册 onPartialResponse 等回调后调用 {@link TokenStream#start()}
   */
  TokenStream chatStream(@MemoryId String userId, @UserMessage String message);
}
