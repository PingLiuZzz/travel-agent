# 旅游出行智能体 - Web 前端

Vue3 + TypeScript + Vite + Ant Design Vue 4 + Pinia。

对接后端 `travel-agent`（Spring Boot）的 `/api/chat` 与 `/api/knowledge/ingest` 接口。

## 技术栈

| 类别 | 选型 |
|---|---|
| 框架 | Vue 3.5（`<script setup>`） |
| 语言 | TypeScript（strict） |
| UI | Ant Design Vue 4 |
| 构建 | Vite 5 |
| 状态 | Pinia（组合式 store） |
| 路由 | Vue Router 4 |
| HTTP | axios（async/await） |

> 版本号请以 npm 最新为准。

## 目录结构

```
src/
├── main.ts / App.vue / style.css      # 入口与全局样式
├── router/                            # 路由
├── types/                             # 类型定义（与后端 DTO 对齐）
├── api/                               # axios 封装 + chat/knowledge 接口
├── stores/chat.ts                     # 会话与消息状态
├── layouts/MainLayout.vue             # 主布局（Ant Layout 侧边栏）
├── components/                        # MessageBubble / ChatInput
└── views/                             # ChatView / KnowledgeView
```

## 快速启动

### 1. 安装依赖

```bash
cd travel-agent/web
npm install          # 或 pnpm install / yarn
```

### 2. 启动后端（另开终端）

```bash
cd travel-agent
mvn spring-boot:run   # 监听 8080
```

### 3. 启动前端

```bash
npm run dev           # 监听 5173，/api 自动代理到 8080
```

浏览器访问 http://localhost:5173

## 与后端的对接

- **跨域**：开发期由 Vite proxy 处理（`/api → http://localhost:8080`），无需后端配置 CORS。
- **会话隔离**：前端为每个会话生成 `userId`，作为后端 `@MemoryId` 的多用户隔离标识。
- **返回结构**：后端统一返回 `ApiResult<T>`（`{code,message,data}`），axios 拦截器自动脱壳。

## 质量门禁

```bash
npm run type-check    # vue-tsc 类型检查
npm run build         # 生产构建（含类型检查）
```

## 后续扩展点

- 接入 SSE 流式输出（需后端 `/api/chat` 改流式）以获得打字机效果。
- 工具调用结果（天气/航班/酒店）的富文本卡片渲染（`components/ToolCallCard` 占位）。
- 会话历史持久化（当前仅前端内存，刷新即失）。
