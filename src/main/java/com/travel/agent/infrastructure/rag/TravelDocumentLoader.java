package com.travel.agent.infrastructure.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文档加载与切分。
 *
 * <p>M3 RAG 阶段：读取文档 → 递归切分为文本块。
 * 切分策略：每块约 500 字符，重叠 50 字符（避免语义被硬切断）。
 *
 * <p>一期占位：仅读取纯文本。正式版改用 ApacheTikaDocumentParser 支持 PDF/Word。
 */
@Component
public class TravelDocumentLoader {

    /**
     * 加载文档并切分为文本片段。
     *
     * @param filePath 文档路径
     * @return 文本片段列表
     */
    // TODO[接入 Tika]：改用 ApacheTikaDocumentParser 解析 PDF/Word，替换 Files.readString
    public List<TextSegment> loadAndSplit(String filePath) throws IOException {
        String text = Files.readString(Path.of(filePath));
        Document document = Document.from(text);
        return DocumentSplitters.recursive(500, 50).split(document);
    }
}
