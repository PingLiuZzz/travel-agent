package com.travel.agent.application.agent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

/**
 * 旅游出行智能体接口（由 AiServices 在运行时动态实现）。
 *
 * <p>开发者只需声明接口，框架负责：上下文记忆管理、工具自主调用、知识库检索。
 * 通过 {@link MemoryId} 绑定 userId 实现多用户会话隔离。
 */
public interface TravelAgent {

    /**
     * 与智能体对话。
     *
     * @param userId  会话标识（用于多用户记忆隔离）
     * @param message 用户输入
     * @return 智能体回复（可能包含工具调用结果的综合输出）
     */
    String chat(@MemoryId String userId, @UserMessage String message);
}
