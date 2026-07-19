## Context

TrioBase Phase 1 是平台的"最小启动单元"，需要在不违反十一条铁律的前提下，建立用户认证体系、LLM 治理层和基础知识检索能力。当前 common-core、common-dto、common-temporal 已有骨架代码（Result 响应体、异常枚举、TraceId 工具、BaseActivity 等），platform-gateway 已有 TraceId 和脱敏过滤器骨架，service-auth 和 ai-llm-gateway 仅有 POM/pyproject.toml 和空启动类。本阶段将它们从骨架推进到可运行。

## Goals / Non-Goals

**Goals:**
- service-auth 提供完整的注册/登录/Token 刷新/RBAC 能力，可作为网关鉴权的依据
- ai-llm-gateway 可代理至少 2 个 LLM 供应商（OpenAI + 至少一个国产模型），具备语义缓存减少重复调用
- 基础 RAG：文档入库 → 向量化 → 语义检索 → 拼装 Prompt 上下文，形成闭环
- 前端 ChatPanel 通过 SSE 对接真实 LLM 网关端点（铁律 9）
- 所有变更通过 ArchUnit + Checkstyle 门禁

**Non-Goals:**
- 不做 OAuth2/SAML 等外部 IDP 集成（Phase 2）
- 不做复杂 RAG 策略（HyDE、Re-ranking、多路召回）—— 本阶段仅实现朴素语义检索
- 不做 LLM 供应商的异步流式聚合（本阶段单路透传）
- 不涉及 Temporal Workflow（认证和 RAG 都是同步操作，无需编排）

## Decisions

### D1: 认证方案 — Spring Security + Stateless JWT

**选型**：Spring Security 6 + Nimbus Jose JWT（spring-security-oauth2-jose 内置），无状态会话，BCrypt 密码编码。

**理由**：
- Spring Security 是 Spring 生态标准，与 Spring Cloud Gateway 的鉴权过滤器天然兼容
- Stateless JWT 避免了分布式 Session 问题，网关只需验证签名即可放行
- BCrypt 是 OWASP 推荐的口令哈希算法

**备选考虑**：Nacos Session 共享方案（有状态，与无状态网关设计冲突，放弃）。

### D2: RBAC 模型 — 标准五表设计

**表结构**：`sys_user` → `sys_user_role` ← `sys_role` → `sys_role_permission` ← `sys_permission`

用户-角色 N:M，角色-权限 N:M。权限粒度 = 资源路径 + 操作（如 `GET:/api/v1/users`）。

**理由**：标准 RBAC 五表足以覆盖 Phase 1 需求，后续扩展 ABAC 时在 Permission 表加 condition 字段即可，不需大改。

### D3: LLM 网关架构 — FastAPI 中间件链

**架构**：
```
请求 → DataMaskingMiddleware（脱敏二次校验）
     → LiteLLM Router（模型路由）
     → GPTCache Lookup（语义缓存命中 → 直接返回）
     → Provider Call（未命中 → 调用 LLM）
     → GPTCache Store（存入缓存）
     → PromptLoggingMiddleware（记录日志）
     → SSE Streaming Response
```

**理由**：
- 中间件链模式与 FastAPI 的 `@app.middleware` 机制天然契合
- LiteLLM 已提供统一的 `acompletion` 接口，无需自行封装供应商差异
- GPTCache 在请求级做语义相似度匹配（`semantic_similarity` evaluator），对调用方透明

**备选**：自研供应商适配层（工作量大，且 LiteLLM 已覆盖 100+ 供应商，放弃）。

### D4: 语义缓存策略 — GPTCache + Redis

GPTCache 配置 Redis 作为 eviction manager，相似度阈值 0.85，TTL 1 小时。

**理由**：企业场景下大量重复性咨询（"如何创建用户？"），语义缓存可降低 30-50% LLM 调用成本。Redis 存储避免单机内存溢出。

### D5: RAG 方案 — pgvector + sentence-transformers

**选型**：PostgreSQL pgvector 扩展做向量存储，sentence-transformers（all-MiniLM-L6-v2）做 embedding 模型。

**理由**：
- pgvector 已在 docker-compose 中部署，无需引入新的向量数据库
- all-MiniLM-L6-v2（384 维）轻量且中文可接受，可在本地运行，避免调用外部 embedding API
- 分块策略：固定 500 字符 + 50 字符重叠（朴素但有效）

**备选**：Milvus/Qdrant（功能更强但增加运维复杂度，Phase 2 再评估）。

### D6: 前端对接方式 — 直接 SSE 代理

ChatPanel 已有 `useChat({ api: "/api/v1/ai/chat" })`，Next.js rewrites 将 `/api/v1/ai/*` 代理到 LLM 网关。

```
Browser → Next.js (rewrites) → Spring Cloud Gateway → ai-llm-gateway (FastAPI)
```

**理由**：利用 Next.js 内置 rewrites 避免 CORS 问题，且网关已有的 TraceId + 脱敏过滤器无需在前端重复实现。

### D7: 认证服务 Task Queue（铁律 5 预留）

service-auth 内嵌 Temporal Worker，Task Queue = `service-auth-queue`（与 `spring.application.name` 绑定）。

Phase 1 虽无 Temporal Workflow，但 Worker 随应用启动已就绪，后续添加异步流程（如注册后发送验证邮件）时无需改部署配置。

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| pgvector 在大数据量下性能下降 | Phase 1 数据量可控（<10 万文档）；Phase 2 评估 Milvus |
| all-MiniLM-L6-v2 中文效果一般 | 可接受的基础质量；Phase 2 换用 BGE-M3 或调用外部 embedding API |
| GPTCache 语义相似度误判（不同问题命中相同缓存） | 阈值设 0.85 偏保守，后续根据线上数据调参 |
| JWT 无状态无法主动失效 | RefreshToken 短 TTL（30min）+ AccessToken 超短 TTL（5min）；Phase 2 加 Redis 黑名单 |
| Phase 1 无 Temporal 流程，Worker 空转 | Worker 仅占线程池资源，启动即就绪，成本可忽略 |

## Open Questions (Resolved)

1. ~~国产 LLM 供应商优先接入哪家？~~ → **DeepSeek**（`deepseek-chat` 模型，API 兼容 OpenAI 格式，成本低且中文能力强）
2. ~~RAG 首期文档来源？~~ → **本地 Markdown 文件**（扫描 `docs/` 目录下 `*.md` 文件入库，支持文件变更后重新入库）
3. ~~登录页面风格？~~ → **Shadcn UI 模板**（`shadcn/ui` Card + Input + Button 组件，与现有项目风格一致）
