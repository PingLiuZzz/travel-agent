package com.travel.agent.application.session;

/** chat() 返回值：回复内容 + 会话标识。 */
public record ChatResult(String reply, String sessionId) {}
