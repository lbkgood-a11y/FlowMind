## Why

TrioBase 的第一步必须让系统"立得住"——用户能登录（认证中心）、AI 能对话（LLM 网关）、知识能被检索（基础 RAG）。没有这三块，上层的工作流、Agent 编排、低代码都没有运行基础。Phase 1 是整个平台的"最小启动单元"。

## What Changes

- **新增 service-auth 认证中心**：用户注册、登录（JWT + RefreshToken）、角色-权限 RBAC 模型、Token 校验端点（供网关调用）
- **新增 ai-llm-gateway LLM 抽象网关**：基于 LiteLLM 的多模型路由、GPTCache 语义缓存、Prompt 追踪与成本监控、脱敏二次校验（铁律 2 第二道防线）
- **新增基础 RAG 能力**：文档向量化入库（pgvector）、语义检索 API、检索结果注入 Prompt 模板
- **扩展 common-core**：JWT 工具类、通用分页模型、安全上下文持有器
- **扩展 common-dto**：认证相关跨服务 DTO（登录请求/响应、用户信息载荷、Token 校验结果）
- **扩展 platform-gateway**：JWT 鉴权全局过滤器、认证白名单配置、AI 路径脱敏规则完善
- **前端对接**：登录页面（GUI）、AI 对话面板接入真实 LLM 网关端点（LUI）

## Capabilities

### New Capabilities

- `user-auth`: 用户注册、登录、Token 管理、RBAC 角色权限控制。包含密码加密存储、JWT 签发/验证/刷新、角色-权限关联 CRUD
- `llm-gateway`: LLM 模型抽象与治理。包含多供应商模型路由（LiteLLM）、语义缓存（GPTCache）、Prompt 日志追踪、Token 用量与成本统计、敏感数据二次校验
- `rag-knowledge-base`: 基础 RAG 知识检索。包含文档分块与向量化入库（pgvector）、语义相似度检索、检索结果拼装为 LLM Prompt 上下文

### Modified Capabilities

<!-- 当前无已有 spec，后续 Phase 修改时填写 -->

## Impact

| 影响范围 | 详情 |
|----------|------|
| `common-core` | 新增 JwtUtil、PageResult、SecurityContextHolder |
| `common-dto` | 新增认证 DTO（LoginRequest、LoginResponse、TokenValidateResult、UserInfoPayload） |
| `service-auth` | 完整实现：AuthApplication 启动类、认证控制器、用户/角色/权限 Domain + Repository、JWT Service、Spring Security 配置 |
| `ai-llm-gateway` | 完整实现：FastAPI 应用入口、模型路由服务（LiteLLM 适配）、语义缓存服务、Prompt 日志中间件、脱敏过滤器 |
| `platform-gateway` | 新增 JwtAuthFilter（全局鉴权）、AuthWhitelistConfig（白名单路径）、完善 DataMaskingFilter |
| `trio-base-frontend` | 新增登录页面、ChatPanel 对接真实 `/api/v1/ai/chat` SSE 端点 |
| `docker-compose` | 补充 ai-llm-gateway 服务定义 |
| 铁律合规 | 铁律 2（AI 脱敏双重校验）、铁律 6（Python↔Java JSON 契约）、铁律 7（Activity 幂等预留）、铁律 9（SSE 流式）|
