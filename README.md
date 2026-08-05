# 旅游出行智能体（Travel Agent）

基于 **Java 21 + Spring Boot 3.4 + LangChain4j 1.17.2** 的旅游出行智能体。已具备多轮对话、工具调用（天气真实接入）、RAG 知识检索能力，提供后端 REST API；配套 Vue3 前端位于 `travel-web/`。

## 技术栈

| 类别 | 选型 |
|---|---|
| 语言/运行时 | JDK 21 |
| 后端框架 | Spring Boot 3.4 |
| AI 框架 | LangChain4j 1.17.2（核心 GA）/ 1.17.2-beta27（Tika 扩展） |
| 大模型 | DeepSeek（对话，OpenAI 协议） |
| Embedding | 千问 DashScope `qwen3.7-text-embedding` |
| 向量库 | InMemoryEmbeddingStore（一期，进程内；二期可切 Milvus，只改一个 @Bean） |
| 文档解析 | Apache Tika |
| 前端 | Vue3 + TypeScript + Ant Design Vue 4（`travel-web/`） |

## 环境要求

- **JDK 21**（系统默认可能是 17，需切换）：本机位于 `E:\File-work\Java_Detail\jdk-21`
- **Maven 3.8+**，本地仓库 `E:\File-work\Java_Detail\maven\repo`（非默认 `~/.m2`），镜像阿里云
- **Node 18+ / npm**

## 配置密钥（不入库）

`application.yml` 用占位 `${LLM_API_KEY}` / `${EMBEDDING_API_KEY}`（无默认值），真实值放 `application-local.yml`（**已被 .gitignore 忽略，需自行创建**）：

```yaml
# src/main/resources/application-local.yml（自行创建，勿提交）
travel:
  llm:
    api-key: 你的 DeepSeek Key
  embedding:
    api-key: 你的 DashScope（千问）Key
```

`spring.profiles.active: dev,local` 已配好，会自动加载 `application-local.yml`。

## 快速启动

### 后端

```powershell
$env:JAVA_HOME = "E:\File-work\Java_Detail\jdk-21"   # 必设
mvn spring-boot:run                                   # 监听 8080
```

### 前端

```bash
cd travel-web
npm install
npm run dev                                           # 监听 5173，/api 自动代理到 8080
```

浏览器访问 http://localhost:5173

### 接口试用

```bash
# 对话（触发天气工具，返回 Open-Meteo 真实时天气）
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"userId":"u001","message":"帮我查下北京今天天气"}'

# 灌入知识库（触发 embedding 向量化）
curl -X POST http://localhost:8080/api/knowledge/ingest \
  -H "Content-Type: application/json" \
  -d '{"filePath":"docs/beijing-attractions.txt"}'
```

## 能力现状

| 能力 | 状态 | 说明 |
|---|---|---|
| M1 多轮对话 | ✅ | DeepSeek，按 userId 隔离记忆 |
| M2 工具调用 | ✅ 部分 | 天气接 Open-Meteo 真实 API；航班/酒店 Mock（无匹配的免费 API） |
| M3 RAG 检索 | ✅ | 千问 embedding + InMemoryEmbeddingStore，文档切分/灌入/检索全通 |
| M5 多 Agent | 二期 | 单 Agent 足够时暂不拆 |
| M6 生产化 | 基础 | 统一异常、结构化日志已具备；监控/审计待补 |

## 质量门禁

```bash
mvn spotless:apply             # 后端格式化（google-java-format）
mvn test                       # 后端单元测试
cd travel-web && npm run type-check   # 前端类型检查（vue-tsc，strict）
```

## 工程结构

```
travel-agent/
├── src/main/java/com/travel/agent/   # 后端（DDD 包分层）
├── src/main/resources/               # application.yml / application-local.yml / prompts / logback
├── docs/                             # RAG 测试文档（如 beijing-attractions.txt）
├── travel-web/                              # 前端（Vue3 + Ant Design Vue）
├── pom.xml
├── CLAUDE.md                         # Claude Code 指南（项目级 + 后端）
└── README.md
```

更详细的开发指南见 `CLAUDE.md`（项目级 + 后端）和 `travel-web/CLAUDE.md`（前端）。

---

## MIT许可证

版权所有 (c) 2026 PingLiuZzz

特此授予任何人免费获取副本的许可。
本软件及相关文档文件（以下简称“软件”），用于处理
软件不受任何限制，包括但不限于以下权利：
使用、复制、修改、合并、发布、分发、再许可和/或出售
软件副本，并允许软件的所有者获取软件副本。
按照以下条件提供：

上述版权声明和本许可声明应包含在所有文件中。
软件的副本或实质性部分。

本软件按“原样”提供，不提供任何形式的明示或暗示的保证。
默示的，包括但不限于适销性的保证，
适用于特定用途且不侵权。在任何情况下，
作者或版权所有者对任何索赔、损害或其他损失概不负责。
因以下原因引起的责任，无论是在合同诉讼、侵权诉讼或其他诉讼中，
因软件本身或其使用或其他交易而引起的或与之相关的
软件。
