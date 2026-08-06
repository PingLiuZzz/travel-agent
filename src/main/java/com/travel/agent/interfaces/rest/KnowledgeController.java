package com.travel.agent.interfaces.rest;

import com.travel.agent.common.result.ApiResult;
import com.travel.agent.domain.knowledge.KnowledgeDocument;
import com.travel.agent.infrastructure.rag.KnowledgeService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理接口。
 *
 * <p>提供文档上传灌入、文档清单查询、按文档删除三个能力， 供前端知识库管理页调用。灌入后即可被 Agent 的 ContentRetriever 自动检索召回。
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

  private final KnowledgeService knowledgeService;

  @Autowired
  public KnowledgeController(KnowledgeService knowledgeService) {
    this.knowledgeService = knowledgeService;
  }

  /** 上传文档并灌入向量库（multipart）。返回灌入后的文档元数据。 */
  @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResult<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file) {
    return ApiResult.success(knowledgeService.upload(file));
  }

  /** 列出全部已灌入文档（按灌入时间倒序）。 */
  @GetMapping("/documents")
  public ApiResult<List<KnowledgeDocument>> list() {
    return ApiResult.success(knowledgeService.list());
  }

  /** 删除指定文档及其向量片段。 */
  @DeleteMapping("/documents/{documentId}")
  public ApiResult<Void> delete(@PathVariable String documentId) {
    knowledgeService.delete(documentId);
    return ApiResult.success(null);
  }
}
