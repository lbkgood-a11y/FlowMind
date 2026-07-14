## Why

TrioBase 已具备流程包、Temporal Workflow、任务中心和 X6 设计器骨架，但参与者没有真实解析、条件表达式没有执行、发起页仍依赖手写 JSON，导致费用报销示例无法形成可验收的端到端闭环。当前需要先把流程运行时收口为稳定 MVP，再继续扩展低代码、业务流和智能体能力。

## What Changes

- 明确流程包归 `service-workflow-engine` 管理，`service-lowcode` 提供可复用表单定义；流程发布时固化表单 Schema 和 UI Schema 快照。
- 增加流程包草稿编辑、不可变发布版本和新版本派生能力，已有流程实例继续使用启动时版本。
- 在 Temporal Activity 中对接认证与组织服务，解析 ROLE、USER、DEPT 参与者并在节点进入时固化任务参与者快照。
- 为待办查询和办理动作增加真实的受理人校验，防止未分配用户处理任务。
- 增加受限、安全的流程条件表达式执行，支持费用报销等基于表单字段的分支路由。
- 收口审批、会签、驳回、转办和加签的任务状态及节点审计行为，并验证 Worker 重启后的流程恢复。
- 前端根据流程包表单 Schema 动态渲染发起表单，补齐设计器参与者配置、发布前校验和任务操作界面。
- 增加后端集成测试、Temporal Workflow 测试和费用报销端到端验收场景，恢复前端类型检查门禁。
- 本次不实现 NOTIFY、TIMER、SUB_PROCESS、SERVICE_TASK、SAGA、EVENT、通用页面搭建器或 AI 生成流程。

## Capabilities

### New Capabilities

- `process-definition-management`: 流程包草稿、版本、发布快照、不可变性和实例版本绑定。
- `process-participant-assignment`: ROLE、USER、DEPT 参与者解析、节点快照、待办可见性和办理授权。
- `process-runtime-execution`: 条件分支、审批与会签状态流转、驳回/转办/加签、审计和 Temporal 恢复语义。
- `process-form-runtime`: 基于流程包 Schema 的安全动态表单渲染、校验和流程发起。

### Modified Capabilities

<!-- No existing process capability specs are modified; process-platform currently contains a design draft only. -->

## Impact

- Java 服务：`service-workflow-engine`、`service-auth`、`service-org`、`common-temporal`、`common-core`。
- 前端：`apps/web-antd` 的流程设计器、流程实例和任务中心页面。
- 网关与配置：`platform-gateway` 的流程路由、服务地址和用户上下文透传。
- 数据库：流程包唯一键与版本模型、参与者快照和必要的审计字段迁移。
- 测试与基础设施：PostgreSQL、Temporal 测试环境、前端 typecheck 和端到端费用报销场景。
- 架构文档：同步调整 `openspec/specs/process-platform/design.md` 中流程包归属和发布快照决策。
