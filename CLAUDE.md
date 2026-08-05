# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

旅游出行智能体（Travel Agent）。后端（Spring Boot 3.4 + LangChain4j，代码在本目录根 `src/`），前端（Vue3 + Ant Design Vue，在 `travel-web/`）。后端用 LangChain4j `AiServices` 把 LLM、工具、记忆、RAG 组装为单一 `TravelAgent` 接口；M1（对话）/ M2（工具）/ M3（RAG）端到端已验证打通。

> **文档结构**：本文件覆盖**项目整体 + 后端**（后端代码在本目录根，故项目级与后端指南合一）。前端开发指南见 [`travel-web/CLAUDE.md`](travel-web/CLAUDE.md)。

## 环境约束（非显而易见，务必遵守）

- **JDK 21**：系统默认 `java` 是 17，但 pom 的 `java.version=21`。所有 mvn 命令前必须设 `$env:JAVA_HOME="E:\File-work\Java_Detail\jdk-21"`（PowerShell），否则编译失败。jdk-21 实际为 21.0.10 LTS。
- **Maven 本地仓库非默认**：`localRepository` 是 `E:\File-work\Java_Detail\maven\repo`（不是 `~/.m2`），镜像走阿里云。排查依赖 / jar 内容时去这个目录找，不要去 `~/.m2`。
- **LangChain4j 版本双轨**（pom 已配，勿统一）：核心与主流集成已 GA `1.17.2`（`langchain4j`、`langchain4j-open-ai`），部分扩展仍 beta `1.17.2-beta27`（`langchain4j-document-parser-apache-tika`）。两套版本号在 pom properties 分别管理。

## 常用命令

### 后端（在 `travel-agent/`）

```powershell
$env:JAVA_HOME = "E:\File-work\Java_Detail\jdk-21"   # 每次执行 mvn 前必设

mvn -q compile                    # 编译
mvn spring-boot:run               # 启动（监听 8080）
mvn spotless:apply                # 格式化（google-java-format，质量门禁）
mvn test                          # 全部单元测试
mvn -Dtest=类名#方法名 test        # 跑单个测试
```

### 前端（在 `travel-agent/travel-web/`）

```bash
npm install
npm run dev          # 启动 dev server（5173）
npm run type-check   # vue-tsc 类型检查（strict）
npm run build        # 生产构建（含类型检查）
```

### 前后端联调

前端 `travel-web/vite.config.ts` 配了 vite proxy：`/api` → `http://localhost:8080`，开发期无需 CORS。先起后端（8080）再起前端（5173）。

## 后端架构（big picture）

DDD 包分层，`com.travel.agent`：

- **`config/`** — 显式 `@Bean` 装配（**刻意不用** starter 的 yml 自动装配，便于排障）。核心是 `AgentConfig`：用 `AiServices.builder()` 把 `chatModel` + `chatMemoryProvider` + `tools` + `contentRetriever` + `systemMessageProvider` 组装成 `TravelAgent`。其余：`LlmConfig`（OpenAI 协议的 Chat/Embedding 模型，兼容 DeepSeek/DashScope）、`ChatMemoryConfig`、`EmbeddingStoreConfig`、`RestClientConfig`。
- **`application/`** — `TravelAgent`（AiServices 接口）、`ChatSessionService`（对话编排 + 日志埋点）、`prompts/travel-agent-system.txt`（System Prompt）。
- **`interfaces/rest/`** — `ChatController`、`KnowledgeController` + DTO，统一返回 `ApiResult<T>`。
- **`infrastructure/`** — `tools/`（`@Tool` Function Calling，薄适配层，真实逻辑在 external）、`external/`（外部 API 客户端）、`rag/`（文档加载/切分/灌入）。
- **`domain/`** — 不可变值对象（record）。
- **`common/`** — `ApiResult`、`GlobalExceptionHandler`、`BizException`。

### 关键数据流

- **对话**：`POST /api/chat {userId,message}` → `ChatController` → `ChatSessionService` → `TravelAgent.chat(@MemoryId userId, @UserMessage msg)` → LLM 自主调 tools / 检索 RAG → `reply`。
- **RAG 灌入**：`POST /api/knowledge/ingest {filePath}` → `KnowledgeIngestService` → `TravelDocumentLoader`（切分）→ `EmbeddingModel` 向量化 → `EmbeddingStore` 入库。
- **RAG 检索**（对话时自动）：用户查询 → `EmbeddingStoreContentRetriever` 向量化 + 召回 Top-K（`travel.rag.top-k`）→ 注入 LLM 上下文。

### 向量库

一期用 **`InMemoryEmbeddingStore`**（进程内，零依赖，无需 Docker/Milvus）。二期切 Milvus 只改 `EmbeddingStoreConfig.embeddingStore()` 一个 `@Bean`，其余代码依赖 `EmbeddingStore` 抽象，无需改动。

## 前端（`travel-web/`）

Vue3 + TypeScript + Pinia + Ant Design Vue 4，位于 `travel-web/`。**完整前端开发指南见 [`travel-web/CLAUDE.md`](travel-web/CLAUDE.md)**。要点：vite proxy `/api`→8080 免跨域；Pinia 按 `userId` 隔离会话（不可变更新）；`request<T>()` 泛型脱壳 `ApiResult`（不在 axios 拦截器脱壳，否则 TS 报错）。

## LangChain4j 1.17 API 约定（踩坑记录，勿用旧路径）

- 对话模型用 **`dev.langchain4j.model.chat.ChatModel`**（不是 0.x 的 `ChatLanguageModel`）；`AiServices.builder().chatModel(...)`。
- `EmbeddingStoreContentRetriever` 在 **`dev.langchain4j.rag.content.retriever`**（不是 `retriever`）。
- `DocumentSplitters` 在 **`dev.langchain4j.data.document.splitter`**（不是 `data.document`）。
- `ChatMemoryProvider` 是函数式接口，**没有 `from()` 静态方法**，直接 `return memoryId -> MessageWindowChatMemory.builder()...build()`。
- logback 滚动策略用 `SizeAndTimeBasedRollingPolicy`（含 `%i` + `maxFileSize`）；`TimeBasedRollingPolicy` 配 `%i` 会启动报错。

## 配置与密钥

- `application.yml`：`travel.llm.api-key: ${LLM_API_KEY}`、`travel.embedding.api-key: ${EMBEDDING_API_KEY}`（均无默认值占位），`spring.profiles.active: dev,local`。
- `application-local.yml`（**被 `.gitignore` 忽略**）：本地真实 key 覆盖占位。生产改用环境变量注入。
- LLM 用 DeepSeek（`deepseek-chat`，OpenAI 协议）；Embedding 用千问 DashScope（`qwen3.7-text-embedding`，`compatible-mode/v1`，1024 维）。

## 外部 API

- 天气：`WeatherClient` 接 **Open-Meteo**（免 key，geocoding 城市→坐标 + forecast 取实时天气，WMO weather_code 映射中文）。
- 航班/酒店：`FlightClient`/`HotelClient` 为 Mock（无匹配的免费 API，真实接入需商业 key + 审核）。
