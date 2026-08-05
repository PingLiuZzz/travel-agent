# 对话记录持久化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让历史对话在刷新页面、后端重启后都不丢失，对标 DeepSeek 网页端（AI 标题、后端生成会话 id、首条消息才落库、刷新从后端加载）。

**Architecture:** 后端 MySQL + MyBatis-Plus 落库全部历史；自定义 `PersistentChatMemoryStore`（getMessages 只读最近 N 条恢复 LLM 上下文、updateMessages no-op、deleteMessages 删库）让 LLM 重启不失忆；`ChatSessionService` 按条落库全量历史 + 触发 AI 标题。前端刷新从后端 GET 会话/消息，localStorage 仅作消息缓存。

**Tech Stack:** Spring Boot 3.4 / JDK 21 / LangChain4j 1.17.2 / MyBatis-Plus 3.5.9 (mybatis-plus-spring-boot3-starter) / mysql-connector-j / MySQL 8 / Vue3 + TS + Pinia。

## Global Constraints

- **JDK 21**：所有 mvn 命令前必设 `$env:JAVA_HOME="E:\File-work\Java_Detail\jdk-21"`（PowerShell），否则编译失败。
- **Maven 本地仓库**：`E:\File-work\Java_Detail\maven\repo`（非 `~/.m2`），排查依赖去这里。
- **LangChain4j 1.17.2 API 约定**：对话模型用 `dev.langchain4j.model.chat.ChatModel`；消息类型用 `UserMessage`/`AiMessage`（1.x，非 0.x `AssistantMessage`）；`ChatMemoryStore` 预期在 `dev.langchain4j.store.memory.chat`。涉及处标注「实现时核对包路径/签名」。
- **非 git 仓库**：项目当前未纳入 git，故各 task 末尾不写 `git commit`，改为**阶段性验证门禁**（编译/测试通过即里程碑）。
- **代码风格**：google-java-format（`mvn spotless:apply`）；Java 注释中文；构造器注入 + `@Autowired`；OOP/组合优先。
- **前端**：TS strict 禁 `any`；异步统一 `async/await`；类型对齐后端 DTO。
- **MySQL**：用户本地已有；需先建空库 `travel_agent`（主库）与 `travel_agent_test`（测试库），表由 `schema.sql` 自动建。

---

## File Structure

**后端新增**
- `src/main/resources/schema.sql` — 建表 DDL（幂等）。
- `infrastructure/persistence/entity/ChatSessionEntity.java` / `ChatMessageEntity.java` — MyBatis-Plus 实体。
- `infrastructure/persistence/mapper/ChatSessionMapper.java` / `ChatMessageMapper.java` — BaseMapper。
- `infrastructure/persistence/ChatMessageRepository.java` / `ChatSessionRepository.java` — 封装查询/落库。
- `infrastructure/persistence/PersistentChatMemoryStore.java` — ChatMemoryStore 实现（核心）。
- `application/session/SessionTitleSummarizer.java` — @Async AI 标题。
- `interfaces/rest/dto/ChatSessionVo.java` / `ChatMessageVo.java` / `RenameSessionRequest.java` — 新 DTO。

**后端修改**
- `pom.xml` — 加 MyBatis-Plus + MySQL 驱动。
- `src/main/resources/application.yml` — datasource + mybatis-plus + sql.init。
- `src/main/resources/application-local.yml` — 本机真实连接（gitignore）。
- `TravelAgentApplication.java` — `@EnableAsync` + `@MapperScan`。
- `config/ChatMemoryConfig.java` — 注入 PersistentChatMemoryStore。
- `application/session/ChatSessionService.java` — 落库编排 + 标题触发 + 列表/重命名/删除。
- `interfaces/rest/dto/ChatRequest.java` — userId 放宽为可空。
- `interfaces/rest/dto/ChatResponse.java` — 加 sessionId。
- `interfaces/rest/ChatController.java` — 新增 sessions/messages/rename/delete 接口 + chat 返回 sessionId。

**测试新增**（src/test 无现存测试，从零搭）
- `ChatMessageRepositoryTest.java`（@MybatisPlusTest 集成，连 test 库）
- `PersistentChatMemoryStoreTest.java`（Mockito 单元）
- `SessionTitleSummarizerTest.java`（Mockito 单元）
- `ChatSessionServiceTest.java`（Mockito 单元）
- `src/test/resources/application-test.yml` — 测试库连接。

**前端修改**
- `web/src/types/chat.ts` — 对齐新 DTO。
- `web/src/api/chat.ts` — 新接口封装。
- `web/src/stores/chat.ts` — init/newChat/sendMessage/selectSession/rename/removeSession + 消息缓存。
- `web/src/views/ChatView.vue` — onMounted init、新建按钮、`:key=msg.id`。

---

### Task 1: 后端依赖、配置与建表

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.yml`
- Modify: `src/main/resources/application-local.yml`
- Create: `src/main/resources/schema.sql`
- Modify: `src/main/java/com/travel/agent/TravelAgentApplication.java`

**Interfaces:**
- Produces: MySQL 连接 + 表 `chat_session`/`chat_message` 就绪，供后续 Repository 使用。

- [ ] **Step 1: pom 加依赖与版本**

`pom.xml` 的 `<properties>` 内加：
```xml
<mybatis-plus.version>3.5.9</mybatis-plus.version>
```
`<dependencies>` 内加（置于 spring-boot-starter-web 之后）：
```xml
<!-- MyBatis-Plus（Spring Boot 3 专用 starter）：ORM 与 CRUD -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>${mybatis-plus.version}</version>
</dependency>
<!-- MySQL 驱动（版本由 Spring Boot 父 BOM 管理） -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: 写 schema.sql**

`src/main/resources/schema.sql`：
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
  role         VARCHAR(16)  NOT NULL,
  content      MEDIUMTEXT   NOT NULL,
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 3: application.yml 加 datasource + mybatis-plus + sql.init**

`src/main/resources/application.yml` 顶部 `spring:` 块内增补（与现有 `application.name`/`profiles` 同级）：
```yaml
spring:
  application:
    name: travel-agent
  profiles:
    active: dev,local
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB:travel_agent}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```
文件末尾（`management:` 之后）加：
```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

- [ ] **Step 4: application-local.yml 加本机连接**

`src/main/resources/application-local.yml`（已被 .gitignore）追加（用户填真实密码）：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/travel_agent?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: <本机MySQL密码>
```

- [ ] **Step 5: 主类加 @EnableAsync 与 @MapperScan**

`TravelAgentApplication.java` 顶部注解改为：
```java
import org.springframework.scheduling.annotation.EnableAsync;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@EnableAsync
@MapperScan("com.travel.agent.infrastructure.persistence.mapper")
public class TravelAgentApplication { ... }
```

- [ ] **Step 6: 建库并验证启动建表**

在 MySQL 执行：`CREATE DATABASE IF NOT EXISTS travel_agent DEFAULT CHARSET utf8mb4;`
运行：
```powershell
$env:JAVA_HOME = "E:\File-work\Java_Detail\jdk-21"
mvn -q spring-boot:run
```
Expected：启动无异常，日志含 `Initializing Spring DispatcherServlet` / schema 执行；连库可见两表已建。Ctrl+C 停止。
（无需单测；本 task 是基础设施。）

---

### Task 2: 实体与 Mapper

**Files:**
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/entity/ChatSessionEntity.java`
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/entity/ChatMessageEntity.java`
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/mapper/ChatSessionMapper.java`
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/mapper/ChatMessageMapper.java`

**Interfaces:**
- Produces: `ChatSessionEntity`（字段 userId/title/lastMessage/createTime/updateTime）、`ChatMessageEntity`（id/userId/role/content/createTime）、两个 `BaseMapper`。

- [ ] **Step 1: ChatSessionEntity**

```java
package com.travel.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 会话实体。user_id 由应用生成（非自增），故 IdType.INPUT。 */
@Data
@TableName("chat_session")
public class ChatSessionEntity {
    @TableId(type = IdType.INPUT)
    private String userId;
    private String title;
    private String lastMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: ChatMessageEntity**

```java
package com.travel.agent.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 消息实体。id 自增。 */
@Data
@TableName("chat_message")
public class ChatMessageEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
```

- [ ] **Step 3: 两个 Mapper**

```java
package com.travel.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.agent.infrastructure.persistence.entity.ChatSessionEntity;

public interface ChatSessionMapper extends BaseMapper<ChatSessionEntity> {
}
```
```java
package com.travel.agent.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;

public interface ChatMessageMapper extends BaseMapper<ChatMessageEntity> {
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -q compile`（先设 JAVA_HOME）
Expected: BUILD SUCCESS。

---

### Task 3: Repository + 集成测试（TDD）

**Files:**
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/ChatMessageRepository.java`
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/ChatSessionRepository.java`
- Test: `src/test/java/com/travel/agent/infrastructure/persistence/ChatMessageRepositoryTest.java`
- Create: `src/test/resources/application-test.yml`

**Interfaces:**
- Produces:
  - `ChatMessageRepository.findRecentByUserId(String userId, int n): List<ChatMessageEntity>` — 最近 n 条，**正序**（旧→新）。
  - `ChatMessageRepository.findAllByUserId(String userId): List<ChatMessageEntity>` — 全部正序。
  - `ChatMessageRepository.appendMessage(String userId, String role, String content): void`
  - `ChatMessageRepository.deleteByUserId(String userId): void`
  - `ChatSessionRepository.upsertMeta(String userId, String title, String lastMessage): boolean` — 新建返回 true。
  - `ChatSessionRepository.listAll(): List<ChatSessionEntity>` — 按 updateTime 倒序。
  - `ChatSessionRepository.renameTitle(String userId, String title): void`
  - `ChatSessionRepository.deleteByUserId(String userId): void`

- [ ] **Step 1: 先写失败测试**

`src/test/resources/application-test.yml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/travel_agent_test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: <本机MySQL密码>
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
```

`src/test/java/com/travel/agent/infrastructure/persistence/ChatMessageRepositoryTest.java`：
```java
package com.travel.agent.infrastructure.persistence;

import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisPlusTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisPlusTest
@ActiveProfiles("test")
@Import({ChatMessageRepository.class, ChatSessionRepository.class})
class ChatMessageRepositoryTest {

    @Autowired
    ChatMessageRepository messageRepo;
    @Autowired
    ChatSessionRepository sessionRepo;

    @Test
    void findRecent_returnsLastN_inChronologicalOrder() {
        sessionRepo.upsertMeta("u1", "新对话", "r0");
        messageRepo.appendMessage("u1", "user", "m1");
        messageRepo.appendMessage("u1", "assistant", "m2");
        messageRepo.appendMessage("u1", "user", "m3");

        List<ChatMessageEntity> recent = messageRepo.findRecentByUserId("u1", 2);

        assertThat(recent).extracting(ChatMessageEntity::getContent)
                .containsExactly("m2", "m3");
    }

    @Test
    void upsertMeta_newReturnsTrue_updateReturnsFalse() {
        assertThat(sessionRepo.upsertMeta("u2", "新对话", "r1")).isTrue();
        assertThat(sessionRepo.upsertMeta("u2", "新对话", "r2")).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

先在 MySQL 建测试库：`CREATE DATABASE IF NOT EXISTS travel_agent_test DEFAULT CHARSET utf8mb4;`
Run: `mvn -Dtest=ChatMessageRepositoryTest test`
Expected: 编译失败（ChatMessageRepository / ChatSessionRepository 不存在）。

- [ ] **Step 3: 实现 ChatMessageRepository**

```java
package com.travel.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import com.travel.agent.infrastructure.persistence.mapper.ChatMessageMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/** 消息持久化封装。 */
@Repository
public class ChatMessageRepository {

    private final ChatMessageMapper mapper;

    @Autowired
    public ChatMessageRepository(ChatMessageMapper mapper) {
        this.mapper = mapper;
    }

    /** 最近 n 条，按时间正序返回（旧→新），供 LLM 上下文恢复。 */
    public List<ChatMessageEntity> findRecentByUserId(String userId, int n) {
        LambdaQueryWrapper<ChatMessageEntity> wrapper = new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getUserId, userId)
                .orderByDesc(ChatMessageEntity::getId)
                .last("LIMIT " + n);
        List<ChatMessageEntity> desc = mapper.selectList(wrapper);
        Collections.reverse(desc);
        return desc;
    }

    /** 全部消息，正序，供历史接口。 */
    public List<ChatMessageEntity> findAllByUserId(String userId) {
        return mapper.selectList(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getUserId, userId)
                .orderByAsc(ChatMessageEntity::getId));
    }

    public void appendMessage(String userId, String role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setUserId(userId);
        entity.setRole(role);
        entity.setContent(content);
        mapper.insert(entity);
    }

    public void deleteByUserId(String userId) {
        mapper.delete(new LambdaQueryWrapper<ChatMessageEntity>()
                .eq(ChatMessageEntity::getUserId, userId));
    }
}
```

- [ ] **Step 4: 实现 ChatSessionRepository**

```java
package com.travel.agent.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.agent.infrastructure.persistence.entity.ChatSessionEntity;
import com.travel.agent.infrastructure.persistence.mapper.ChatSessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.List;

/** 会话持久化封装。upsertMeta 返回是否为新建。 */
@Repository
public class ChatSessionRepository {

    private final ChatSessionMapper mapper;

    @Autowired
    public ChatSessionRepository(ChatSessionMapper mapper) {
        this.mapper = mapper;
    }

    public boolean upsertMeta(String userId, String title, String lastMessage) {
        ChatSessionEntity existing = mapper.selectById(userId);
        if (existing == null) {
            ChatSessionEntity entity = new ChatSessionEntity();
            entity.setUserId(userId);
            entity.setTitle(title);
            entity.setLastMessage(lastMessage);
            mapper.insert(entity);
            return true;
        }
        existing.setLastMessage(lastMessage);
        mapper.updateById(existing);
        return false;
    }

    public List<ChatSessionEntity> listAll() {
        return mapper.selectList(new LambdaQueryWrapper<ChatSessionEntity>()
                .orderByDesc(ChatSessionEntity::getUpdateTime));
    }

    public void renameTitle(String userId, String title) {
        ChatSessionEntity entity = mapper.selectById(userId);
        if (entity != null) {
            entity.setTitle(title);
            mapper.updateById(entity);
        }
    }

    public void deleteByUserId(String userId) {
        mapper.deleteById(userId);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -Dtest=ChatMessageRepositoryTest test`
Expected: 两个测试 PASS。

---

### Task 4: PersistentChatMemoryStore + ChatMemoryConfig 接入（TDD）

**Files:**
- Create: `src/main/java/com/travel/agent/infrastructure/persistence/PersistentChatMemoryStore.java`
- Test: `src/test/java/com/travel/agent/infrastructure/persistence/PersistentChatMemoryStoreTest.java`
- Modify: `src/main/java/com/travel/agent/config/ChatMemoryConfig.java`

**Interfaces:**
- Consumes: `ChatMessageRepository.findRecentByUserId` / `deleteByUserId`，`TravelAiProperties.memory.maxMessages`。
- Produces: `PersistentChatMemoryStore`（实现 `dev.langchain4j.store.memory.chat.ChatMemoryStore`），被 `ChatMemoryConfig` 注入 provider。

- [ ] **Step 1: 先写失败测试**

`src/test/java/com/travel/agent/infrastructure/persistence/PersistentChatMemoryStoreTest.java`：
```java
package com.travel.agent.infrastructure.persistence;

import com.travel.agent.config.TravelAiProperties;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersistentChatMemoryStoreTest {

    @Mock
    ChatMessageRepository repository;

    @Test
    void getMessages_returnsRecentInOrder_withCorrectTypes() {
        TravelAiProperties props = new TravelAiProperties(); // 默认 maxMessages=20
        when(repository.findRecentByUserId("u1", 20)).thenReturn(List.of(
                entity("user", "你好"),
                entity("assistant", "你好，想去哪？")));
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(repository, props);

        List<ChatMessage> messages = store.getMessages("u1");

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        verify(repository).findRecentByUserId("u1", 20);
    }

    @Test
    void updateMessages_isNoOp_doesNotWrite() {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(repository, new TravelAiProperties());

        store.updateMessages("u1", List.of(new UserMessage("x")));

        verifyNoInteractions(repository);
    }

    @Test
    void deleteMessages_delegatesToRepository() {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore(repository, new TravelAiProperties());

        store.deleteMessages("u1");

        verify(repository).deleteByUserId("u1");
    }

    private ChatMessageEntity entity(String role, String content) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.setRole(role);
        e.setContent(content);
        return e;
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=PersistentChatMemoryStoreTest test`
Expected: 编译失败（PersistentChatMemoryStore 不存在）。

- [ ] **Step 3: 实现 PersistentChatMemoryStore**

> 实现时核对：`ChatMemoryStore` 包路径 `dev.langchain4j.store.memory.chat.ChatMemoryStore`；方法 `getMessages(Object)` / `updateMessages(Object, List<ChatMessage>)` / `deleteMessages(Object)`；`UserMessage.from(String)` / `AiMessage.from(String)`。若 1.17.2 实际签名不同，按 IDE 提示调整。

```java
package com.travel.agent.infrastructure.persistence;

import com.travel.agent.config.TravelAiProperties;
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 落库版 ChatMemoryStore。
 *
 * <p>关键设计：getMessages 只读最近 maxMessages 条用于 LLM 上下文恢复（后端重启不失忆）；
 * updateMessages 为 no-op——全量历史由 ChatSessionService 按条落库，
 * 这里不二次写，避免滑窗截断污染全量历史、避免 user message 重复。
 */
@Component
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository repository;
    private final int maxMessages;

    @Autowired
    public PersistentChatMemoryStore(ChatMessageRepository repository, TravelAiProperties properties) {
        this.repository = repository;
        this.maxMessages = properties.getMemory().getMaxMessages();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String userId = String.valueOf(memoryId);
        return repository.findRecentByUserId(userId, maxMessages).stream()
                .map(PersistentChatMemoryStore::toChatMessage)
                .toList();
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 故意 no-op，见类注释
    }

    @Override
    public void deleteMessages(Object memoryId) {
        repository.deleteByUserId(String.valueOf(memoryId));
    }

    private static ChatMessage toChatMessage(ChatMessageEntity entity) {
        if ("assistant".equals(entity.getRole())) {
            return AiMessage.from(entity.getContent());
        }
        return UserMessage.from(entity.getContent());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=PersistentChatMemoryStoreTest test`
Expected: 三个测试 PASS。

- [ ] **Step 5: 改造 ChatMemoryConfig 注入 store**

`ChatMemoryConfig.java` 全文替换为：
```java
package com.travel.agent.config;

import com.travel.agent.infrastructure.persistence.PersistentChatMemoryStore;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 会话记忆装配。
 *
 * <p>按 userId 维护独立 Memory；注入 PersistentChatMemoryStore 使上下文落 MySQL，重启可恢复。
 */
@Configuration
public class ChatMemoryConfig {

    private final PersistentChatMemoryStore chatMemoryStore;

    @Autowired
    public ChatMemoryConfig(PersistentChatMemoryStore chatMemoryStore) {
        this.chatMemoryStore = chatMemoryStore;
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(TravelAiProperties properties) {
        int maxMessages = properties.getMemory().getMaxMessages();
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(maxMessages)
                .chatMemoryStore(chatMemoryStore)
                .build();
    }
}
```

- [ ] **Step 6: 全量编译 + 单测**

Run: `mvn -q test`
Expected: 所有已写测试 PASS，编译无错。

---

### Task 5: SessionTitleSummarizer（@Async，TDD）

**Files:**
- Create: `src/main/java/com/travel/agent/application/session/SessionTitleSummarizer.java`
- Test: `src/test/java/com/travel/agent/application/session/SessionTitleSummarizerTest.java`

**Interfaces:**
- Consumes: `ChatModel.chat(String)`，`ChatSessionRepository.renameTitle`。
- Produces: `SessionTitleSummarizer.summarize(String userId, String firstUserMessage): void`（异步，失败不抛）。

- [ ] **Step 1: 先写失败测试**

```java
package com.travel.agent.application.session;

import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionTitleSummarizerTest {

    @Mock
    ChatModel chatModel;
    @Mock
    ChatSessionRepository sessionRepo;

    @Test
    void summarize_callsLlm_andUpdatesTitle() {
        when(chatModel.chat(anyString())).thenReturn("东京三日游");
        SessionTitleSummarizer summarizer = new SessionTitleSummarizer(chatModel, sessionRepo);

        summarizer.summarize("u1", "帮我规划东京三日游");

        verify(sessionRepo).renameTitle("u1", "东京三日游");
    }

    @Test
    void summarize_llmFailure_doesNotThrow_doesNotRename() {
        when(chatModel.chat(anyString())).thenThrow(new RuntimeException("api down"));
        SessionTitleSummarizer summarizer = new SessionTitleSummarizer(chatModel, sessionRepo);

        assertThatCode(() -> summarizer.summarize("u1", "x")).doesNotThrowAnyException();

        verify(sessionRepo, never()).renameTitle(anyString(), anyString());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=SessionTitleSummarizerTest test`
Expected: 编译失败（类不存在）。

- [ ] **Step 3: 实现 SessionTitleSummarizer**

> 实现时核对：`ChatModel.chat(String)` 在 1.17.2 是否直接返回 String；若签名不同，改用 `chatModel.generate(new UserMessage(prompt))` 或其他便捷重载。

```java
package com.travel.agent.application.session;

import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import dev.langchain4j.model.chat.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 会话标题 AI 总结（对标 DeepSeek 网页端：首条消息后自动生成主题）。
 *
 * <p>异步执行，失败仅记日志，不影响主对话。
 */
@Component
public class SessionTitleSummarizer {

    private static final Logger log = LoggerFactory.getLogger(SessionTitleSummarizer.class);

    private final ChatModel chatModel;
    private final ChatSessionRepository sessionRepo;

    @Autowired
    public SessionTitleSummarizer(ChatModel chatModel, ChatSessionRepository sessionRepo) {
        this.chatModel = chatModel;
        this.sessionRepo = sessionRepo;
    }

    @Async
    public void summarize(String userId, String firstUserMessage) {
        try {
            String prompt = "请用不超过 10 个字概括以下用户提问的主题，只输出标题文本，不要标点和引号：\n"
                    + firstUserMessage;
            String title = chatModel.chat(prompt).trim();
            if (!title.isEmpty()) {
                sessionRepo.renameTitle(userId, title);
                log.info("session-title-updated userId={} title={}", userId, title);
            }
        } catch (Exception e) {
            log.warn("标题总结失败 userId={}", userId, e);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=SessionTitleSummarizerTest test`
Expected: 两个测试 PASS。

---

### Task 6: ChatSessionService 落库编排（TDD）

**Files:**
- Modify: `src/main/java/com/travel/agent/application/session/ChatSessionService.java`
- Create: `src/main/java/com/travel/agent/application/session/ChatResult.java`
- Test: `src/test/java/com/travel/agent/application/session/ChatSessionServiceTest.java`

**Interfaces:**
- Consumes: `TravelAgent.chat`、`ChatMessageRepository`、`ChatSessionRepository`、`SessionTitleSummarizer`。
- Produces: `ChatSessionService.chat(String userId, String message): ChatResult`；`ChatResult(String reply, String sessionId)`。

- [ ] **Step 1: 先写失败测试**

```java
package com.travel.agent.application.session;

import com.travel.agent.application.agent.TravelAgent;
import com.travel.agent.infrastructure.persistence.ChatMessageRepository;
import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatSessionServiceTest {

    @Mock TravelAgent agent;
    @Mock ChatMessageRepository messageRepo;
    @Mock ChatSessionRepository sessionRepo;
    @Mock SessionTitleSummarizer summarizer;

    @Test
    void chat_newSession_appendsBothMessages_triggersTitle_returnsNewId() {
        when(agent.chat(anyString(), eq("去东京"))).thenReturn("好的");
        when(sessionRepo.upsertMeta(anyString(), eq("新对话"), eq("好的"))).thenReturn(true);
        ChatSessionService svc = new ChatSessionService(agent, messageRepo, sessionRepo, summarizer);

        ChatResult result = svc.chat(null, "去东京");

        assertThat(result.sessionId()).isNotBlank();
        assertThat(result.reply()).isEqualTo("好的");
        verify(messageRepo).appendMessage(result.sessionId(), "user", "去东京");
        verify(messageRepo).appendMessage(result.sessionId(), "assistant", "好的");
        verify(summarizer).summarize(result.sessionId(), "去东京");
    }

    @Test
    void chat_existingSession_doesNotTriggerTitle() {
        when(agent.chat("u1", "继续")).thenReturn("ok");
        when(sessionRepo.upsertMeta("u1", "新对话", "ok")).thenReturn(false);
        ChatSessionService svc = new ChatSessionService(agent, messageRepo, sessionRepo, summarizer);

        ChatResult result = svc.chat("u1", "继续");

        assertThat(result.sessionId()).isEqualTo("u1");
        verify(summarizer, never()).summarize(anyString(), anyString());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=ChatSessionServiceTest test`
Expected: 编译失败（构造器签名不匹配、ChatResult 不存在）。

- [ ] **Step 3: 新建 ChatResult record**

`src/main/java/com/travel/agent/application/session/ChatResult.java`：
```java
package com.travel.agent.application.session;

/** chat() 返回值：回复内容 + 会话标识。 */
public record ChatResult(String reply, String sessionId) {
}
```

- [ ] **Step 4: 改造 ChatSessionService**

全文替换为：
```java
package com.travel.agent.application.session;

import com.travel.agent.application.agent.TravelAgent;
import com.travel.agent.infrastructure.persistence.ChatMessageRepository;
import com.travel.agent.infrastructure.persistence.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 对话应用服务。
 *
 * <p>职责：调 Agent、按条落库全量历史、首条消息建会话并触发 AI 标题。
 * userId 为空则后端生成（对标 DeepSeek：首条消息才落库）。
 */
@Service
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    private final TravelAgent travelAgent;
    private final ChatMessageRepository messageRepo;
    private final ChatSessionRepository sessionRepo;
    private final SessionTitleSummarizer titleSummarizer;

    @Autowired
    public ChatSessionService(TravelAgent travelAgent,
                              ChatMessageRepository messageRepo,
                              ChatSessionRepository sessionRepo,
                              SessionTitleSummarizer titleSummarizer) {
        this.travelAgent = travelAgent;
        this.messageRepo = messageRepo;
        this.sessionRepo = sessionRepo;
        this.titleSummarizer = titleSummarizer;
    }

    /** 处理一次对话；userId 为空表示新建会话。 */
    public ChatResult chat(String userId, String message) {
        String sessionId = (userId == null || userId.isBlank())
                ? UUID.randomUUID().toString()
                : userId;
        log.info("user-input sessionId={} message={}", sessionId, message);

        String reply = travelAgent.chat(sessionId, message);

        // 全量历史按条落库（不受滑窗截断）
        messageRepo.appendMessage(sessionId, "user", message);
        messageRepo.appendMessage(sessionId, "assistant", reply);

        boolean isNew = sessionRepo.upsertMeta(sessionId, "新对话", reply);
        if (isNew) {
            titleSummarizer.summarize(sessionId, message);
        }
        log.info("agent-reply sessionId={} reply={}", sessionId, reply);
        return new ChatResult(reply, sessionId);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -Dtest=ChatSessionServiceTest test`
Expected: 两个测试 PASS。

---

### Task 7: DTO + REST 接口扩展

**Files:**
- Modify: `src/main/java/com/travel/agent/interfaces/rest/dto/ChatRequest.java`
- Modify: `src/main/java/com/travel/agent/interfaces/rest/dto/ChatResponse.java`
- Create: `src/main/java/com/travel/agent/interfaces/rest/dto/ChatSessionVo.java`
- Create: `src/main/java/com/travel/agent/interfaces/rest/dto/ChatMessageVo.java`
- Create: `src/main/java/com/travel/agent/interfaces/rest/dto/RenameSessionRequest.java`
- Modify: `src/main/java/com/travel/agent/application/session/ChatSessionService.java`
- Modify: `src/main/java/com/travel/agent/interfaces/rest/ChatController.java`

**Interfaces:**
- Consumes: `ChatSessionRepository.listAll`、`ChatMessageRepository.findAllByUserId`、二者 rename/delete。
- Produces: REST 接口 `POST /api/chat`（返回 `{reply, sessionId}`）、`GET /api/chat/sessions`、`GET /api/chat/sessions/{userId}/messages`、`PATCH /api/chat/sessions/{userId}`、`DELETE /api/chat/sessions/{userId}`。

- [ ] **Step 1: ChatRequest userId 放宽为可空**

`ChatRequest.java` 删除 userId 的 `@NotBlank`（保留 message 的）：
```java
package com.travel.agent.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 对话请求 DTO。userId 可空：为空表示新建会话。 */
@Data
public class ChatRequest {

    /** 会话标识，用于多用户记忆隔离；为空则后端新建会话 */
    private String userId;

    /** 用户消息内容 */
    @NotBlank(message = "message 不能为空")
    private String message;
}
```

- [ ] **Step 2: ChatResponse 加 sessionId**

```java
package com.travel.agent.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 对话响应 DTO。 */
@Data
@AllArgsConstructor
public class ChatResponse {

    /** 智能体回复内容 */
    private String reply;

    /** 会话标识（新建时由后端生成，前端需用于后续请求） */
    private String sessionId;
}
```

- [ ] **Step 3: 新建三个 VO / 请求 DTO**

`ChatSessionVo.java`：
```java
package com.travel.agent.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 会话视图（左侧列表）。时间已格式化为字符串。 */
@Data
@AllArgsConstructor
public class ChatSessionVo {
    private String userId;
    private String title;
    private String lastMessage;
    private String createTime;
    private String updateTime;
}
```
`ChatMessageVo.java`：
```java
package com.travel.agent.interfaces.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 消息视图（历史记录）。 */
@Data
@AllArgsConstructor
public class ChatMessageVo {
    private Long id;
    private String role;
    private String content;
    private String createTime;
}
```
`RenameSessionRequest.java`：
```java
package com.travel.agent.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 重命名会话请求。 */
@Data
public class RenameSessionRequest {
    @NotBlank(message = "title 不能为空")
    private String title;
}
```

- [ ] **Step 4: ChatSessionService 加列表/重命名/删除方法**

在 `ChatSessionService` 末尾（`chat` 方法之后、`return` 块之外）追加以下方法，并补 import：
```java
import com.travel.agent.infrastructure.persistence.entity.ChatMessageEntity;
import com.travel.agent.infrastructure.persistence.entity.ChatSessionEntity;
import com.travel.agent.interfaces.rest.dto.ChatMessageVo;
import com.travel.agent.interfaces.rest.dto.ChatSessionVo;
import java.time.format.DateTimeFormatter;
import java.util.List;

private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

public List<ChatSessionVo> listSessions() {
    return sessionRepo.listAll().stream()
            .map(this::toSessionVo)
            .toList();
}

public List<ChatMessageVo> listMessages(String userId) {
    return messageRepo.findAllByUserId(userId).stream()
            .map(this::toMessageVo)
            .toList();
}

public void renameSession(String userId, String title) {
    sessionRepo.renameTitle(userId, title);
}

public void deleteSession(String userId) {
    messageRepo.deleteByUserId(userId);
    sessionRepo.deleteByUserId(userId);
}

private ChatSessionVo toSessionVo(ChatSessionEntity e) {
    return new ChatSessionVo(
            e.getUserId(),
            e.getTitle(),
            e.getLastMessage(),
            e.getCreateTime() == null ? null : e.getCreateTime().format(FMT),
            e.getUpdateTime() == null ? null : e.getUpdateTime().format(FMT));
}

private ChatMessageVo toMessageVo(ChatMessageEntity e) {
    return new ChatMessageVo(
            e.getId(),
            e.getRole(),
            e.getContent(),
            e.getCreateTime() == null ? null : e.getCreateTime().format(FMT));
}
```

- [ ] **Step 5: ChatController 加接口**

全文替换为：
```java
package com.travel.agent.interfaces.rest;

import com.travel.agent.application.session.ChatResult;
import com.travel.agent.application.session.ChatSessionService;
import com.travel.agent.common.result.ApiResult;
import com.travel.agent.interfaces.rest.dto.ChatMessageVo;
import com.travel.agent.interfaces.rest.dto.ChatRequest;
import com.travel.agent.interfaces.rest.dto.ChatResponse;
import com.travel.agent.interfaces.rest.dto.ChatSessionVo;
import com.travel.agent.interfaces.rest.dto.RenameSessionRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 对话入口接口。
 *
 * <p>Web/小程序前端统一通过该接口与智能体交互。
 * 支持多会话：userId 为空时新建，会话与消息均落 MySQL。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatSessionService chatSessionService;

    @Autowired
    public ChatController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @PostMapping
    public ApiResult<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResult result = chatSessionService.chat(request.getUserId(), request.getMessage());
        return ApiResult.success(new ChatResponse(result.reply(), result.sessionId()));
    }

    @GetMapping("/sessions")
    public ApiResult<List<ChatSessionVo>> listSessions() {
        return ApiResult.success(chatSessionService.listSessions());
    }

    @GetMapping("/sessions/{userId}/messages")
    public ApiResult<List<ChatMessageVo>> listMessages(@PathVariable String userId) {
        return ApiResult.success(chatSessionService.listMessages(userId));
    }

    @PatchMapping("/sessions/{userId}")
    public ApiResult<Void> rename(@PathVariable String userId,
                                  @Valid @RequestBody RenameSessionRequest request) {
        chatSessionService.renameSession(userId, request.getTitle());
        return ApiResult.success(null);
    }

    @DeleteMapping("/sessions/{userId}")
    public ApiResult<Void> delete(@PathVariable String userId) {
        chatSessionService.deleteSession(userId);
        return ApiResult.success(null);
    }
}
```

- [ ] **Step 6: 编译 + 全量测试 + 格式化**

Run:
```powershell
mvn spotless:apply
mvn -q test
```
Expected: BUILD SUCCESS，全部单元测试 + 集成测试 PASS。

- [ ] **Step 7: 手工冒烟接口（后端独立）**

启动后端，用 curl/PowerShell 验证：
```powershell
# 新建会话对话
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/chat -ContentType 'application/json' -Body '{"message":"去东京玩三天"}'
# 取会话列表
Invoke-RestMethod -Uri http://localhost:8080/api/chat/sessions
# 取某会话消息（替换 userId）
Invoke-RestMethod -Uri http://localhost:8080/api/chat/sessions/<userId>/messages
```
Expected：首次返回 `sessionId`；列表非空；消息含 user/assistant 两条；稍等可见标题已被 AI 改写。

---

### Task 8: 前端类型与 API 封装

**Files:**
- Modify: `web/src/types/chat.ts`
- Modify: `web/src/api/chat.ts`

**Interfaces:**
- Produces: 前端 API `sendChat` 返回 `{reply, sessionId}`；`getSessions`/`getSessionMessages`/`renameSession`/`deleteSession`。

- [ ] **Step 1: 更新 types/chat.ts**

全文替换为：
```typescript
// 与后端 DTO 对齐的类型定义

/** 消息角色：user 用户，assistant 智能体 */
export type MessageRole = 'user' | 'assistant'

/** 单条对话消息 */
export interface ChatMessage {
  id: number
  role: MessageRole
  content: string
  createTime: string
}

/** 对话请求（对应后端 ChatRequest）；userId 可空，为空表示新建会话 */
export interface ChatRequest {
  userId?: string
  message: string
}

/** 后端统一返回结构（对应后端 ApiResult） */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

/** 对话响应载荷（对应后端 ChatResponse） */
export interface ChatResponse {
  reply: string
  sessionId: string
}

/** 会话视图（对应后端 ChatSessionVo） */
export interface ChatSessionVo {
  userId: string
  title: string
  lastMessage: string
  createTime: string
  updateTime: string
}

/** 消息视图（对应后端 ChatMessageVo） */
export interface ChatMessageVo {
  id: number
  role: MessageRole
  content: string
  createTime: string
}

/** 重命名请求 */
export interface RenameSessionRequest {
  title: string
}
```

- [ ] **Step 2: 更新 api/chat.ts**

全文替换为：
```typescript
import instance, { request } from './request'
import type {
  ApiResult,
  ChatMessageVo,
  ChatRequest,
  ChatResponse,
  ChatSessionVo,
  RenameSessionRequest,
} from '@/types/chat'

/** 发送对话消息，返回智能体回复 + 会话标识 */
export async function sendChat(payload: ChatRequest): Promise<ChatResponse> {
  return request<ChatResponse>(instance.post<ApiResult<ChatResponse>>('/chat', payload))
}

/** 拉取全部会话列表（启动填左侧栏） */
export async function getSessions(): Promise<ChatSessionVo[]> {
  return request<ChatSessionVo[]>(instance.get<ApiResult<ChatSessionVo[]>>('/chat/sessions'))
}

/** 拉取某会话全部消息 */
export async function getSessionMessages(userId: string): Promise<ChatMessageVo[]> {
  return request<ChatMessageVo[]>(
    instance.get<ApiResult<ChatMessageVo[]>>(`/chat/sessions/${userId}/messages`),
  )
}

/** 重命名会话 */
export async function renameSession(userId: string, title: string): Promise<void> {
  const payload: RenameSessionRequest = { title }
  await request<null>(
    instance.patch<ApiResult<null>>(`/chat/sessions/${userId}`, payload),
  )
}

/** 删除会话及其消息 */
export async function deleteSession(userId: string): Promise<void> {
  await request<null>(instance.delete<ApiResult<null>>(`/chat/sessions/${userId}`))
}
```

- [ ] **Step 3: 类型检查**

Run（在 `web/`）: `npm run type-check`
Expected: 无报错。

---

### Task 9: 前端 store 重构与视图适配

**Files:**
- Modify: `web/src/stores/chat.ts`
- Modify: `web/src/views/ChatView.vue`

**Interfaces:**
- Produces: store `init()` / `newChat()` / 改造后的 `sendMessage` / `selectSession` / `renameSession` / `removeSession`；localStorage 仅缓存消息。

- [ ] **Step 1: 重写 stores/chat.ts**

全文替换为：
```typescript
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  deleteSession,
  getSessionMessages,
  getSessions,
  renameSession,
  sendChat,
} from '@/api/chat'
import type { ChatMessage, ChatSessionVo } from '@/types/chat'

const CACHE_KEY = 'travel-agent:chat-state'

/**
 * 对话状态管理（组合式 store）。
 *
 * 设计要点：
 * - 后端为唯一真相源：会话列表来自 getSessions()，消息来自 getSessionMessages()。
 * - localStorage 仅缓存已加载会话的消息，切换会话命中缓存免重复请求。
 * - 新建对话：activeUserId 为空即新建态，首条消息发出后才落库（对标 DeepSeek）。
 * - 不可变更新（展开运算符）。
 */
export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSessionVo[]>([])
  const activeUserId = ref<string>('')
  const messagesByUser = ref<Record<string, ChatMessage[]>>(loadCache())
  const loading = ref(false)

  const activeMessages = computed<ChatMessage[]>(
    () => messagesByUser.value[activeUserId.value] ?? [],
  )

  /** 启动初始化：拉会话列表，默认选最近会话或进入新建态 */
  async function init(): Promise<void> {
    const list = await getSessions()
    sessions.value = list
    if (list.length > 0) {
      await selectSession(list[0].userId)
    } else {
      activeUserId.value = ''
    }
  }

  /** 新建对话：只切到新建态，不落库（首条消息才创建会话） */
  function newChat(): void {
    activeUserId.value = ''
  }

  /** 切换会话；命中缓存则免请求 */
  async function selectSession(userId: string): Promise<void> {
    activeUserId.value = userId
    if (messagesByUser.value[userId]) return
    const messages = await getSessionMessages(userId)
    messagesByUser.value = { ...messagesByUser.value, [userId]: messages }
    persistCache()
  }

  /** 发送消息；新建态下首次发消息会建会话 */
  async function sendMessage(content: string): Promise<void> {
    const isNewChat = !activeUserId.value
    const userId = activeUserId.value
    loading.value = true
    try {
      const reply = await sendChat(userId ? { userId, message: content } : { message: content })
      const sessionId = reply.sessionId
      if (isNewChat) {
        activeUserId.value = sessionId
        const newSession: ChatSessionVo = {
          userId: sessionId,
          title: content.slice(0, 12),
          lastMessage: reply.reply,
          createTime: now(),
          updateTime: now(),
        }
        sessions.value = [newSession, ...sessions.value]
      } else {
        sessions.value = sessions.value.map((session) =>
          session.userId === sessionId ? { ...session, lastMessage: reply.reply } : session,
        )
      }
      const list = messagesByUser.value[sessionId] ?? []
      const appended: ChatMessage[] = [
        ...list,
        { id: Date.now(), role: 'user', content, createTime: now() },
        { id: Date.now() + 1, role: 'assistant', content: reply.reply, createTime: now() },
      ]
      messagesByUser.value = { ...messagesByUser.value, [sessionId]: appended }
      persistCache()
    } finally {
      loading.value = false
    }
  }

  /** 重新生成最后一条 AI 回复：移除它，重发上一条用户消息 */
  async function regenerate(): Promise<void> {
    const userId = activeUserId.value
    if (!userId || loading.value) return
    const list = messagesByUser.value[userId] ?? []
    let lastUserContent = ''
    for (let index = list.length - 1; index >= 0; index--) {
      if (list[index].role === 'user') {
        lastUserContent = list[index].content
        break
      }
    }
    if (!lastUserContent) return
    const trimmed =
      list.length > 0 && list[list.length - 1].role === 'assistant' ? list.slice(0, -1) : list
    messagesByUser.value = { ...messagesByUser.value, [userId]: trimmed }

    loading.value = true
    try {
      const reply = await sendChat({ userId, message: lastUserContent })
      const appended = [
        ...trimmed,
        { id: Date.now(), role: 'assistant' as const, content: reply.reply, createTime: now() },
      ]
      messagesByUser.value = { ...messagesByUser.value, [userId]: appended }
      sessions.value = sessions.value.map((session) =>
        session.userId === userId ? { ...session, lastMessage: reply.reply } : session,
      )
      persistCache()
    } finally {
      loading.value = false
    }
  }

  /** 重命名会话 */
  async function renameSessionWithTitle(userId: string, title: string): Promise<void> {
    await renameSession(userId, title)
    sessions.value = sessions.value.map((session) =>
      session.userId === userId ? { ...session, title } : session,
    )
  }

  /** 删除会话及其消息 */
  async function removeSession(userId: string): Promise<void> {
    await deleteSession(userId)
    sessions.value = sessions.value.filter((session) => session.userId !== userId)
    const rest = { ...messagesByUser.value }
    delete rest[userId]
    messagesByUser.value = rest
    persistCache()
    if (activeUserId.value === userId) {
      activeUserId.value = sessions.value[0]?.userId ?? ''
    }
  }

  function now(): string {
    return new Date().toLocaleTimeString('zh-CN', { hour12: false })
  }

  function persistCache(): void {
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(messagesByUser.value))
    } catch {
      // 存储满或不可用，忽略
    }
  }

  function loadCache(): Record<string, ChatMessage[]> {
    try {
      const raw = localStorage.getItem(CACHE_KEY)
      return raw ? (JSON.parse(raw) as Record<string, ChatMessage[]>) : {}
    } catch {
      return {}
    }
  }

  return {
    sessions,
    activeUserId,
    loading,
    activeMessages,
    init,
    newChat,
    selectSession,
    sendMessage,
    regenerate,
    renameSessionWithTitle,
    removeSession,
  }
})
```

- [ ] **Step 2: 适配 ChatView.vue**

`<script setup>` 中 `onMounted` 改为调用 `store.init()`，新建按钮改 `store.newChat()`：
```typescript
onMounted(() => {
  store.init()
})
```
模板内「新建会话」按钮：
```html
<a-button type="primary" block @click="store.newChat()">
  <template #icon><PlusOutlined /></template>
  新建会话
</a-button>
```
`MessageBubble` 的 `:key` 从 `index` 改为 `msg.id`：
```html
<MessageBubble
  v-for="msg in store.activeMessages"
  :key="msg.id"
  :message="msg"
  @regenerate="store.regenerate"
/>
```
（删除原 `onMounted` 里 `createSession` 相关分支；保留滚动逻辑。）

- [ ] **Step 3: 类型检查 + 构建**

Run（在 `web/`）:
```bash
npm run type-check
npm run build
```
Expected: 无报错。

---

### Task 10: 端到端验证

**Files:** 无（验证 only）

- [ ] **Step 1: 启动后端 + 前端**

```powershell
# 后端
$env:JAVA_HOME = "E:\File-work\Java_Detail\jdk-21"
mvn spring-boot:run
# 前端（另一终端，web/）
npm run dev
```

- [ ] **Step 2: 场景一：刷新不丢 + AI 标题**

浏览器开 `http://localhost:5173`，发两条消息 → 刷新页面 →
Expected：左侧列表保留、历史消息完整、标题已从"新对话"变为 AI 总结词。

- [ ] **Step 3: 场景二：后端重启 LLM 不失忆**

发几条消息后 Ctrl+C 停后端 → `mvn spring-boot:run` 重启 → 在前端继续追问上文内容 →
Expected：LLM 能引用前文（上下文从 MySQL 恢复）。

- [ ] **Step 4: 场景三：新建对话不残留空会话**

点"新建会话"但不发消息 → 刷新 →
Expected：左侧无新增空会话项。

- [ ] **Step 5: 场景四：重命名 + 删除**

在左侧对某会话重命名/删除 → 刷新 →
Expected：重命名持久；删除后会话与消息消失。

- [ ] **Step 6: 收尾格式化**

```powershell
mvn spotless:apply
mvn -q test
```
Expected：BUILD SUCCESS，全绿。

---

## Self-Review 记录

- **Spec 覆盖**：①数据模型（schema.sql，Task1）；②方案 D 时序（PersistentChatMemoryStore no-op + service 按条落库，Task4+6）；③后端生成 id + 首条才落库（ChatSessionService，Task6）；④localStorage 仅消息缓存（store，Task9）；AI 标题（SessionTitleSummarizer，Task5）；REST 接口（Task7）。全部覆盖。
- **占位符**：`application-local.yml` 与 `application-test.yml` 的 `<本机MySQL密码>` 是需用户填的真实值（已在 Global Constraints 标注），非 TBD。
- **类型一致性**：`ChatResult(reply, sessionId)`（Task6）↔ `ChatResponse(reply, sessionId)`（Task7）↔ 前端 `ChatResponse`（Task8）↔ store `reply.sessionId`（Task9）链路一致；`findRecentByUserId(userId, n)` 签名 Task3 定义、Task4 消费，一致。
