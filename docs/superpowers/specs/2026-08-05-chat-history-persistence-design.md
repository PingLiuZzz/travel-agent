# 对话记录持久化设计

> 修复 bug4：刷新页面后历史对话清空。对标 DeepSeek 网页端的会话/历史设计。
> 日期：2026-08-05。

## 1. 背景与根因

- **现象**：前端刷新页面后，历史对话全部消失。
- **根因**：对话记录从未写入任何持久化存储。
  - 前端 `stores/chat.ts`：`sessions` / `messagesByUser` / `activeUserId` 全是 Pinia 内存 `ref`，无 localStorage、无后端拉取；`ChatView.onMounted` 在列表为空时 `createSession()` 新建空会话。
  - 后端 `ChatController` 仅有 `POST /api/chat`，无历史查询接口；`ChatSessionService` 仅调 agent + 打日志，无落库；`ChatMemoryConfig` 用进程内 `MessageWindowChatMemory`（默认 `InMemoryChatMemoryStore`）。
  - `pom.xml` 无任何数据库依赖。
  - `web/CLAUDE.md` 明文："会话与消息仅存内存（Pinia），刷新即失——持久化是后续扩展点。"

## 2. 目标 / 非目标

**目标**
- 刷新页面、后端重启后，历史对话均不丢失。
- 全量历史持久化（不受滑窗 20 条截断）。
- 后端 LLM 上下文在重启后可从库恢复（不失忆）。
- 对标 DeepSeek：AI 自动总结会话标题、支持重命名；后端生成会话 id；首条消息才落库（无空会话）；刷新从后端加载、前端缓存仅做加速。

**非目标（YAGNI）**
- 跨设备同步（需账号体系，当前 userId 非用户身份）。
- 流式/SSE（仍整包返回）。
- 工具调用过程的结构化记录（Thought/Action/Observation，M6 再做）。

## 3. 数据模型（MySQL）

两张表，下划线命名，靠 MyBatis-Plus `map-underscore-to-camel-case` 映射。

```sql
CREATE TABLE IF NOT EXISTS chat_session (
  user_id      VARCHAR(64)  NOT NULL,
  title        VARCHAR(128) NOT NULL DEFAULT '新对话',
  last_message TEXT,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      VARCHAR(64)  NOT NULL,
  role         VARCHAR(16)  NOT NULL,            -- user / assistant
  content      MEDIUMTEXT   NOT NULL,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- `chat_message.id` 作为稳定主键，前端 `:key` 用 `msg.id`（不再用 index，避免刷新/重排错位）。
- 字段极简：role/content/createTime；不存 token 数、模型名。
- 建表用 `src/main/resources/schema.sql`（如上，幂等 `IF NOT EXISTS`）+ `spring.sql.init.mode: always`，无需 Flyway。

## 4. 后端设计

### 4.1 新增依赖（pom）

- `com.baomidou:mybatis-plus-spring-boot3-starter:3.5.9`（Spring Boot 3 专用 starter）
- `com.mysql:mysql-connector-j`（runtime，版本由 Spring Boot 3.4 父 BOM 管理）

### 4.2 配置

`application.yml` 新增：
```yaml
spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB:travel_agent}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always          # 启动时执行 schema.sql（建表幂等）
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

`application-local.yml`（gitignore）覆盖为本机真实连接（密码等）。需预先在 MySQL 中创建空库 `travel_agent`（表由 schema.sql 自动建）。

### 4.3 分层（DDD + 组合）

```
infrastructure/persistence/
  entity/   ChatSessionEntity, ChatMessageEntity        (@TableName, @TableId)
  mapper/   ChatSessionMapper, ChatMessageMapper        (extends BaseMapper)
  ChatMessageRepository                                  (封装按条追加/查最近N条/查全部/删除)
  ChatSessionRepository                                  (封装 upsert 元数据 / 列表 / 删除)
  PersistentChatMemoryStore                              (implements ChatMemoryStore，核心)
application/session/
  ChatSessionService                                      (改造：落库 + 标题 + 编排)
  SessionTitleSummarizer                                  (AI 标题总结，@Async)
config/
  ChatMemoryConfig  (注入 PersistentChatMemoryStore)
  MyBatisPlusConfig (@MapperScan) 或在主类标注
```

- `ChatMessageRepository.findRecentByUserId(userId, n)`：按 `create_time` 倒序取最近 n 条，正序返回（供 LLM 上下文）。
- `ChatMessageRepository.findAllByUserId(userId)`：全部消息（供历史接口）。
- `ChatSessionRepository.upsertMeta(userId, title, lastMessage)`：存在则更新，不存在则插入（首条对话建会话）。

### 4.4 ChatMemory 持久化（方案 D，对标 DeepSeek stateless）

DeepSeek 后端无状态、每次请求从历史重建上下文。本方案是其 LangChain4j 等价实现：

| 组件 | 职责 | 行为 |
|---|---|---|
| `ChatSessionService.chat()` | **全量历史落库** | 调 agent 拿 reply 后，把 user message + assistant reply **按条**写 `chat_message`（完整保留，不受滑窗截断） |
| `PersistentChatMemoryStore.getMessages(id)` | **LLM 上下文恢复** | 读 `chat_message` 该 userId 最近 `maxMessages` 条 → 转 `UserMessage`/`AiMessage` 返回 |
| `PersistentChatMemoryStore.updateMessages(id, msgs)` | **no-op** | 不二次写，避免截断全量历史、避免 user message 重复 |
| `PersistentChatMemoryStore.deleteMessages(id)` | 删会话消息 | 删该 userId 的 `chat_message` |

**时序验证（无重复、无截断、重启可恢复）**：
```
chat(u1, "去东京"):
  1. reply = agent.chat(u1, "去东京")
       // AiServices: memory.add(User) → store.getMessages 读库(此时空) → add → updateMessages(no-op)
       //             发 LLM → memory.add(Ai) → updateMessages(no-op)
  2. 落库 chat_message(user, "去东京")        ← 全量历史
  3. 落库 chat_message(assistant, reply)
  4. 若 session 新建 → @Async summarizeTitle(u1, "去东京")
chat(u1, "天气呢"):
  1. reply = agent.chat(u1, "天气呢")
       // memory.add(User) → store.getMessages 读库 → [去东京, reply](步骤2/3已落库) → 上下文正确
  2/3. 落库本轮两条
后端重启 → chat(u1, "继续"):
  // store.getMessages 从 MySQL 读 → 上下文恢复 ✅
```

> 实现时需按 LangChain4j 1.17.2 实际包路径/方法签名核对：
> - `ChatMemoryStore` 预期在 `dev.langchain4j.store.memory.chat`，方法 `getMessages/updateMessages/deleteMessages`。
> - 消息类型用 `UserMessage` / `AiMessage`（1.x，非 0.x 的 `AssistantMessage`）。
> - `MessageWindowChatMemory.builder().chatMemoryStore(store)` 设置 store。

### 4.5 会话标题 AI 总结（@Async）

- 首次对话（session 新建）后，异步用 `ChatModel.generate(prompt)` 生成标题：
  - prompt：`"请用不超过 10 个字概括以下用户提问的主题，只输出标题文本：\n" + userMessage`
- 结果写回 `chat_session.title`。失败 try-catch 记日志，不影响主对话。
- 在主类或 config 加 `@EnableAsync`；`SessionTitleSummarizer` 方法标 `@Async`。

### 4.6 会话生命周期

- **会话 id 由后端生成**（UUID），前端不再生成。
- **首条消息才落库**：`POST /api/chat` 不带 `userId` → 后端生成 id、`upsertMeta` 建会话 → 返回 `sessionId`。带 `userId` → 续聊。
- 无空会话垃圾。

## 5. REST 接口

统一 `ApiResult<T>` 包裹。

| 方法 | 路径 | 入参 | 出参 data | 说明 |
|---|---|---|---|---|
| POST | `/api/chat` | `{userId?, message}` | `{reply, sessionId}` | userId 空→建会话+首条；非空→续聊 |
| GET | `/api/chat/sessions` | — | `ChatSessionVo[]` | 会话列表（启动填左侧栏） |
| GET | `/api/chat/sessions/{userId}/messages` | — | `ChatMessageVo[]` | 该会话全部消息 |
| PATCH | `/api/chat/sessions/{userId}` | `{title}` | `void` | 手动重命名 |
| DELETE | `/api/chat/sessions/{userId}` | — | `void` | 删会话及其消息 |

DTO：
- `ChatRequest { String userId; String message; }`（userId 可空）
- `ChatResponse { String reply; String sessionId; }`
- `ChatSessionVo { String userId; String title; String lastMessage; String createTime; String updateTime; }`
- `ChatMessageVo { Long id; String role; String content; String createTime; }`
- `RenameSessionRequest { String title; }`

## 6. 前端设计

定位：**后端为唯一真相源；localStorage 仅缓存已加载会话的消息，刷新从后端加载。**

### 6.1 api/chat.ts（对齐新 DTO）
- `sendChat(payload: { userId?: string; message: string }): Promise<{ reply: string; sessionId: string }>`
- `getSessions(): Promise<ChatSessionVo[]>`
- `getSessionMessages(userId: string): Promise<ChatMessageVo[]>`
- `renameSession(userId: string, title: string): Promise<void>`
- `deleteSession(userId: string): Promise<void>`

### 6.2 types/chat.ts
- 对齐上述 DTO；`ChatMessage` 增加 `id: number`。

### 6.3 stores/chat.ts 重构
- state：`sessions`（来自后端）、`activeUserId`（可空 = 新建态）、`messagesByUser`（缓存）、`loading`。
- `init()`：`getSessions()` 填列表；非空 → 默认选首个并 `loadMessages`；空 → 新建态。
- `newChat()`：`activeUserId = ''`，清当前消息视图（不落库，对标 DeepSeek）。
- `sendMessage(content)`：
  - `activeUserId` 空 → `sendChat({ message })` → 拿 `sessionId` → sessions 前置新会话、`activeUserId=sessionId`、append 两条。
  - 非空 → `sendChat({ userId, message })` → append 两条、更新该会话 `lastMessage`。
- `selectSession(userId)`：切换；若消息未缓存则 `getSessionMessages`。
- `renameSession` / `removeSession`：调后端 + 同步本地。
- localStorage `travel-agent:chat-state`：仅存 `messagesByUser`（已加载消息缓存），会话列表始终以 `getSessions()` 为准。

### 6.4 ChatView.vue
- `onMounted` → `store.init()`（带 loading）。
- "新建会话"按钮 → `store.newChat()`。
- `MessageBubble` 的 `:key` 从 `index` 改 `msg.id`。

## 7. 决策记录

| 决策点 | 结论 |
|---|---|
| ① 数据模型 | 加 `chat_message.id` 稳定主键；标题 AI 总结；字段极简 |
| ② ChatMemory 持久化 | 方案 D：service 按条落库全量 + store 只读恢复最近 N 条 + updateMessages no-op |
| ③ 会话标识 | 后端生成 UUID；首条消息才落库；去空会话 |
| ④ localStorage | 仅消息缓存；刷新从后端加载 |
| MySQL 实例 | 本地已有，配置走 application-local.yml，不动 docker-compose |

## 8. 验证计划

- **后端单测**：`ChatMessageRepository`（查最近 N 条顺序、upsert）、`PersistentChatMemoryStore`（entity↔ChatMessage 转换、getMessages 最近 N、updateMessages no-op 不写）。
- **集成验证（手工）**：
  1. 对话两轮 → 刷新页面 → 历史完整、左侧标题为 AI 总结。
  2. 重启后端 → 继续对话 → LLM 记得前文（上下文从库恢复）。
  3. 新建对话不发消息 → 刷新 → 不残留空会话。
  4. 重命名、删除会话生效。
- **质量门禁**：`mvn spotless:apply` + `mvn test`；前端 `npm run type-check`。

## 9. 风险与回退

- **LangChain4j 1.17.2 API 细节**（ChatMemoryStore 包路径/签名、AiMessage）：实现时核对，必要时调整。
- **MyBatis-Plus 与 SB 3.4 / JDK 21 兼容**：3.5.9 已支持 SB3。
- **@Async 标题失败**：try-catch 记日志，不影响主流程；最坏标题停留"新对话"，可手动重命名。
- **回退**：`PersistentChatMemoryStore` 出问题可换回 `InMemoryChatMemoryStore`（改 `ChatMemoryConfig` 一处），历史落库仍由 service 承担，前端不受影响。
