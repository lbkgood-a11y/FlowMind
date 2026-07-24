## Context

TrioBase 的 AI 层已经包含 `ai-llm-gateway`、`ai-rag-service`、`ai-intent-router` 和 `ai-agent-orchestrator`。其中 LLM 网关与 RAG 已有基础实现，后两个模块仍接近脚手架状态；当前 `ai-agent-orchestrator` 依赖 LangGraph 0.3、LangChain 0.3 和 Temporal Python SDK，但尚无统一入口、状态模型、工具边界、持久化、流式事件协议和生产验收门禁。

平台侧已经具备 Global Action、RBAC、审计和 Spring Boot 内嵌 Temporal Worker。Agent 框架必须复用这些确定性能力，不能成为新的业务写入口，也不能在 Python 侧建立与 Java Temporal 重叠的业务流程运行时。

本设计面向 AI 开发人员、业务服务开发人员、前端开发人员、安全与运维人员。它遵守现有十一条铁律以及 ADR-001、ADR-002，并以默认租户 `default` 运行，同时保留未来的租户命名空间。

## Goals / Non-Goals

**Goals:**

- 建立一个可复用、可恢复、可观测、可评测的 Agent 编排运行时。
- 为“请假申请”和“费用报销”提供从自然语言到 ActionCandidate、确认、Global Action 和结果回传的完整路径。
- 明确 LangGraph、LLM Gateway、RAG、Global Action 和 Java Temporal 的职责边界。
- 统一图、节点、状态、工具、SSE、错误、版本、审计和安全规范。
- 保持模型供应商无关，并允许在不改业务契约的前提下替换模型。

**Non-Goals:**

- 不在本变更中建设开放式自治 Multi-Agent 团队或允许 Agent 无人值守执行高风险动作。
- 不允许 Agent 直接访问数据库、任意 URL、Shell、动态代码或业务写接口。
- 不使用 Python Temporal Worker 承担业务流程，不用 LangGraph 替代 Java Temporal。
- 不在本变更中正式开放多租户，只做 `tenantId=default` 和命名空间预留。
- 不要求所有 AI 调用都进入 LangGraph；简单分类、单次生成和纯数据转换可以保持普通函数或服务。

## Decisions

### 1. LangGraph 作为 Agent 编排标准，按复杂度使用

`ai-agent-orchestrator` 使用 LangGraph 1.x 稳定版本作为有状态 Agent 图运行时，并通过 Poetry lock 固定可复现版本。只有满足以下任一条件的流程才建立图：多步骤状态迁移、工具调用、分支/循环、人工介入、失败恢复或长对话记忆。

单次分类、规则路由、schema 转换和单次模型调用保持普通 Python 代码。这样既利用 LangGraph 的 checkpoint、interrupt 和 streaming，也避免把简单逻辑图形化后增加维护成本。

备选方案：

- OpenAI Agents SDK：抽象更轻、内置 handoff/guardrail/tracing，但 TrioBase 要求 LiteLLM 多供应商和显式复杂状态图，暂不作为主编排框架。
- Microsoft Agent Framework：适合微软/.NET 生态，也提供图工作流；当前项目的 AI 层与团队基线是 Python/LangGraph，切换收益不足。
- Google ADK：适合 Gemini/Vertex AI 体系，TrioBase 当前不绑定该生态。
- AutoGen：不再作为新模块默认选型；微软已经将 Agent Framework 定位为 AutoGen 与 Semantic Kernel 的直接继任方向。
- 原生 Python 状态机：依赖更少，但需要自行实现持久化、interrupt、恢复和图可视化，长期成本更高。

### 2. 单一 Agent 入口与分层图结构

对外由 FastAPI 暴露版本化 Agent Run API；入口只接受经过 API Gateway 鉴权、租户解析、TraceId 注入和第一层脱敏后的请求。建议资源模型：

- `POST /api/v1/agent/runs`：创建运行并返回 SSE 流或运行引用。
- `GET /api/v1/agent/runs/{runId}/events`：订阅/恢复事件流。
- `POST /api/v1/agent/runs/{runId}/resume`：提交缺失参数或人工确认结果并恢复 interrupt。
- `POST /api/v1/agent/runs/{runId}/cancel`：取消尚未终止的 Agent 运行。
- `GET /api/v1/agent/runs/{runId}`：查询受权限保护的运行摘要。

第一阶段使用一个顶层 Router Graph，路由至：

- `knowledge-answer`：RAG 检索、证据组装和带引用回答。
- `business-assistant`：意图识别、槽位补全、候选动作生成和人工确认。
- `unsupported`：安全拒绝或降级为普通帮助信息。

请假与报销使用共享的 Business Assistant Graph，加领域 schema、ActionDefinition 和 Prompt 配置，不复制两套运行时。

### 3. 统一 AgentState 与版本语义

AgentState 使用 Pydantic 定义并禁止任意字典穿透。核心字段包括：

- 身份与关联：`schemaVersion`、`graphId`、`graphVersion`、`threadId`、`runId`、`tenantId`、`actor`、`traceId`、`correlationId`、`locale`。
- 对话与意图：受限消息引用、`intent`、`confidence`、`slots`、`missingSlots`。
- 知识与动作：`evidence`、`actionCandidates`、`pendingInterrupt`、`actionRefs`。
- 运行与治理：`status`、`stepCount`、`usage`、`warnings`、结构化 `error`。

`tenantId` 和 actor 只能来自受信网关/认证上下文，模型输出不得覆盖。状态不得存储密钥、授权头或未脱敏的敏感字段；大文本、附件和敏感业务正文优先存引用而非复制内容。

每次发布图、State schema、Prompt、tool schema 都必须带版本。已有 thread 默认继续使用创建时固定的 graph version；不兼容迁移必须显式编写 migration 或终止旧 thread，禁止静默加载新图恢复旧 checkpoint。

### 4. PostgreSQL checkpoint 是生产持久化基线

生产环境使用 LangGraph PostgreSQL checkpointer/store；内存 checkpointer 仅允许单元测试和本地临时运行。checkpoint 以 `(tenantId, threadId, graphVersion)` 形成逻辑命名空间，当前租户固定为 `default`。

运行状态与 SSE 事件摘要设置可配置保留期；敏感字段在进入 checkpoint 前完成最小化、脱敏或引用化。checkpoint 负责 AI 推理过程的暂停恢复，不作为业务事实来源；业务结果以 Global Action 和 owner service 数据为准。

### 5. 节点分为确定性节点与模型节点

每个节点必须声明输入/输出 schema、超时、可重试性、数据敏感级别和观测名称。节点只能返回状态增量，不得通过共享可变全局变量传递数据。

- 确定性节点：schema 校验、路由、权限结果解释、候选动作构造、引用格式化。
- 模型节点：意图补全、自然语言生成、受控结构化输出。
- I/O 节点：调用 RAG、LLM Gateway 或只读业务工具。
- interrupt 节点：等待缺参、用户确认或受控人工复核。

只有幂等的只读 I/O 节点可以自动重试。模型调用重试必须保留同一 run/step 关联并受最大次数与成本预算控制。业务动作 dispatch 不在 LangGraph 节点内自动重试。

### 6. 工具白名单与业务副作用隔离

工具注册表只允许三类工具：

1. `READ`：带用户权限和数据范围的只读查询。
2. `RETRIEVAL`：知识检索与引用获取。
3. `ACTION_CANDIDATE`：根据已注册 ActionDefinition 生成候选动作。

工具参数和返回值都使用 Pydantic/JSON Schema 校验。禁止任意 HTTP、SQL、Shell、动态 Python、自由 Prompt 工具以及直连 owner mutation API。

Agent 默认只生成 ActionCandidate。候选动作先调用 `service-action` 的 availability/validation 契约完成注册、schema、权限与 guard 预检，再由前端 Component Registry 展示。在第一阶段，所有会改变业务状态的 LUI/Agent 动作均要求用户明确确认；确认后由 Action Client/Bridge 以 `LUI` 或 `AGENT` 来源提交 Global Action。Action Runtime 负责幂等、授权、审计和 owner dispatch。

### 7. LangGraph 与 Java Temporal 分工

LangGraph 管理对话推理、工具选择、证据、槽位和人工确认；Java Temporal 管理跨服务业务状态、超时、补偿和最终一致性。

Agent 发起长业务流程时，只生成对应 ActionCandidate，例如 `process.instance.start`。Global Action 成功受理后，由 `service-workflow-engine` 启动或 signal Java Temporal Workflow，并将 action/workflow reference 返回 Agent 事件流。

生产环境不启用 Temporal LangGraph Python plugin，也不在 `ai-agent-orchestrator` 注册 Python Temporal Worker。若 Python 仅作为 Temporal Client 有独立需求，必须另建 ADR、使用标准 JSON/Protobuf 契约，并继续满足 TraceId 和鉴权要求。

### 8. 统一 SSE 事件协议

所有 Agent 文本和运行中间态使用 SSE。事件 envelope 至少包含：`eventId`、`eventType`、`runId`、`threadId`、`sequence`、`timestamp`、`traceId`、`dataSchemaVersion` 和 `data`。

标准事件类型：

- `run.created`
- `message.delta`
- `evidence.ready`
- `slot.missing`
- `action.candidate`
- `confirmation.required`
- `action.status`
- `run.completed`
- `run.failed`
- `heartbeat`

同一运行的 `sequence` 单调递增。客户端使用 `Last-Event-ID` 或显式 cursor 恢复；服务端不得把断线重连解释为重新执行业务动作。未知事件必须可被客户端安全忽略，破坏性变更通过 `dataSchemaVersion` 升级。

### 9. 安全、权限和 Prompt Injection 防线

所有模型请求必须经过 API Gateway 与 LLM Gateway 双重脱敏；Agent 不得直接调用任何模型供应商。日志、checkpoint、trace 和评测样本禁止保存密钥、授权头和原始高敏 Prompt。

检索文档、用户消息和工具返回都视为不可信数据，不能修改 system policy、tool allowlist、actor、tenant、权限或确认规则。结构化模型输出必须 schema 校验；未知字段默认拒绝。读取业务数据的工具必须在每次调用时以受信 actor 上下文执行权限和数据范围检查，不能复用模型声称的权限。

Generative UI 只允许输出注册组件标识和 schema-valid props，不允许脚本、HTML handler、动态标签或 DOM 修改。

### 10. 可观测、评测和生产门禁

每个 run、节点、LLM 调用、RAG 查询和 ActionCandidate 都关联同一 TraceId/correlationId。观测至少覆盖运行成功率、节点延迟、首 token 延迟、总耗时、token/成本、cache hit、重试、interrupt、取消、错误分类、ActionCandidate 校验率和最终 Action 结果。

不得在中间件先记录原始 Prompt 再脱敏；日志只保存脱敏摘要、hash 或受控引用。错误响应只暴露稳定错误码和有限诊断，不返回堆栈、Prompt 或供应商密钥信息。

上线前必须通过：

- 图编译、分支、interrupt、checkpoint 恢复和版本兼容测试。
- 工具白名单、schema、权限、租户上下文和 Prompt Injection 测试。
- SSE 顺序、断线恢复、取消和背压测试。
- 请假与报销黄金数据集评测，包括意图、槽位、引用、ActionCandidate 和拒绝场景。
- 安全硬门禁：未注册副作用、未确认状态变更、越权读取和跨租户访问必须为零容忍。

业务质量阈值由版本化评测配置维护；未达到已批准阈值不得自动提升 Prompt、模型或图版本至生产。

## Risks / Trade-offs

- [LangGraph 与 Temporal 都有持久化概念，团队可能混淆职责] → checkpoint 只保存 AI 推理状态，业务事实和长事务唯一归 Global Action/owner service/Java Temporal。
- [图和状态版本升级可能导致旧 thread 无法恢复] → thread 固定 graph version，提供显式 migration，保留受支持旧版本直至会话过期。
- [checkpoint 与日志可能沉淀敏感数据] → 状态最小化、字段分级、写前脱敏/引用化、加密和保留期清理。
- [模型可能伪造工具参数或受检索内容注入] → 工具白名单、Pydantic schema、服务端身份上下文、权限重检和确认门禁。
- [SSE 断线可能引起重复运行或重复动作] → 事件 cursor、run 幂等、ActionCandidate 与 Global Action 独立幂等键，重连只重放事件。
- [低层 LangGraph 增加开发复杂度] → 提供图模板、节点基类、状态模型、测试夹具和开发脚手架；简单场景不强制建图。
- [框架版本迭代较快] → 固定 LangGraph 1.x 稳定版本与 lockfile，升级必须通过兼容性评测和变更记录。

## Migration Plan

1. 新增 ADR 和本规范，修正 `ai-agent-orchestrator` 中“Temporal Python SDK 作为业务编排”的模糊描述。
2. 建立 FastAPI 入口、Pydantic AgentState、图注册表、工具注册表、错误模型和 SSE envelope。
3. 接入 PostgreSQL checkpoint/store、版本命名空间、保留期和敏感字段治理。
4. 接入 LLM Gateway、RAG 与 `service-action` candidate availability；禁止直连模型和业务 mutation。
5. 实现共享 Business Assistant Graph，并配置请假与费用报销领域 schema。
6. 前端接入 SSE、Component Registry、缺参补全、候选动作预览、确认和 Action 状态回传。
7. 建立黄金数据集、离线评测、集成测试、负载测试、观测面板和 CI 门禁后灰度启用。

所有 Agent 入口使用功能开关。回滚时停止创建新 run，保留旧版本只读查询与事件恢复；已经提交的 Global Action 和 Java Temporal Workflow 不随 Agent 回滚而取消，由各自运行时继续完成。

## Open Questions

- Agent checkpoint 和事件摘要的默认保留天数需要由安全与合规负责人确认；实现必须支持配置和按 thread 删除。
- `ai-intent-router` 长期保持独立微服务还是收敛为 orchestrator 内部轻量组件，需要在首批链路压测后根据延迟与独立扩缩容需求决定。
- 下一阶段是否允许低风险、预授权 Agent 自动 dispatch Global Action，需要单独的委托授权模型和 ADR；第一阶段一律要求人工确认。
