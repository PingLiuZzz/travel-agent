package com.travel.agent.interfaces.rest;

import com.travel.agent.common.result.ApiResult;
import com.travel.agent.infrastructure.rag.KnowledgeIngestService;
import com.travel.agent.interfaces.rest.dto.KnowledgeIngestRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理接口（M3 RAG 阶段使用）。
 *
 * <p>提供文档灌入入口，将旅游指南/景点介绍向量化后存入 Milvus。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

  private final KnowledgeIngestService knowledgeIngestService;

  @Autowired
  public KnowledgeController(KnowledgeIngestService knowledgeIngestService) {
    this.knowledgeIngestService = knowledgeIngestService;
  }

  /**
   * 灌入知识库：解析文档 → 切分 → 向量化 → 存入 Milvus。
   *
   * @return 灌入的文本片段数
   */
  @PostMapping("/ingest")
  public ApiResult<String> ingest(@Valid @RequestBody KnowledgeIngestRequest request) {
    int segmentCount = knowledgeIngestService.ingest(request.getFilePath());
    return ApiResult.success("已灌入 " + segmentCount + " 个文本片段");
  }
}
