# ADR-003：AI Agent 编排框架与业务执行边界

**日期：** 2026-07-24  
**状态：** Accepted  
**决策者：** TrioBase 架构组

## 背景

TrioBase 计划从基础 RAG 逐步发展到能够理解业务意图、补全表单、生成操作建议并推动业务流程的 Agent 平台。项目已经存在 `ai-agent-orchestrator`、LLM Gateway、RAG、Global Action 和 Java Temporal，但 Agent 框架尚未形成统一要求。

如果不提前划清边界，容易出现以下问题：

- 多个 Agent 框架并存，状态、工具和观测契约不统一。
- Python Agent 直接调用业务写接口，绕过权限、确认、幂等和审计。
- LangGraph 的 checkpoint 与 Temporal 的业务持久化职责重叠。
- 模型或检索内容通过 Prompt Injection 扩大工具权限。
- SSE、TraceId、敏感数据、评测和版本升级缺少生产标准。

## 决策

### 1. 主框架

TrioBase 采用 **LangGraph 1.x 稳定版本**作为 `ai-agent-orchestrator` 的标准有状态 Agent 编排运行时。

LangGraph 适用于包含多步骤、分支、循环、工具调用、持久状态、人工介入或失败恢复的 Agent 流程。简单意图分类、单次模型调用、schema 转换和纯确定性逻辑不强制使用 LangGraph。

采用 LangGraph 的主要原因：

- 支持显式状态图，可将确定性节点与 LLM 节点组合。
- 支持 checkpoint、interrupt、resume、streaming 和人工介入。
- 模型供应商无关，能够与 TrioBase 的 LiteLLM 多模型网关配合。
- 适合请假、报销等“理解—补全—预览—确认—执行”的企业 Agent 场景。

### 2. 框架职责边界

| 组件 | 负责 | 不负责 |
|---|---|---|
| LangGraph | 意图、推理、槽位、工具选择、证据、对话状态、人工介入 | 业务事实、跨服务事务、直接业务写入 |
| LLM Gateway | 模型路由、二次脱敏、配额、缓存、成本与 Prompt 治理 | Agent 业务编排 |
| RAG | 受权限约束的检索、证据和引用 | 决定业务动作权限 |
| Global Action | ActionDefinition、schema、权限、确认、幂等、审计、owner dispatch | 自然语言推理 |
| Java Temporal | 跨服务长流程、超时、重试、补偿、最终一致性 | 对话和模型推理状态 |

Agent 只能生成并校验 `ActionCandidate`。所有业务状态变更必须经过 Global Action；第一阶段所有由 LUI/Agent 发起的状态变更均要求用户明确确认。

### 3. Temporal 约束

生产环境不采用 Temporal 的 LangGraph Python plugin，不在 `ai-agent-orchestrator` 中注册 Python Temporal Worker。所有业务 Temporal Worker 必须继续作为 Bean/进程组件嵌入对应 Spring Boot 微服务。

Python Agent 如需启动长业务流程，只能生成注册的 Global Action，由 owner service 启动或 signal Java Temporal Workflow。Python 作为 Temporal Client 的任何新增用途必须单独进行 ADR 评审，并使用标准 JSON 或 Protobuf 契约。

### 4. 状态与持久化

- 生产 Agent checkpoint 使用 PostgreSQL，内存存储只用于测试或本地临时运行。
- AgentState 必须使用 Pydantic schema，并包含 graph/thread/run/tenant/actor/trace/version 等受控字段。
- checkpoint 只保存 AI 推理与交互状态，不是业务事实来源。
- 图、State、Prompt 和 tool schema 必须版本化；旧 thread 不得静默加载不兼容的新版本。
- 当前只允许受信租户 `default`，但 checkpoint、缓存、检索、trace 和 ActionCandidate 必须保留 tenant namespace。

### 5. 工具与安全

Agent 工具仅允许：

- `READ`：经过当前用户权限和数据范围检查的只读查询。
- `RETRIEVAL`：受知识空间权限控制的检索。
- `ACTION_CANDIDATE`：基于已注册 ActionDefinition 生成候选动作。

禁止任意 HTTP、SQL、Shell、动态代码、自由 Prompt 工具、直接业务 mutation 和直接模型供应商调用。所有工具参数、返回值、模型结构化输出和 Generative UI props 必须通过 schema 校验。

用户消息、检索文档、模型输出和工具返回均按不可信数据处理，不得修改 system policy、tool allowlist、actor、tenant、权限、确认规则或组件注册表。

### 6. 流式与可观测性

所有 Agent 文本及中间状态使用版本化 SSE 事件流，至少支持运行创建、文本增量、证据、缺参、候选动作、确认、Action 状态、完成、失败和 heartbeat。断线恢复只能重放事件，不能重新创建运行或重复提交业务动作。

网关注入的 TraceId 和 correlationId 必须贯穿 Agent、LLM Gateway、RAG、工具、Global Action、owner service 和 Temporal。日志、checkpoint、trace 和评测样本不得保存授权头、密钥、原始高敏 Prompt 或不必要的敏感业务正文。

### 7. 生产准入

Agent 版本发布必须通过图执行、checkpoint 恢复、interrupt、SSE 重连、取消、工具白名单、Prompt Injection、权限、默认租户隔离和首批业务黄金数据集评测。

以下属于零容忍门禁：

- 未注册的业务副作用。
- 未经确认的 LUI/Agent 状态变更。
- 越权数据读取。
- 跨租户数据访问。
- 直接调用模型供应商。
- Python Temporal Worker 承担生产业务流程。

## 备选方案

### OpenAI Agents SDK

优点是轻量，并内置 handoff、guardrail、session 和 tracing。它适合以 OpenAI 模型为主、编排较简单的应用；TrioBase 需要多供应商模型治理和显式复杂图，因此不作为当前主框架。

### Microsoft Agent Framework

它整合了 AutoGen 与 Semantic Kernel 的演进方向，并提供 Agent 与图工作流能力。该方案更适合微软/.NET 和 Azure 深度集成环境；TrioBase 当前 Python/LangGraph 基线更成熟，迁移收益不足。

### Google ADK

它支持多 Agent、Graph Workflow、评测和部署，适合 Gemini/Vertex AI 体系；TrioBase 当前没有绑定该生态。

### AutoGen

AutoGen 不再作为 TrioBase 新模块的默认候选。微软已经将 Microsoft Agent Framework 定位为 AutoGen 与 Semantic Kernel 的直接继任方向。

### 原生 Python 状态机

依赖少，但 checkpoint、interrupt、恢复、可视化和长期维护都需要自行建设，不符合当前快速形成企业 Agent 底座的目标。

## 后果

### 正面影响

- Agent 开发、状态、工具、安全和评测形成统一基线。
- AI 推理与确定性业务执行职责清晰。
- 保持模型供应商无关，并能利用现有 Global Action 和 Java Temporal 能力。
- 请假和费用报销可以复用同一 Business Assistant Graph，通过领域 schema 扩展。

### 负面影响

- 团队需要掌握 LangGraph 的图、checkpoint、interrupt 和版本迁移机制。
- AI 状态与业务状态分属不同运行时，需要严格的引用和一致性处理。
- 生产环境需要新增 PostgreSQL checkpoint、事件恢复、评测集和 Agent 可观测性建设。

## 复审触发条件

- LangGraph 停止维护、出现重大兼容性或安全问题。
- TrioBase 战略性绑定 OpenAI、Azure 或 Vertex AI，且专属框架带来明确收益。
- Agent 场景长期只有简单单轮工具调用，LangGraph 复杂度明显高于收益。
- 出现需要低风险 Agent 无人值守执行的正式业务需求。
- Python 需要参与 Temporal Workflow/Activity Worker，触及现有铁律 5。

## 官方依据

- [LangGraph overview](https://docs.langchain.com/oss/python/langgraph/overview)
- [LangGraph interrupts](https://docs.langchain.com/oss/python/langgraph/interrupts)
- [LangGraph persistence](https://docs.langchain.com/oss/python/langgraph/persistence)
- [OpenAI Agents SDK](https://openai.github.io/openai-agents-python/)
- [Microsoft Agent Framework overview](https://learn.microsoft.com/en-us/agent-framework/overview/agent-framework-overview)
- [Google Agent Development Kit](https://google.github.io/adk-docs/)
- [Temporal LangGraph integration](https://docs.temporal.io/develop/python/integrations/langgraph)

以上官方资料于 2026-07-24 核对。
