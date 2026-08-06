package com.travel.agent.domain.knowledge;

/**
 * 知识库文档记录（不可变值对象）。
 *
 * <p>一期无 DB 持久化，文档清单与向量库同驻内存，故该 record 直接兼作 API 返回 VO， 不再额外定义一层 VO 做无意义的字段拷贝。
 */
public record KnowledgeDocument(
    String id,
    String fileName,
    long fileSize,
    int segmentCount,
    String contentType,
    String ingestTime) {}
