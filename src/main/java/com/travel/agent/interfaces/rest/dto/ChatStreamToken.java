package com.travel.agent.interfaces.rest.dto;

/** SSE 流式对话的增量 token 事件载荷（对应前端 {@code event: token} 的 data）。 */
public record ChatStreamToken(String delta) {}
