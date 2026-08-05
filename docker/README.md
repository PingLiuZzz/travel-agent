# Docker 部署（后端）

> 本期仅容器化后端；前端 `travel-web/` 的容器化留待后续。一期后端使用内存向量库，Milvus 可选。

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
docker compose -f docker/docker-compose.yml --env-file ./.env up -d app
```

> **必须带 `--env-file ./.env`**：compose v2 用 `-f docker/...` 指定子目录 compose 文件时，`.env` 默认从 `docker/` 子目录查找，找不到项目根的 `.env`。不加会导致**假健康**——密钥被当成空字符串、`/actuator/health` 仍返回 UP，但密钥未真正注入、对话 API 必失败。判断标志：启动时出现 `variable is not set. Defaulting to a blank string` 警告 = 未加载；无该警告 = 已正确注入。

首次启动会构建镜像（约 3-8 分钟）。`up -d` 后台运行。

### 3. 验证健康

等待约 40 秒（`start_period`），然后：

```powershell
docker compose -f docker/docker-compose.yml --env-file ./.env ps
```

期望 `app` 列 `STATUS` 为 `Up ... (healthy)`。

或直接探端点：

```powershell
curl http://localhost:8080/actuator/health
# 期望：{"status":"UP"}
```

### 4. 查看日志

```powershell
docker compose -f docker/docker-compose.yml --env-file ./.env logs -f app
```

`Ctrl+C` 退出跟随。

### 5. 停止与清理

```powershell
# 停止并删容器（保留数据卷）
docker compose -f docker/docker-compose.yml --env-file ./.env down

# 连同 Milvus 数据卷一起清理（谨慎：会删除已灌入的向量数据）
docker compose -f docker/docker-compose.yml --env-file ./.env down -v
```

## 可选：启用 Milvus（二期）

二期切 Milvus 向量库时：

1. 启动 Milvus 三件套：

   ```powershell
   docker compose -f docker/docker-compose.yml --env-file ./.env up -d milvus
   ```

2. 取消 `docker/docker-compose.yml` 中 `app` 服务 `depends_on` 的注释，使 app 等 milvus 健康后再启动。

3. 改后端 `EmbeddingStoreConfig.embeddingStore()` 一处 `@Bean`，由 `InMemoryEmbeddingStore` 切到 Milvus（详见上级 `CLAUDE.md`）。

## 备注

- 镜像以非 root 用户 `appuser` 运行；JVM 按 `MaxRAMPercentage=75.0` 自动配堆。
- HEALTHCHECK 走 `/actuator/health`，`docker compose ps` 的 `(healthy)` 即据此判定。
