## Context

TrioBase 已经完成 Global Action Runtime 的第一轮建设：`common-action` 提供共享模型，`service-action` 提供 `/api/v1/actions`、候选验证、派发、事件、定义快照、执行记录和文档时间线，`service-lowcode`、`service-workflow-engine`、`service-openapi` 通过 owner adapter 被中央服务调用。这个模型统一了 GUI/LUI/Agent 的业务操作契约，但当前实现把状态变更热路径重新集中到了 `service-action`，并出现了 `service-openapi`、`service-workflow-engine` 通过 `service-action` 环回调用自身业务逻辑的问题。

现有约束仍然成立：Global Action 是跨 GUI、LUI、Agent 的业务操作信封；业务状态和 Temporal Worker 必须由 owner Spring Boot 微服务自理；AI 不得调用任意 API 或绕过确认、授权、幂等和审计；前端业务生命周期动作仍需统一的 Action Client 体验。

## Goals / Non-Goals

**Goals:**

- 将 `service-action` 从执行热路径中移除，把 action validation、authorization、guard、idempotency、audit、dispatch 和 result normalization 下沉到 owner 服务。
- 保留 `GlobalActionRequest`、`GlobalActionResult`、`ActionCandidate`、`ActionDefinition`、状态枚举和敏感字段脱敏契约。
- 提供 owner-hosted action runtime，使 lowcode、workflow-engine、openapi 可以以统一方式暴露候选验证和派发能力。
- 迁移 AI Agent、前端 Action Client 和服务内环回调用，删除对 `/api/v1/actions` 中央执行服务的依赖。
- 将 action timeline/audit 转为 owner emit + read-model/projection 形态，避免文档时间线查询绑定到被下线的执行服务。
- 删除 `service-action` 模块、gateway 路由、Docker 配置和 allowed-callers 中的 `service-action`，前提是调用方清零并通过回归验证。

**Non-Goals:**

- 不删除 Global Action 作为企业业务动作的统一契约。
- 不把 owner 服务的业务规则、Temporal Worker、流程状态机或低代码表单逻辑搬到 common 模块。
- 不允许 AI Agent 因去中心化而直接访问任意 owner API；AI 仍只能通过注册过的 ActionCandidate/ActionDefinition 执行。
- 不在本次重构中重建完整的审计管理前端，只保证 action audit/timeline 数据来源与查询契约不失效。
- 不把所有 draft 保存、查询和纯管理 CRUD 强制改造成 Global Action。

## Decisions

### 1. `common-action` 保持纯契约，Spring 能力放入 starter

`common-action` 继续承载 DTO、枚举、定义、owner contract、payload redaction、idempotency key 和状态机等无 Spring 或轻依赖工具。需要 AOP、Controller advice、审计写入、权限注解桥接、事务边界和 MyBatis/Repository 的能力放入新的 `common-action-spring-boot-starter` 或等价子模块。

备选方案是把 `@Auditable`、`@Idempotent` 和持久化 helper 全放入 `common-action`。拒绝该方案，因为它会让所有 Java 服务被迫依赖 Spring Web/AOP/DB 语义，形成新的 common 巨石。

### 2. Owner 服务自托管 Action Runtime

每个 owner 服务本地注册 `ActionDefinition`，并暴露标准 owner-hosted endpoint：

- `GET /api/v1/action-definitions` 或内部发现等价接口，返回本服务可执行 ActionDefinition。
- `POST /api/v1/action-candidates/validate`，对单个或批量候选做定义、schema、权限和 guard 校验。
- `POST /api/v1/actions/dispatch`，接收 `ActionCandidate` 或 `GlobalActionRequest` 并执行本服务拥有的 action。
- `GET /api/v1/actions/{actionId}` 与事件查询/流式能力只在该 owner 需要异步可观测时提供。

owner runtime 在同一个 Spring Boot 服务进程内执行，事务边界由 owner 服务控制。跨服务长事务仍遵守 Temporal 规则，workflow start/signal 由 `service-workflow-engine` 或对应 owner 的 Activity/Service 层完成。

备选方案是保留一个轻量 `service-action` 仅负责实时路由。拒绝作为最终态，因为它仍是热路径和可用性瓶颈；但允许迁移窗口内保留兼容外壳。

### 3. 候选动作路由由 target/definition 发现决定

前端和 AI Agent 不再硬编码只调用 `/api/v1/actions/candidates/*`。新的 Action Client 根据候选 target owner、action definition owner，或一个只读 discovery/catalog 解析 owner endpoint，然后调用 owner-hosted validate/dispatch。

候选仍必须在派发前通过注册定义、schema、权限、guard 和确认校验。AI Agent 的工具调用返回 ActionCandidate，前端确认后再派发，保留 LUI 到 GUI 的可追溯单向流。

### 4. 审计和时间线改为 owner emit + projection

执行记录不再由中央 `service-action` 统一写入 `act_action_execution`。owner runtime 负责写本服务 action audit 或发出标准 `ActionEventPayload`。平台审计或文档时间线通过读取 owner 事件表、平台审计表、Kafka/outbox 投影，或一个非执行热路径的查询服务聚合。

如果历史 `act_*` 数据有保留价值，迁移脚本只停止新写入，不立即删除表；查询 projection 可兼容读取历史表直到保留期结束。

### 5. 删除 `service-action` 采用双跑和清零门禁

实现按 dual-run 阶段推进：先让 owner endpoint 与原中央路径同时可用，然后迁移调用方，最后用仓库搜索和测试证明没有 `/api/v1/actions`、`service-action`、`ActionOwnerDispatchRequest` 环回调用残留，再删除模块和配置。

删除前必须验证 AI 请假/低代码联动、OpenAPI orchestration、workflow closure effect、本地表单 submit+workflow 启动、前端 Action Client 测试和 gateway 路由。

## Risks / Trade-offs

- [Action Client 路由复杂度上升] -> 使用 definition/owner registry 或约定 target owner 映射，前端和 AI 只依赖一个 Action Client API，不让页面散落 owner URL。
- [审计查询短期分散] -> 先定义标准 ActionEventPayload 与 owner audit 写入，再用 projection 聚合；历史 `act_*` 表保留只读兼容窗口。
- [AOP 幂等与业务幂等冲突] -> 平台幂等只保证 action request 去重，owner 仍保留业务唯一键和状态机前置校验。
- [迁移期间双路径行为不一致] -> owner runtime 和旧 `service-action` 使用同一 `common-action` 校验、状态机和 redaction 工具，测试覆盖同一候选请求在两条路径的结果。
- [一次性删除范围过大] -> 分阶段任务推进，每阶段完成后更新 OpenSpec task checkbox，只有调用方清零后才删除模块。

## Migration Plan

1. 增强 `common-action` 并新增 Spring starter/runtime 支撑 owner-hosted action。
2. 将 `LowcodeActionDefinitionProvider`、`WorkflowActionDefinitionProvider`、`OpenApiActionDefinitionProvider` 的定义迁入对应 owner 服务。
3. 在 lowcode、workflow-engine、openapi 增加 owner-hosted candidate validate/dispatch endpoint，并复用现有业务 Service，不再通过内部 `/internal/v1/actions/execute` 适配层。
4. 改造 `service-openapi` 和 `service-workflow-engine` 的 GlobalActionClient 环回调用为本地 Service 调用。
5. 改造前端和 AI Agent Action Client，使候选动作校验/派发走 owner-hosted 路由。
6. 改造 action audit/timeline 写入和查询来源，停止依赖 `service-action` 的执行表作为新数据源。
7. 删除 `service-action` gateway 路由、Docker service、Maven module、allowed-callers 和 `triobase.integrations.action` 配置。

Rollback strategy: 在删除 `service-action` 前，保留中央路径作为兼容 fallback；如果 owner-hosted dispatch 回归失败，Action Client 可以短期切回中央路径。删除模块后，回滚需要恢复上一版本服务和 gateway 配置，并保留数据库表结构兼容历史读取。

## Open Questions

- 文档时间线最终归属是平台审计聚合服务、data projection，还是 owner 查询聚合，需要根据现有产品入口决定；本次实现至少不能把新写入绑定到 `service-action`。
- 是否需要为 ActionDefinition discovery 保留只读 catalog 服务，或暂时采用前端/AI owner resolver 映射，取决于后续 action 数量和多租户动态配置需求。
