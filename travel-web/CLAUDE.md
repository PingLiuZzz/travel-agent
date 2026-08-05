# CLAUDE.md（前端）

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> 本文件覆盖前端（`travel-web/`）。项目整体与后端指南见上级 [`../CLAUDE.md`](../CLAUDE.md)。

## 技术栈

Vue3（`<script setup>`）+ TypeScript（strict，禁 any）+ Vite 5 + Ant Design Vue 4 + Pinia + Vue Router 4 + axios。

## 常用命令（在 `travel-web/` 下）

```bash
npm install
npm run dev          # dev server，监听 5173
npm run type-check   # vue-tsc --noEmit，类型检查（strict）
npm run build        # 生产构建（含类型检查）
```

## 架构

- `stores/chat.ts`：Pinia **组合式 store**，按 `userId` 存消息（多会话隔离），**不可变更新**（展开运算符生成新数组/对象，不直接 mutate）。
- `api/request.ts`：axios 实例。**拦截器只做网络错误兜底**、返回完整 response（保持返回类型与 `AxiosResponse` 合法）；`ApiResult` 脱壳逻辑放在泛型封装 `request<T>()` 里——在拦截器里脱壳会与 `AxiosResponse` 返回类型冲突，导致 TS 报错。
- `api/chat.ts`、`api/knowledge.ts`：调用 `request<T>()`，类型对齐后端 DTO。
- `types/chat.ts`：与后端 DTO 对齐的类型（`ChatRequest`/`ChatResponse`/`ApiResult`/`Session`）。
- `router/index.ts`：`/chat`（默认）、`/knowledge`。
- `layouts/MainLayout.vue`：Ant Layout 侧边栏导航。
- `views/ChatView.vue`（会话列表 + 对话区）、`views/KnowledgeView.vue`（文档灌入）。
- `components/MessageBubble.vue`、`ChatInput.vue`。

## 与后端对接

- **跨域**：`vite.config.ts` 配 proxy `/api` → `http://localhost:8080`，开发期无需 CORS。先起后端（8080）再起前端（5173）。
- **会话隔离**：前端为每个会话生成 `userId`（`u-{timestamp}`），作为后端 `@MemoryId` 的多用户隔离标识。
- **返回结构**：后端统一 `ApiResult<T>`（`{code,message,data}`）；`code!==0` 由 `request<T>()` 弹错并抛异常，调用方只拿 `data`。
- **接口**：`POST /api/chat {userId,message} → {reply}`；`POST /api/knowledge/ingest {filePath} → 提示文案`。

## 约定（项目特定）

- 异步统一 `async/await`，禁止 `.then()` 链（遵循全局 CLAUDE.md）。
- 类型严格对齐后端 DTO（`types/chat.ts`），后端改 DTO 要同步改这里。
- Ant Design Vue 在 `main.ts` 全量注册（`app.use(Antd)`），模板用 `a-` 前缀组件；图标来自 `@ant-design/icons-vue`，按需 import。
- 后端 `/api/chat` 为整包返回（非流式），前端用 `loading` 占位等待；如需打字机效果，后端需改 SSE。
- 会话与消息仅存内存（Pinia），刷新即失——持久化是后续扩展点。
