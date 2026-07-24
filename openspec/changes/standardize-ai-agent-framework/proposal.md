## Why

TrioBase 已具备 LLM 网关、RAG 和 Global Action 基础，但 Agent 编排模块尚未形成统一的框架边界、状态模型、安全约束和验收标准，容易出现框架混用、Agent 直连业务写接口、重复建设持久化能力等问题。现在需要在进入“问得到、办得到”的首个业务闭环前，固化 LangGraph、Global Action 与 Java Temporal 的职责边界。

## What Changes

- 将 LangGraph 确立为 `ai-agent-orchestrator` 的标准 Agent 编排运行时，适用于多步骤、有状态、可暂停恢复和需要人工介入的 AI 流程。
- 明确简单分类、单次模型调用和确定性数据转换不得为了统一形式强制包装成 LangGraph 图。
- 定义 Agent 图、节点、状态、checkpoint、interrupt、tool、错误、取消和恢复的统一契约。
- 规定 Agent 只能生成和校验 `ActionCandidate`；所有业务状态变更必须通过 Global Action 完成授权、确认、幂等、审计与 owner dispatch。
- 规定跨服务长事务仍由嵌入 Spring Boot 微服务的 Java Temporal Workflow 承担；Python Agent 不承载业务 Temporal Worker。
- 统一 SSE 事件协议、TraceId/会话关联、敏感数据治理、Prompt Injection 防护、租户命名空间预留、观测、评测和上线门禁。
- 以“请假申请”和“费用报销”作为首批端到端验收场景，覆盖知识问答、表单参数补全、操作预览、人工确认和执行结果回传。

## Capabilities

### New Capabilities

- `ai-agent-orchestration-runtime`: TrioBase Agent 编排框架规范，覆盖 LangGraph 适用边界、图与节点契约、状态持久化、流式协议、人工介入、工具与业务动作边界、安全治理、Temporal 协作、观测评测和生产门禁。

### Modified Capabilities

<!-- 现有 lui-agent-action-bridge 与 global-action-runtime 已定义业务副作用边界；本变更引用并落实这些契约，不改变其既有需求。 -->

## Impact

- Python AI 服务：`trio-base-ai/ai-agent-orchestrator` 成为 LangGraph 标准宿主；`ai-intent-router`、`ai-rag-service` 和 `ai-llm-gateway` 按职责通过显式契约协作。
- Java 服务：`service-action` 保持所有业务副作用入口；`service-workflow-engine` 继续以 Spring Boot 内嵌 Temporal Worker 承担长业务流程。
- 前端：LUI 通过 SSE 消费标准事件，通过 Component Registry 展示回答、缺参、ActionCandidate、确认和执行结果，不直接修改 DOM 或业务数据。
- 数据与基础设施：需要 Agent checkpoint/store、会话/线程标识、TraceId、受控租户命名空间、审计关联和评测数据集。
- 工程治理：新增 Agent 架构测试、安全测试、流式协议测试、图恢复测试、工具白名单测试和首批业务闭环验收。
