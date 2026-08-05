# Docker 容器化加固（档位②）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复后端 Docker 两个阻断性 bug 并完成标准加固，使 `docker compose up` 能一键拉起健康、非 root、无密钥泄露的后端服务。

**Architecture:** 沿用现有「多阶段 Dockerfile + 双 compose 文件」结构，不重构、不新增 profile、不碰前端。改动集中在 `.dockerignore` / `Dockerfile` / 两个 compose 文件 + 新增 `.env.example` 与 `docker/README.md`。

**Tech Stack:** Docker、Docker Compose v2、Spring Boot Actuator（已暴露 `/actuator/health`）、eclipse-temurin:21-jre、PowerShell（Windows 执行环境）。

**对应 Spec:** `docs/superpowers/specs/2026-07-20-docker-hardening-design.md`

## Global Constraints

- **构建在容器内进行**：构建阶段 `maven:3.9-eclipse-temurin-21`，运行阶段 `eclipse-temurin:21-jre`（均 JDK 21，与 `pom.xml` 的 `java.version=21` 对齐）。宿主机无需设 `JAVA_HOME`、无需本地 Maven。
- **Maven 加速**：构建用 `docker/maven-settings.xml`（阿里云镜像），由 `.dockerignore` 例外放行。
- **密钥只走环境变量**：`LLM_API_KEY` / `EMBEDDING_API_KEY`。`application.yml` 中为无默认值占位符，缺失则启动 fail-fast（预期行为，不是 bug）。
- **`application-local.yml` 不得进镜像**：`.dockerignore` 用 `**/application-local.yml` 递归排除。
- **非 git 仓库**：本计划无 `git commit` 步骤；每个任务以「验证检查点」收尾，验证通过即视为该任务完成。
- **执行环境**：Windows PowerShell；`docker build` 在项目根（`travel-agent/`）执行；compose 文件用 `-f docker/docker-compose.yml` 指定。
- **不改动**：`pom.xml`、`application*.yml`、Java 源码、`web/`、`docker/maven-settings.xml`、`volumes` 顶层声明。

---

## File Structure

| 文件 | 操作 | 责任 |
|---|---|---|
| `.dockerignore` | 修改 | 放行 `docker/maven-settings.xml`；递归排除 `application-local.yml` |
| `Dockerfile` | 修改 | 运行阶段加 curl、非 root 用户、JVM 调优、HEALTHCHECK |
| `docker/docker-compose.yml` | 修改 | app 加 restart + healthcheck；minio/milvus 加 restart + 端口 healthcheck；etcd 加 restart |
| `docker/docker-compose-milvus.yml` | 修改 | 三服务各加 restart（不引入 healthcheck） |
| `.env.example` | 新增 | 密钥变量模板（`.env` 已被 `.gitignore` 忽略） |
| `docker/README.md` | 新增 | 5 步部署流程 + 二期 Milvus 说明 |

任务间依赖链：Task 1（.dockerignore 可 build）→ Task 2（镜像加固）→ Task 3/4（compose 加固）→ Task 5（部署资产）→ Task 6（端到端验收，依赖所有前置）。

---

## Task 1: 修复 .dockerignore（冲突 + 密钥泄露）

**Files:**
- Modify: `.dockerignore`

**依赖（Consumes）：** 无（首个任务）。
**产物（Produces）：** 一个「`docker build` 能跑到 `COPY docker/maven-settings.xml` 成功、且 `src/main/resources/application-local.yml` 不进构建上下文」的 `.dockerignore`。

- [ ] **Step 1: 把密钥排除规则改为递归匹配**

打开 `.dockerignore`，将这一行：

```
application-local.yml
```

改为：

```
# 递归匹配所有目录下的 local 配置（含 src/main/resources，防密钥打进 jar）
**/application-local.yml
```

- [ ] **Step 2: 把 `docker/` 排除改为例外放行 maven-settings.xml**

将 `.dockerignore` 末尾这三行：

```
# Docker 自身（避免循环）
Dockerfile
.dockerignore
docker/
```

改为：

```
# Docker 自身（避免循环）
Dockerfile
.dockerignore
# Docker 编排文件不进构建上下文；例外放行构建必需的 maven-settings.xml
docker/*
!docker/maven-settings.xml
```

- [ ] **Step 3: 核对最终内容**

打开 `.dockerignore`，确认完整内容为：

```
# 构建产物
target/
web/dist/
web/node_modules/

# 版本控制与 IDE
.git/
.idea/
.vscode/
*.iml

# 日志与本地配置（含密钥，勿入镜像）
logs/
*.log
.env
# 递归匹配所有目录下的 local 配置（含 src/main/resources，防密钥打进 jar）
**/application-local.yml

# Docker 自身（避免循环）
Dockerfile
.dockerignore
# Docker 编排文件不进构建上下文；例外放行构建必需的 maven-settings.xml
docker/*
!docker/maven-settings.xml
```

- [ ] **Step 4: 验证构建不再 COPY failed（验证检查点 ①）**

在项目根执行（首次构建需下载依赖，约 3-8 分钟；后续任务复用层缓存）：

```powershell
docker build -t travel-agent:test .
```

Expected: 构建成功，**不再出现** `COPY failed: forbidden path outside of the build context` 或 `docker/maven-settings.xml: not found` 之类报错；末尾输出 `naming to docker.io/library/travel-agent:test`。

- [ ] **Step 5: 验证镜像不含密钥文件（验证检查点 ②）**

```powershell
docker run --rm --entrypoint sh travel-agent:test -c "if grep -aq application-local /app/app.jar; then echo SECRET_LEAKED; else echo CLEAN; fi"
```

> 必须用 `--entrypoint sh` 覆盖镜像 ENTRYPOINT（`java -jar`），否则 `sh -c ...` 会被当作参数传给 `java`，验证失效。

Expected: 输出 `CLEAN`（说明 `application-local.yml` 未被打进 jar）。

---

## Task 2: 加固 Dockerfile（curl + 非 root + JVM 调优 + HEALTHCHECK）

**Files:**
- Modify: `Dockerfile`（仅运行阶段，构建阶段不动）

**依赖（Consumes）：** Task 1 的 `.dockerignore`（保证可 build）。
**产物（Produces）：** 镜像含 curl、以 `appuser` 运行、带 HEALTHCHECK 与 JVM 容器参数。

- [ ] **Step 1: 用以下完整内容覆盖 `Dockerfile`**

```dockerfile
# ===== 构建阶段：Maven + JDK 21 编译打包 =====
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build
# 使用阿里云镜像加速依赖下载（国内构建必备）
COPY docker/maven-settings.xml /root/.m2/settings.xml
# 先拷 pom，利用 Docker 层缓存避免每次重下依赖
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

# ===== 运行阶段：精简 JRE 镜像 =====
FROM eclipse-temurin:21-jre
WORKDIR /app

# 安装 curl（供 HEALTHCHECK 使用；装完清理 apt 缓存减体积）
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 拷贝构建产物（此时仍为 root，便于设属主）
COPY --from=builder /build/target/*.jar app.jar

# 非 root 用户运行（EXPOSE 8080 > 1024，非 root 可绑定）
RUN useradd -r -s /bin/false appuser \
    && chown -R appuser:appuser /app
USER appuser

EXPOSE 8080

# 容器内 JVM 调优：按容器内存上限自动配堆；OOM 主动退出便于 compose restart 拉起
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

# 健康检查：依赖 actuator /actuator/health（application.yml 已暴露 health 端点）
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
```

- [ ] **Step 2: 重新构建镜像**

```powershell
docker build -t travel-agent:test .
```

Expected: 构建成功。

- [ ] **Step 3: 验证以非 root 运行（验证检查点 ①）**

```powershell
docker run --rm --entrypoint id travel-agent:test
```

> 用 `--entrypoint id` 覆盖，避免触发 `java -jar` 启动。

Expected: 输出形如 `uid=NNN(appuser) gid=NNN(appuser) groups=NNN(appuser)`，**uid 不是 0**。

- [ ] **Step 4: 验证 HEALTHCHECK 已写入镜像（验证检查点 ②）**

```powershell
docker inspect --format='{{json .Config.Healthcheck.Test}}' travel-agent:test
```

Expected: 输出含 `CMD-SHELL` 与 `curl -fsS http://localhost:8080/actuator/health`（非 `null`）。

- [ ] **Step 5: 验证 JVM 参数已写入 ENTRYPOINT（验证检查点 ③）**

```powershell
docker inspect --format='{{json .Config.Entrypoint}}' travel-agent:test
```

Expected: 输出包含 `MaxRAMPercentage=75.0` 与 `ExitOnOutOfMemoryError`。

---

## Task 3: 加固 docker/docker-compose.yml

**Files:**
- Modify: `docker/docker-compose.yml`

**依赖（Consumes）：** Task 2 的镜像（app healthcheck 用 curl，curl 已在镜像内）。
**产物（Produces）：** app 带 restart + healthcheck；minio/milvus 带 restart + 端口 healthcheck；etcd 带 restart。

- [ ] **Step 1: 用以下完整内容覆盖 `docker/docker-compose.yml`**

```yaml
# 一键启动：Milvus 向量库 + Java 应用
#
# 用法（需先安装 Docker Desktop）：
#   1. 项目根创建 .env 文件（复制 .env.example），填入：
#        LLM_API_KEY=你的 DeepSeek Key
#        EMBEDDING_API_KEY=你的 DashScope Key
#   2. 启动全部：     docker compose -f docker/docker-compose.yml up -d
#   3. 仅启动应用：   docker compose -f docker/docker-compose.yml up -d app
#   4. 仅启动 Milvus：docker compose -f docker/docker-compose.yml up -d milvus
services:
  # ===== Milvus 向量库（二期启用 Milvus 时使用；一期 app 用内存向量库，可不启）=====
  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    restart: unless-stopped
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
    volumes:
      - etcd_data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    restart: unless-stopped
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/minio_data
    command: minio server /minio_data
    # 最简端口探测：minio 监听 9000
    healthcheck:
      test: ["CMD-SHELL", "bash -c '</dev/tcp/localhost/9000' || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s

  milvus:
    image: milvusdb/milvus:v2.4.0
    restart: unless-stopped
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    ports:
      - "19530:19530"
      - "9091:9091"
    depends_on: [etcd, minio]
    # 最简端口探测：milvus 健康/metrics 端口 9091
    healthcheck:
      test: ["CMD-SHELL", "bash -c '</dev/tcp/localhost/9091' || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s

  # ===== Java 应用（一期用内存向量库，独立运行）=====
  app:
    build:
      context: ..
      dockerfile: Dockerfile
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      LLM_API_KEY: ${LLM_API_KEY}
      EMBEDDING_API_KEY: ${EMBEDDING_API_KEY}
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 40s
    # 一期 app 用内存向量库，不依赖 Milvus；二期切 Milvus 时改为带就绪条件的依赖：
    # depends_on:
    #   milvus:
    #     condition: service_healthy

volumes:
  etcd_data:
  minio_data:
```

> 说明：minio/milvus 均基于 ubuntu，含 `bash`，可用 `/dev/tcp` 端口探测，无需额外装 curl。etcd 按 spec 仅加 restart。

- [ ] **Step 2: 校验 compose 语法（验证检查点）**

```powershell
docker compose -f docker/docker-compose.yml config --quiet
```

Expected: 无输出、退出码 0（若 `${LLM_API_KEY}` 未设，可能有一行 warning，属正常，不计为错误）。

- [ ] **Step 3: 抽查 app healthcheck 与 restart 已写入（验证检查点）**

```powershell
docker compose -f docker/docker-compose.yml config | Select-String -Pattern "restart:|healthcheck:|curl -fsS"
```

Expected: 至少出现 4 处 `restart:`、3 处 `healthcheck:`、1 处 `curl -fsS`。

---

## Task 4: 加固 docker/docker-compose-milvus.yml

**Files:**
- Modify: `docker/docker-compose-milvus.yml`

**依赖（Consumes）：** 无新依赖（独立 Milvus 调试文件）。
**产物（Produces）：** 三服务各带 `restart: unless-stopped`，不引入 healthcheck。

- [ ] **Step 1: 用以下完整内容覆盖 `docker/docker-compose-milvus.yml`**

保留原 `version: '3.8'` 与注释（精准修改，不删）；仅给三服务各加 `restart: unless-stopped`。

```yaml
# Milvus 向量库一键启动
# 用法：docker compose -f docker/docker-compose-milvus.yml up -d
# 说明：使用 Milvus Standalone 模式（单机），适合一期开发与测试。
#       生产环境建议改用 Milvus 集群模式或托管服务（Zilliz Cloud）。
version: '3.8'

services:
  etcd:
    image: quay.io/coreos/etcd:v3.5.5
    restart: unless-stopped
    environment:
      - ETCD_AUTO_COMPACTION_MODE=revision
      - ETCD_AUTO_COMPACTION_RETENTION=1000
      - ETCD_QUOTA_BACKEND_BYTES=4294967296
      - ETCD_SNAPSHOT_COUNT=50000
    volumes:
      - etcd_data:/etcd
    command: etcd -advertise-client-urls=http://127.0.0.1:2379 -listen-client-urls http://0.0.0.0:2379 --data-dir /etcd

  minio:
    image: minio/minio:RELEASE.2023-03-20T20-16-18Z
    restart: unless-stopped
    environment:
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
    volumes:
      - minio_data:/minio_data
    command: minio server /minio_data

  milvus:
    image: milvusdb/milvus:v2.4.0
    restart: unless-stopped
    command: ["milvus", "run", "standalone"]
    environment:
      ETCD_ENDPOINTS: etcd:2379
      MINIO_ADDRESS: minio:9000
    ports:
      - "19530:19530"   # gRPC 端口，应用连接用
      - "9091:9091"     # 健康检查 / metrics
    depends_on:
      - etcd
      - minio

volumes:
  etcd_data:
  minio_data:
```

- [ ] **Step 2: 校验语法（验证检查点）**

```powershell
docker compose -f docker/docker-compose-milvus.yml config --quiet
```

Expected: 无输出、退出码 0。

- [ ] **Step 3: 抽查 restart 已写入（验证检查点）**

```powershell
docker compose -f docker/docker-compose-milvus.yml config | Select-String -Pattern "restart:"
```

Expected: 出现 3 处 `restart: unless-stopped`。

---

## Task 5: 新增部署资产（.env.example + docker/README.md）

**Files:**
- Create: `.env.example`（项目根）
- Create: `docker/README.md`

**依赖（Consumes）：** 无（纯文档/模板）。
**产物（Produces）：** 用户可复制的密钥模板 + 5 步部署说明。

- [ ] **Step 1: 创建 `.env.example`**

写入项目根 `.env.example`，完整内容：

```
# 复制本文件为 .env 并填入真实密钥。
# .env 已被 .gitignore 忽略，切勿提交真实密钥。
#
# 对应 application.yml 中 travel.llm.api-key / travel.embedding.api-key 占位符。
# 缺失则容器启动 fail-fast（预期行为）。

# DeepSeek（兼容 OpenAI 协议），用于对话：https://platform.deepseek.com
LLM_API_KEY=

# 阿里云 DashScope（千问 Embedding），用于 RAG 向量化：https://dashscope.console.aliyun.com
EMBEDDING_API_KEY=
```

- [ ] **Step 2: 创建 `docker/README.md`**

写入 `docker/README.md`，完整内容：

````markdown
# Docker 部署（后端）

> 本期仅容器化后端；前端 `web/` 的容器化留待后续。一期后端使用内存向量库，Milvus 可选。

## 前置

- 已安装 Docker Desktop（含 Docker Compose v2）。
- 拿到两个 API Key：
  - DeepSeek（对话）：https://platform.deepseek.com
  - 阿里云 DashScope（千问 Embedding）：https://dashscope.console.aliyun.com

## 5 步部署

### 1. 配置密钥

在**项目根**复制模板并填值：

```powershell
Copy-Item .env.example .env
# 编辑 .env，填入 LLM_API_KEY 与 EMBEDDING_API_KEY
```

> `.env` 已被 `.gitignore` 忽略。若不填，容器启动会 fail-fast（占位符解析失败）。

### 2. 启动后端（一期内存向量库，不启 Milvus）

```powershell
docker compose -f docker/docker-compose.yml up -d app
```

首次启动会构建镜像（约 3-8 分钟）。`up -d` 后台运行。

### 3. 验证健康

等待约 40 秒（`start_period`），然后：

```powershell
docker compose -f docker/docker-compose.yml ps
```

期望 `app` 列 `STATUS` 为 `Up ... (healthy)`。

或直接探端点：

```powershell
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}
```

### 4. 查看日志

```powershell
docker compose -f docker/docker-compose.yml logs -f app
```

`Ctrl+C` 退出跟随。

### 5. 停止与清理

```powershell
# 停止并删容器（保留数据卷）
docker compose -f docker/docker-compose.yml down

# 连同 Milvus 数据卷一起清理（谨慎：会删除已灌入的向量数据）
docker compose -f docker/docker-compose.yml down -v
```

## 可选：启用 Milvus（二期）

二期切 Milvus 向量库时：

1. 启动 Milvus 三件套：

   ```powershell
   docker compose -f docker/docker-compose.yml up -d milvus
   ```

2. 取消 `docker/docker-compose.yml` 中 `app` 服务 `depends_on` 的注释，使 app 等 milvus 健康后再启动。

3. 改后端 `EmbeddingStoreConfig.embeddingStore()` 一处 `@Bean`，由 `InMemoryEmbeddingStore` 切到 Milvus（详见上级 `CLAUDE.md`）。

## 备注

- 镜像以非 root 用户 `appuser` 运行；JVM 按 `MaxRAMPercentage=75.0` 自动配堆。
- HEALTHCHECK 走 `/actuator/health`，`docker compose ps` 的 `(healthy)` 即据此判定。
````

- [ ] **Step 3: 验证两个文件已创建（验证检查点）**

```powershell
Get-ChildItem .env.example, docker/README.md | Select-Object Name, Length
```

Expected: 列出两个文件且 `Length` 均大于 0。

- [ ] **Step 4: 验证 `.env` 仍被忽略、`.env.example` 不被忽略（验证检查点）**

人工核对 `.gitignore` 第 39 行为 `.env`（仅忽略 `.env`，不影响 `.env.example`）。因当前非 git 仓库，此项为规则确认，确保后续 `git init` 时 `.env.example` 可入库、`.env` 不会。

---

## Task 6: 端到端验收（最终 gate）

**Files:** 无（仅运行验证）。

**依赖（Consumes）：** Task 1-5 全部完成；用户提供有效 DeepSeek / DashScope 密钥。
**产物（Produces）：** 一份「从建 `.env` 到 healthy 到 down」的完整运行证据。

> 本任务需要**真实可用的 API Key**。若暂无，可只执行 Step 1-2（建 .env + 启动），观察 fail-fast 行为作为部分验收，其余步骤待有 Key 后补跑。

- [ ] **Step 1: 建立 `.env`**

```powershell
Copy-Item .env.example .env
# 编辑 .env 填入真实 LLM_API_KEY 与 EMBEDDING_API_KEY
```

- [ ] **Step 2: 启动 app**

```powershell
docker compose -f docker/docker-compose.yml up -d app
```

Expected: 容器创建并启动；镜像不存在时自动构建。

- [ ] **Step 3: 等待 healthy 并核对状态（验证检查点 ①）**

```powershell
# 轮询直到状态出现（healthy），最多等约 90 秒
docker compose -f docker/docker-compose.yml ps
```

Expected: `app` 的 `STATUS` 为 `Up X seconds (healthy)`。

- [ ] **Step 4: 健康端点可用（验证检查点 ②）**

```powershell
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`。

- [ ] **Step 5: 业务接口可达（验证检查点 ③，可选）**

```powershell
curl http://localhost:8080/actuator/info
```

Expected: 返回 200（info 端点已暴露；返回体可能为空 `{}`，状态码 200 即可）。

- [ ] **Step 6: 反向验证 fail-fast（验证检查点 ④，可选）**

另开一个**完全不传密钥**的运行，确认占位符无法解析会 fail-fast：

```powershell
docker run --rm -p 8081:8080 travel-agent:test
```

> 不要用 `-e LLM_API_KEY=`（空字符串会被占位符解析为 `""`，不报错）；必须完全不传该变量，`${LLM_API_KEY}` 才会解析失败。

Expected: 启动日志含 `Could not resolve placeholder 'LLM_API_KEY'`（或同类 IllegalArgumentException），进程自动退出。这印证「密钥只能走环境变量注入」。

- [ ] **Step 7: 停止与清理**

```powershell
docker compose -f docker/docker-compose.yml down
```

Expected: 容器停止并移除，退出码 0。

---

## Self-Review 结论

- **Spec 覆盖**：4.1.1→Task1 Step1-2；4.1.2→Task1 Step1；4.2→Task2；4.3.1/4.3.2→Task3；4.3.3→Task4；4.4.1→Task5 Step1；4.4.2→Task5 Step2；验证方式（spec 第6节 5 条）→ 散布于各 Task 验证检查点与 Task6。全覆盖。
- **Placeholder 扫描**：无 TBD/TODO；每步含完整文件内容或精确命令与 expected。
- **类型/命名一致**：用户名 `appuser`、镜像标签 `travel-agent:test`、端口 `8080/9091/9000/19530`、端点 `/actuator/health` 在所有任务中一致。
- **环境适配**：所有命令为 PowerShell 可执行形式；二进制 jar 校验用容器内 `grep -aq`（避免 PowerShell `>` 重定向损坏二进制）。
