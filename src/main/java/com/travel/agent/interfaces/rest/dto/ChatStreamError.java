package com.travel.agent.interfaces.rest.dto;

/** SSE 流式对话的错误事件载荷（对应前端 {@code event: error} 的 data）。 */
public record ChatStreamError(String message) {}
