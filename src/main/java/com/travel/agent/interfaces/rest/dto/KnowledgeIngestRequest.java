package com.travel.agent.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 知识库灌入请求 DTO（M3 RAG 阶段使用）。
 */
@Data
public class KnowledgeIngestRequest {

    /** 待灌入文档的路径（本地文件路径，后续可扩展支持上传） */
    @NotBlank(message = "filePath 不能为空")
    private String filePath;
}
