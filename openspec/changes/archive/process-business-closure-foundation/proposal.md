## Why

`process-runtime-mvp-closure` 已经让审批流能够端到端跑完，但流程完成仍不等于业务完成：报销单、采购单、工单等真实业务对象的状态回写、权限协同、事件沉淀、通知和 Agent 后续动作尚未形成标准闭环。当前需要把流程平台从“审批运行时”升级为“业务对象驱动的全闭环流程底座”，同时保持配置人员可以通过业务语言、选择器和可视化完成配置，而不是填写技术结构。

## What Changes

- 在 `service-workflow-engine` 内抽取业务对象目录，第一版使用数据库注册业务对象、状态、表单、权限、事件、可选动作和 Agent follow-up 动作；报销单作为首个跑通样例，目录模型保留采购单、工单等扩展能力。
- 业务对象目录支持 `GLOBAL + tenant override`、`DRAFT / PUBLISHED / OFFLINE` 生命周期；流程发布只能引用已发布目录，并在流程版本中固化目录、表单、权限、状态和闭环计划快照。
- 闭环执行采用“数据库注册可选项 + 代码注册执行器”模式；数据库不得注册任意 URL、SQL、脚本、动态类名或自由 Prompt，产生副作用的动作必须通过代码执行器和 `executorKey` 绑定。
- 流程发起兼容两种模式：已有单据发起，以及新建单据并发起；两种模式都必须校验业务对象权限、单据状态门禁和发起策略。
- 增加标准 `ProcessOutcome`、`ClosureRecord`、`ClosurePlan` 和 effect 执行记录，支持 StartEffect、OutcomeEffect、FailureEffect 和 AgentFollowUpEffect。
- 支持全闭环执行：发起时更新业务状态、流程终态回写业务结果、发送领域事件、通知相关人、触发预注册 Agent follow-up；关键 StartEffect 和 OutcomeEffect 可配置硬闭环，通知、事件和 Agent follow-up 默认软闭环。
- 设计器升级为业务对象驱动的流程配置工作台：配置人员先选择业务对象，再通过选择器配置表单、权限、状态流转、流程图、闭环动作和 Agent follow-up；底层编码/JSON 仅允许只读预览。
- 设计器必须提供流程图、单据状态图、闭环动作链和权限矩阵四类可视化，并提供业务语言的完成度检查、发布校验错误和运行时闭环状态展示。
- 增加失败可诊断与安全重试能力；人工标记已处理如纳入第一版必须带审计记录。

## Capabilities

### New Capabilities

- `process-business-object-catalog`: 流程平台内的业务对象目录，覆盖数据库注册、多租户覆盖、目录生命周期、可选表单/状态/权限/动作/事件/Agent 动作，以及发布快照语义。
- `process-launch-policy`: 已有单据发起与新建单据并发起的策略、状态门禁、StartEffect 和发起失败语义。
- `process-business-permission-binding`: 业务对象权限动作与现有 RBAC 权限编码的绑定，以及发起、查看、办理、闭环重试和 Agent follow-up 授权规则。
- `process-outcome-contract`: 流程进入业务结果态时生成唯一 `ProcessOutcome`，并包含流程引用、业务对象引用、结果、操作者上下文和 TraceId。
- `process-business-closure-policy`: 流程包中的业务闭环策略，覆盖 businessRef、结果映射、Start/Outcome/Failure/Agent effect 配置、硬/软闭环选择和发布快照。
- `process-closure-effect-execution`: `ClosurePlan` 驱动的幂等 effect 执行、Outbox、重试、失败诊断、硬闭环阻断和审计语义。
- `process-designer-closure-visualization`: 设计器中的业务对象驱动向导、选择优先配置、流程图/单据状态图/闭环动作链/权限矩阵可视化、业务语言校验和只读技术预览。
- `process-agent-follow-up-configuration`: Agent follow-up 动作目录、预注册动作选择、参数表单、授权校验、执行事件和失败可见性。

### Modified Capabilities

<!-- No existing capability specs are modified; current process capabilities live in an active MVP change and this change extends them with new business-closure capabilities. -->

## Impact

- Java 服务：`service-workflow-engine` 新增业务对象目录、目录查询 API、发布快照、Outcome/Closure 模型、effect 执行器注册表和闭环执行服务；`service-auth` 继续提供 RBAC 权限编码；后续报销样例服务或 fixture 提供受控业务动作执行器。
- 前端：`trio-base-frontend/apps/web-antd` 的流程设计器、流程包管理、实例详情和任务/闭环管理页面；设计器从 JSON 编辑转向业务对象向导与可视化配置。
- 数据库：新增业务对象目录表、目录状态/表单/权限/动作/事件/Agent 动作表、Outcome/Closure/effect/outbox 表，以及必要的种子数据和索引。
- API：新增业务对象目录查询 API、流程发布校验扩展、闭环状态查询、effect 重试、只读技术预览和 Agent follow-up 配置查询接口。
- Temporal 与事件：终态 Outcome、硬闭环 Activity、软闭环 Outbox/Worker、TraceId 透传和幂等键需要与现有 Workflow/Activity 语义兼容。
- 测试与验收：以费用报销跑通已有单据发起、新建单据并发起、审批通过/驳回、硬闭环失败、软闭环重试、四类可视化、权限矩阵和 Agent follow-up 场景。
