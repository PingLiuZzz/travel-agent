package com.travel.agent.infrastructure.rag;

import com.travel.agent.common.exception.BizException;
import com.travel.agent.domain.knowledge.KnowledgeDocument;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库管理服务。
 *
 * <p>职责：文档上传→解析→切分→批量向量化→入库；列出已灌入文档；按文档删除其向量片段。
 *
 * <p>存储策略（一期）：向量库与文档清单均为内存（InMemoryEmbeddingStore + 进程内注册表）， 重启清空，二者保持一致。二期切 Milvus 时连同迁移——本服务依赖
 * EmbeddingStore 抽象，无需改动。
 */
@Service
public class KnowledgeService {

  private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

  /** 时间格式化（与 ChatSessionService 对齐，前端按此解析）。 */
  private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final TravelDocumentLoader documentLoader;
  private final EmbeddingModel embeddingModel;
  private final EmbeddingStore<TextSegment> embeddingStore;

  /** 内存文档注册表：docId → 文档元数据（与 InMemoryEmbeddingStore 同生命周期）。 */
  private final ConcurrentHashMap<String, KnowledgeDocument> documentRegistry =
      new ConcurrentHashMap<>();

  @Autowired
  public KnowledgeService(
      TravelDocumentLoader documentLoader,
      EmbeddingModel embeddingModel,
      EmbeddingStore<TextSegment> embeddingStore) {
    this.documentLoader = documentLoader;
    this.embeddingModel = embeddingModel;
    this.embeddingStore = embeddingStore;
  }

  /**
   * 上传并灌入文档：解析→切分→批量向量化→带 docId 标签存入向量库。
   *
   * <p>每个片段以 "docId#i" 作为稳定 ID，删除时按此还原 ID 批量移除，无需遍历向量库。
   */
  public KnowledgeDocument upload(MultipartFile file) {
    String fileName = file.getOriginalFilename();
    List<TextSegment> segments;
    try {
      segments = documentLoader.loadAndSplit(file.getInputStream(), fileName);
    } catch (IOException e) {
      // MultipartFile 读取底层临时文件失败时抛出，属外部输入边界
      throw new BizException(5004, "读取上传文件失败：" + e.getMessage());
    }
    if (segments.isEmpty()) {
      throw new BizException(5005, "文档内容为空或无法提取文本：" + fileName);
    }

    // 批量向量化（一次 API 调用，替代旧的逐段 embed）
    List<Embedding> embeddings = embeddingModel.embedAll(segments).content();

    String docId = UUID.randomUUID().toString();
    List<String> ids = new ArrayList<>(segments.size());
    for (int i = 0; i < segments.size(); i++) {
      ids.add(docId + "#" + i);
    }
    embeddingStore.addAll(ids, embeddings, segments);

    KnowledgeDocument document =
        new KnowledgeDocument(
            docId,
            fileName,
            file.getSize(),
            segments.size(),
            file.getContentType(),
            LocalDateTime.now().format(FMT));
    documentRegistry.put(docId, document);
    log.info("知识库灌入完成：{}（{} 个片段）", fileName, segments.size());
    return document;
  }

  /** 列出全部已灌入文档（按灌入时间倒序）。 */
  public List<KnowledgeDocument> list() {
    // ingestTime 为定宽 yyyy-MM-dd HH:mm:ss，字典序倒序即时间倒序
    return documentRegistry.values().stream()
        .sorted(Comparator.comparing(KnowledgeDocument::ingestTime).reversed())
        .toList();
  }

  /** 删除文档：移除其全部向量片段 + 注册表记录。不存在的 docId 视为错误（前端清单是唯一真相源）。 */
  public void delete(String docId) {
    KnowledgeDocument document = documentRegistry.remove(docId);
    if (document == null) {
      throw new BizException(4004, "文档不存在：" + docId);
    }
    // 按 docId#i 还原片段 ID 批量移除
    List<String> ids = new ArrayList<>(document.segmentCount());
    for (int i = 0; i < document.segmentCount(); i++) {
      ids.add(docId + "#" + i);
    }
    embeddingStore.removeAll(ids);
    log.info("知识库文档已删除：{}（{} 个片段）", document.fileName(), document.segmentCount());
  }
}
