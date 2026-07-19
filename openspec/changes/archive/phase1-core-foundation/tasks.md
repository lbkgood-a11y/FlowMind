## 1. common-core 基础工具扩展

- [ ] 1.1 新增 `JwtUtil` 工具类（JWT 签发/验证/刷新，基于 Nimbus Jose，通过 `spring-security-oauth2-jose` 提供）— 关联铁律 8（TraceId 透传不涉及，纯工具类）
- [ ] 1.2 新增 `PageResult<T>` 通用分页响应模型（records/total/page/size）
- [ ] 1.3 新增 `SecurityContextHolder` 请求上下文持有器（ThreadLocal 存储当前 userId/username/permissions，Filter 注入、业务代码读取）
- [ ] 1.4 新增 `AuthErrorCode` 枚举（实现 ErrorCode 接口：USER_ALREADY_EXISTS、BAD_CREDENTIALS、ACCOUNT_DISABLED、TOKEN_EXPIRED、PASSWORD_TOO_WEAK）

## 2. common-dto 认证 DTO 扩展

- [ ] 2.1 新增 `LoginRequest`（username, password）
- [ ] 2.2 新增 `LoginResponse`（accessToken, refreshToken, expiresIn, userId, username, roles: List<String>）
- [ ] 2.3 新增 `TokenValidateResult`（valid: boolean, userId, username, permissions: List<String>）
- [ ] 2.4 新增 `UserInfoPayload`（id, username, email, status, roles, createdAt）— 供网关/其他服务通过 Feign 查询用户信息

## 3. service-auth 认证中心实现

- [ ] 3.1 数据库 Schema：创建 `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission` 五张表的 Flyway 迁移脚本（`V1__auth_schema.sql`）
- [ ] 3.2 Entity 层：`SysUser`、`SysRole`、`SysPermission` + 关联表 Entity（MyBatis-Plus 映射）
- [ ] 3.3 Repository 层：`UserRepository`（继承 `BaseMapper<SysUser>`）、`RoleRepository`、`PermissionRepository`
- [ ] 3.4 Domain Service：`AuthService`（注册逻辑：用户名唯一校验 + BCrypt 密码编码 + 默认角色分配；登录逻辑：凭证校验 + JWT 签发 + RefreshToken 生成）
- [ ] 3.5 Domain Service：`TokenService`（JWT AccessToken 签发 5min TTL / RefreshToken 签发 30min TTL / Token 刷新 / Token 验证）— 关联铁律 8（Token 中携带 traceId 预留字段）
- [ ] 3.6 Domain Service：`UserService`（用户 CRUD、角色分配、状态启用/禁用）
- [ ] 3.7 Domain Service：`RoleService` + `PermissionService`（角色 CRUD + 权限分配、权限列表查询）
- [ ] 3.8 Controller：`AuthController`（`POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`、`GET /api/v1/auth/validate`）
- [ ] 3.9 Controller：`UserController`（`GET /api/v1/users/{id}`、`PUT /api/v1/users/{id}`、`POST /api/v1/users/{id}/roles`）
- [ ] 3.10 Controller：`RoleController`（`GET /api/v1/roles`、`POST /api/v1/roles`、`PUT /api/v1/roles/{id}`、`DELETE /api/v1/roles/{id}`）
- [ ] 3.11 Spring Security 配置：`SecurityConfig`（禁用 Session、放行 `/api/v1/auth/**`、其余路径要求 JWT 认证、BCryptPasswordEncoder Bean）
- [ ] 3.12 Temporal Worker 配置：`AuthWorkerConfig`（监听 Task Queue `service-auth-queue`，Worker 随应用启动）— 关联铁律 5（Worker 与微服务生命周期共生死）
- [ ] 3.13 更新 `application.yml`（数据库连接、JWT 签名密钥、Temporal 连接、Nacos 注册）
- [ ] 3.14 单元测试：`AuthServiceTest`（注册成功/重复用户/弱密码）、`TokenServiceTest`（签发/验证/过期/刷新）
- [ ] 3.15 ArchUnit 门禁：确认 auth 模块无违禁调用（通过 `common-archunit` 内置规则自动检查）

## 4. platform-gateway 网关增强

- [ ] 4.1 新增 `JwtAuthFilter` 全局过滤器（从请求头提取 Bearer Token → 调用 service-auth `/api/v1/auth/validate` → 注入 `X-User-Id`、`X-User-Permissions` 头到下游）— 关联铁律 8（TraceId 透传）
- [ ] 4.2 新增 `AuthWhitelistConfig` 配置类（白名单路径：`/api/v1/auth/login`、`/api/v1/auth/register`、`/api/v1/auth/refresh`、`/health`、`/actuator/**`）
- [ ] 4.3 完善 `DataMaskingFilter`：补充手机号、身份证、银行卡号的正则扫描规则；新增财务关键词（"密钥"、"secret"、"token"）扫描
- [ ] 4.4 新增 `/api/v1/ai/**` 路由规则（转发到 ai-llm-gateway，保留 SSE 长连接超时 120s）
- [ ] 4.5 更新 `application.yml`（JWT 公钥/验证端点、白名单路径、AI 路由超时配置）

## 5. ai-llm-gateway LLM 抽象网关实现

- [ ] 5.1 FastAPI 应用入口：`app/main.py`（应用工厂模式，注册中间件链、路由、生命周期事件）— 关联铁律 6（对外 JSON 契约）
- [ ] 5.2 中间件：`DataMaskingMiddleware` — 脱敏二次校验（手机号/身份证/金融关键词正则扫描 + 替换为 `[REDACTED_*]`）— 关联铁律 2（第二道防线）
- [ ] 5.3 服务：`LiteLLMService` — 封装 LiteLLM `acompletion`，支持 `openai/gpt-4o` 和 `deepseek/deepseek-chat`（OpenAI 兼容 API），异步流式返回
- [ ] 5.4 服务：`CacheService` — GPTCache 集成（`semantic_similarity` evaluator，相似度阈值 0.85，Redis eviction manager，TTL 3600s）
- [ ] 5.5 中间件：`PromptLoggingMiddleware` — 请求/响应日志记录（trace_id、model、prompt 前 500 字符、response 前 500 字符、token 用量、成本估算、延迟 ms）— 关联铁律 8（TraceId 提取）
- [ ] 5.6 模型：`ChatRequest` Pydantic Schema（model, messages, temperature, max_tokens, stream）
- [ ] 5.7 模型：`ChatResponse` Pydantic Schema（id, model, content, usage, cached: bool）
- [ ] 5.8 路由：`POST /api/v1/ai/chat` — 核心对话端点（中间件链 → 缓存查询 → LLM 调用 → SSE StreamingResponse）— 关联铁律 9（SSE 流式）
- [ ] 5.9 路由：`GET /api/v1/ai/models` — 模型列表
- [ ] 5.10 路由：`GET /health` — 健康检查（含 provider 连通性检查）
- [ ] 5.11 配置：`config.py`（LLM API keys、model mapping、cache TTL、masking patterns，全部从环境变量读取）
- [ ] 5.12 契约：OpenAPI schema 自动生成（FastAPI 内置 `/docs`），确保所有端点有 Request/Response Schema 文档 — 关联铁律 6
- [ ] 5.13 更新 `pyproject.toml`（补齐 litellm、gptcache、redis、sentence-transformers、python-jose 依赖）
- [ ] 5.14 测试：`test_chat.py`（缓存命中/未命中、流式响应、模型不存在 400、脱敏校验）

## 6. RAG 基础知识检索（嵌入 ai-llm-gateway 或独立部署）

- [ ] 6.1 数据库 Schema：创建 `rag_documents` 和 `rag_document_chunks` 表的迁移脚本（pgvector embedding 列，384 维）
- [ ] 6.2 模型加载：`EmbeddingService` — 启动时加载 `all-MiniLM-L6-v2`（sentence-transformers），提供 `embed(text: str) -> List[float]` 和 `embed_batch(texts: List[str]) -> List[List[float]]`
- [ ] 6.3 服务：`IngestionService` — 文档分块（500 字符 + 50 重叠）+ 批量向量化 + pgvector 入库（原子事务，失败回滚）
- [ ] 6.4 服务：`SearchService` — 查询向量化 + pgvector `<=>` cosine similarity 检索 + top-K 过滤 + 相似度阈值裁剪
- [ ] 6.5 服务：`AssemblyService` — 检索结果拼装为 LLM Prompt 上下文模板
- [ ] 6.6 模型：`DocumentIngestRequest`（title, content）、`SearchRequest`（query, top_k）、`AssembleRequest`（query, top_k）
- [ ] 6.7 路由：`POST /api/v1/rag/documents`、`GET /api/v1/rag/documents`、`DELETE /api/v1/rag/documents/{id}`、`POST /api/v1/rag/scan`（扫描 `docs/` 下 `*.md` 文件入库）
- [ ] 6.8 路由：`GET /api/v1/rag/search?q=...&top_k=5`
- [ ] 6.9 路由：`POST /api/v1/rag/assemble` — 搜索 + Prompt 拼装一体化
- [ ] 6.10 测试：`test_rag.py`（文档入库/分块/搜索/空结果/删除不存在文档）

## 7. 前端对接

- [ ] 7.1 新增 `src/app/login/page.tsx` — 登录页面（Shadcn UI Card + Input + Button 组件，调用 `/api/v1/auth/login`，成功后存储 Token 到 localStorage + Zustand Store）
- [ ] 7.2 `next.config.ts` 添加 rewrites：`/api/v1/*` → Spring Cloud Gateway 地址（开发环境）
- [ ] 7.3 `ChatPanel.tsx`：更新 `useChat` 配置（添加 Authorization header 从 Zustand Store 获取 Token，处理 401 跳转登录）
- [ ] 7.4 `page.tsx`：未登录时重定向到 `/login`；登录后根据 `mode` 切换 GUI 主页 / LUI 对话
- [ ] 7.5 新增 `src/lib/api.ts` — HTTP 客户端封装（自动附加 Authorization header、401 拦截 → 跳转登录、请求/响应日志）

## 8. Docker & CI 门禁

- [ ] 8.1 更新 `docker/docker-compose.yml`：添加 `ai-llm-gateway` 服务定义（环境变量：LLM_API_KEYS、REDIS_URL、DB_URL）
- [ ] 8.2 创建 `trio-base-services/service-auth/Dockerfile`（多阶段构建：maven build + jre run）
- [ ] 8.3 创建 `trio-base-ai/ai-llm-gateway/Dockerfile`（python:3.12-slim + poetry install）
- [ ] 8.4 验证 CI 门禁：`mvn clean verify` 全量通过（Checkstyle + Unit Test + Jacoco + ArchUnit）
