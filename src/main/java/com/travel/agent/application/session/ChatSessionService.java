package com.travel.agent.application.session;

import com.travel.agent.application.agent.TravelAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 对话应用服务。
 *
 * <p>应用层封装领域编排：调用 Agent、记录交互日志（M6 可观测性的前置埋点）。
 * Controller 只面向应用服务，不直接持有 Agent。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final TravelAgent travelAgent;

    @Autowired
    public ChatSessionService(TravelAgent travelAgent) {
        this.travelAgent = travelAgent;
    }

    /**
     * 处理一次用户对话。
     */
    public String chat(String userId, String message) {
        // 记录用户输入；M6 阶段可扩展为结构化 Thought/Action/Observation 日志
        log.info("user-input userId={} message={}", userId, message);
        String reply = travelAgent.chat(userId, message);
        log.info("agent-reply userId={} reply={}", userId, reply);
        return reply;
    }
}
