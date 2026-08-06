package com.travel.agent.infrastructure.rag;

import com.travel.agent.common.exception.BizException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 文档加载与切分。
 *
 * <p>使用 Apache Tika 自动识别 PDF / Word / 纯文本 / Markdown / HTML 等格式提取正文， 再递归切分为文本块（每块约 500 字符，重叠 50
 * 字符，避免语义被硬切断）。
 */
@Component
public class TravelDocumentLoader {

  /**
   * 解析输入流并切分为文本片段。
   *
   * @param inputStream 文档输入流（由调用方负责来源，这里负责安全关闭）
   * @param fileName 文档名（仅用于异常提示）
   * @return 文本片段列表
   */
  public List<TextSegment> loadAndSplit(InputStream inputStream, String fileName) {
    try (InputStream stream = inputStream) {
      // Tika 按内容自动嗅探格式，无需按扩展名分支
      Document document = new ApacheTikaDocumentParser().parse(stream);
      return DocumentSplitters.recursive(500, 50).split(document);
    } catch (Exception e) {
      // 文档解析属外部输入边界：损坏/不支持的格式都应转成友好业务提示，而非泄露堆栈
      throw new BizException(5002, "知识库文档解析失败：" + fileName + " — " + e.getMessage());
    }
  }
}
