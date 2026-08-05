# Docker 容器化加固设计（档位②）

- 日期：2026-07-20
- 范围：仅后端 Docker（前端 `web/` 本期不碰）
- 档位：② 修复 + 标准加固
- 状态：已与用户对齐，待 spec 复核

## 1. 背景与现状

项目已有如下 Docker 资产（质量尚可，但存在阻断性 bug 与加固缺口）：

- `Dockerfile`（根目录）：多阶段构建，`maven:3.9-eclipse-temurin-21` 编译 → `eclipse-temurin:21-jre` 运行，**仅打包后端 jar**。
- `.dockerignore`：已排除 `target/`、`web/dist/`、`web/node_modules/`、`.git/`、IDE 目录、`logs/`、`.env`、`application-local.yml`、`docker/`。
- `docker/docker-compose.yml`：编排 Milvus 三件套（etcd + minio + milvus）+ `app`。一期 `app` 用内存向量库、不依赖 Milvus。
- `docker/docker-compose-milvus.yml`：独立启动 Milvus。
- `docker/maven-settings.xml`：阿里云镜像加速。

后端关键事实：

- `pom.xml` 已引入 `spring-boot-starter-actuator`；`application.yml` 已暴露 `management.endpoints.web.exposure.include: health,info,metrics`，可直接用于 healthcheck。
- `application.yml` 配置 `spring.profiles.active: dev,local`；`travel.llm.api-key: ${LLM_API_KEY}`、`travel.embedding.api-key: ${EMBEDDING_API_KEY}`（均无默认值占位）。
- `src/main/resources/application-local.yml` 含真实 DeepSeek / DashScope 密钥（已被 `.gitignore` 忽略，但物理存在于 src 下）。

## 2. 目标与非目标

### 目标

1. 修复两个阻断性 bug，使 `docker build` 能成功、镜像不含密钥。
2. 对后端镜像与 compose 编排做标准加固（非 root、JVM 容器调优、healthcheck、restart 策略）。
3. 补齐部署所需的元资产（`.env.example`、部署 README）。

### 非目标（本期不做）

- 前端 `web/` 的容器化（前端静态资源构建、nginx 托管等）。
- 新增 `application-prod.yml` 或调整 `spring.profiles.active` 声明。
- 合并 / 重构两个 compose 文件。
- CI/CD、多架构构建、镜像漏洞扫描、资源限额、日志持久化卷等生产化项（属档位③）。

## 3. 关键决策

| 维度 | 决策 | 理由 |
|---|---|---|
| 范围 | 仅修复 + 加固后端 Docker | 用户选择；前端缺口留待后续 |
| Profile | 不改 profile 声明；镜像走 `dev,local`，但 `local` 文件靠 `.dockerignore` 挡在镜像外，密钥走环境变量注入 | 最小改动；Spring Boot 找不到 local 文件会静默忽略，不报错 |
| Compose 结构 | 保留两个 compose 文件，仅各自加固 | 用户选择；符合"精准修改"原则，不重构 |
| 加固强度 | 档位②（修复 + 标准加固） | 与"仅修复+加固后端"语义最贴合；不引入运维复杂度 |

## 4. 详细设计

### 4.1 必修 Bug

#### 4.1.1 `.dockerignore` 与 Dockerfile 冲突

**问题**：`.dockerignore` 排除了 `docker/`，但 `Dockerfile` 第 5 行 `COPY docker/maven-settings.xml /root/.m2/settings.xml` 会因整个目录被排除而失败 → 当前 `docker build` 直接报错。

**修法**：将 `.dockerignore` 末尾的 `docker/` 替换为例外放行模式：

```
docker/*
!docker/maven-settings.xml
```

效果：排除 `docker/` 下所有文件（compose 的 yml 不进构建上下文，保持干净），但单独放行构建必需的 `maven-settings.xml`。

#### 4.1.2 密钥泄露进镜像

**问题**：`.dockerignore` 中的 `application-local.yml` 只匹配项目根目录，挡不住 `src/main/resources/application-local.yml`（含真实密钥）。该文件会被 `COPY src ./src` 带进构建上下文并打进 jar。

**修法**：将该行改为递归匹配：

```
**/application-local.yml
```

效果：所有目录下的 `application-local.yml` 都被挡在构建上下文外，jar 不含密钥。镜像里 `local` profile 找不到对应文件，Spring Boot 静默忽略；密钥完全由环境变量 `LLM_API_KEY` / `EMBEDDING_API_KEY` 注入（`application.yml` 中已是 `${...}` 占位）。

> **副作用（预期）**：`application.yml` 中 `${LLM_API_KEY}` 无默认值，若 compose / `.env` 未注入，启动会因占位符解析失败而 fail-fast。这是期望行为（fail fast 优于静默用错配置），需在 README 中明确提示。

### 4.2 Dockerfile 加固

在现有多阶段构建的**运行阶段**（`FROM eclipse-temurin:21-jre` 之后）追加。

> **顺序约定**：所有需要 root 权限的操作（apt-get、useradd、chown、COPY）必须在 `USER appuser` 之前完成；HEALTHCHECK 与 ENTRYPOINT 在切换用户之后。

1. **安装 curl**（供 HEALTHCHECK 使用，约 +5MB）：
   - `apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*`
2. **非 root 用户**：
   - `useradd -r -s /bin/false appuser`
   - `chown -R appuser:appuser /app`（在 `COPY --from=builder /build/target/*.jar app.jar` 之后执行，确保 jar 属主正确）
   - `USER appuser`
   - 说明：EXPOSE 8080 > 1024，非 root 用户可正常绑定。
3. **JVM 容器调优**：
   - ENTRYPOINT 改为：`["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]`
   - `MaxRAMPercentage`：按容器内存限制自动配置堆。
   - `ExitOnOutOfMemoryError`：OOM 主动退出，便于 compose `restart` 拉起。
4. **HEALTHCHECK**：
   - `HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 CMD curl -fsS http://localhost:8080/actuator/health || exit 1`
   - 依赖 4.2.1 安装的 curl 与已暴露的 `/actuator/health` 端点。

> 构建阶段（builder）保持现状不变；`go-offline` 层缓存策略沿用。

### 4.3 compose 加固

#### 4.3.1 `docker/docker-compose.yml` 的 `app` 服务

- 新增 `restart: unless-stopped`。
- 新增 `healthcheck`（与 Dockerfile 的 HEALTHCHECK 对齐，compose 层显式声明便于编排读取状态）：
  ```yaml
  healthcheck:
    test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]
    interval: 30s
    timeout: 5s
    start_period: 40s
    retries: 3
  ```
- 二期切 Milvus 时，把注释里的 `# depends_on: [milvus]` 改为带就绪条件的形式（本期保持注释，不启用）。

#### 4.3.2 `docker/docker-compose.yml` 的 Milvus 三件套

- 给 `milvus`、`minio` 各加一条**最简端口探测** healthcheck 与 `restart: unless-stopped`。
- `etcd` 仅加 `restart: unless-stopped`，不加 healthcheck（一期 app 不依赖 Milvus，避免过度）。
- 具体探测命令：`milvus` 探测 9091（metrics/health 端口）、`minio` 探测 9000。

#### 4.3.3 `docker/docker-compose-milvus.yml`

- 仅给三个服务各补 `restart: unless-stopped`，**不引入 healthcheck**（该文件用于独立拉起 Milvus 调试，保持轻量）。

### 4.4 新增资产

#### 4.4.1 `.env.example`（项目根）

内容（注释说明 + 两个变量）：

```
# 复制本文件为 .env 并填入真实密钥；.env 已被 .gitignore 忽略
# DeepSeek（兼容 OpenAI 协议）：https://platform.deepseek.com
LLM_API_KEY=

# 阿里云 DashScope（千问 Embedding）：https://dashscope.console.aliyun.com
EMBEDDING_API_KEY=
```

#### 4.4.2 `docker/README.md`

5 步部署流程：

1. 在项目根复制 `.env.example` 为 `.env`，填入 `LLM_API_KEY` / `EMBEDDING_API_KEY`。
2. 启动应用（一期内存向量库，不启 Milvus）：`docker compose -f docker/docker-compose.yml up -d app`。
3. 验证健康：`curl http://localhost:8080/actuator/health`（期望 `{"status":"UP"}`）。
4. 查看日志：`docker compose -f docker/docker-compose.yml logs -f app`。
5. 停止与清理：`docker compose -f docker/docker-compose.yml down`（加 `-v` 一并清理 Milvus 数据卷）。

并在文末附"二期启用 Milvus"的简短说明（取消 `app` 的 `depends_on` 注释、改 `EmbeddingStoreConfig` 一处 `@Bean`，详见上级 CLAUDE.md）。

## 5. 改动文件清单

| 文件 | 操作 | 说明 |
|---|---|---|
| `.dockerignore` | 修改 | `docker/` → `docker/*` + `!docker/maven-settings.xml`；`application-local.yml` → `**/application-local.yml` |
| `Dockerfile` | 修改 | 运行阶段加 curl、非 root 用户、JVM 调优、HEALTHCHECK |
| `docker/docker-compose.yml` | 修改 | app 加 restart + healthcheck；Milvus/minio 加最简 healthcheck + restart；etcd 加 restart |
| `docker/docker-compose-milvus.yml` | 修改 | 三服务各加 restart |
| `.env.example` | 新增 | 密钥变量模板 |
| `docker/README.md` | 新增 | 5 步部署流程 |

不改动：`pom.xml`、`application.yml`、`application-dev.yml`、Java 源码、前端 `web/`、`docker/maven-settings.xml`。

## 6. 验证方式

1. **构建成功**：`docker build -t travel-agent:test .` 在项目根执行，无 `COPY failed` 报错（验证 4.1.1）。
2. **镜像无密钥**：构建后把镜像内 jar 导出到宿主机检查——`docker run --rm travel-agent:test cat /app/app.jar > /tmp/app.jar && unzip -l /tmp/app.jar | grep application-local`，期望无输出（验证 4.1.2；不依赖容器内解压工具）。
3. **启动 + 健康**：用 `.env` 注入密钥后 `docker compose -f docker/docker-compose.yml up -d app`，等 `start_period` 后 `docker compose ... ps` 显示 `healthy`，且 `curl localhost:8080/actuator/health` 返回 `{"status":"UP"}`。
4. **非 root 生效**：`docker run --rm travel-agent:test id` 期望 `uid` 非 0。
5. **fail-fast**：不注入密钥启动，期望容器因占位符解析失败退出（`docker compose logs app` 含相关异常）。

## 7. 风险与取舍

- **curl 增加约 5MB 镜像体积**：换取标准、可靠的 HEALTHCHECK；已评估备选（wget / 仅 compose 层探测），curl 准确性最佳，用户已认可档位②。
- **fail-fast 可能被误判为"启动失败"**：靠 README 明确提示"必须先建 `.env`"来规避。
- **Milvus 三件套 healthcheck 较粗（端口探测）**：一期 app 不依赖 Milvus，深度健康检查留待档位③。
- **`docker-compose.yml` 与 `-milvus.yml` 仍有重复**：本期按用户决策保留双文件、仅加固，重复成本可接受。
