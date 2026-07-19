# Global Action Runtime 开发规范

Global Action 是 TrioBase 的业务变更操作契约。凡是会修改业务状态、启动/推进流程、调用外部副作用、需要重试补偿或进入审计的操作，都必须先表达为 `GlobalActionRequest`，再由 Action Runtime 完成注册校验、payload 校验、授权、幂等、owner dispatch、状态记录和审计关联。

本规范不覆盖本地 UI 事件，例如打开抽屉、切换 tab、刷新列表、编辑未提交的输入值。

## 命名

Action type 使用小写命名空间，格式为 `domain.resource.verb`。

已注册的核心动作：

- `lowcode.form.create`
- `lowcode.form.save`
- `lowcode.form.submit`
- `lowcode.workflow.retry`
- `process.instance.start`
- `process.task.approve`
- `process.task.reject`
- `process.task.transfer`
- `process.task.addSign`
- `process.closure.effect.retry`
- `process.closure.effect.markHandled`
- `integration.orchestration.start`
- `integration.orchestration.cancel`
- `integration.invocation.stateChanging`
- `integration.callback.signal`

新增动作必须先注册 `ActionDefinition`，包含 owner service、target type、payload schema、permission、guard、execution mode、audit level、sensitive paths 和 retry policy。

## Envelope

`GlobalActionRequest` 必填核心字段：

- `actionType`: 注册动作类型
- `source`: `GUI`、`LUI`、`AGENT`、`API`、`EVENT`、`SCHEDULER` 或 `WORKFLOW`
- `actor`: 用户、Agent、服务、系统或调度器身份
- `target`: 目标类型、目标 id、owner service、tenant id、可选版本
- `payload`: 只放动作参数，必须匹配定义里的 schema
- `context`: trace id、request id、correlation id、locale、授权版本、确认信息
- `idempotencyKey`: state-changing 或 retryable 动作必须提供
- `executionMode`: `SYNC`、`ASYNC`、`WORKFLOW` 或 `SIGNAL`

敏感字段只允许在 owner 执行时使用，Action 记录、事件、审计摘要必须使用定义里的 sensitive paths 进行脱敏。

## 生命周期

客户端只消费统一状态：

- `CREATED`
- `VALIDATING`
- `REJECTED`
- `AUTHORIZED`
- `ACCEPTED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCELLED`
- `COMPENSATING`
- `COMPENSATED`

领域状态保存在 `result.data`，例如 `runtimeStatus=WORKFLOW_PENDING`、`workflowStatus=RUNNING`、`effectStatus=RETRYING`，不得再作为跨页面通用状态模型。

## 幂等

Action Runtime 先用 `(tenantId, actionType, idempotencyKey)` 防重复 dispatch。owner service 仍要保留业务级幂等，例如流程启动 key、任务 operation id、闭环 effect id、外部集成 invocation key。

前端使用 `createActionIdempotencyKey(actionType, targetId, ...)` 生成幂等键。后端 owner 不再生成新的页面级 operation id，除非它是业务表的稳定幂等字段。

## 后端接入

公共入口只允许：

- `service-action` 的 Action facade
- owner service 的 `/internal/v1/actions/execute`
- 必要的内部服务接口，例如 workflow 内部启动接口

迁移后的 public runtime/task/closure/form mutation endpoint 必须删除，或者作为非 public internal adapter 存在。新增 public mutation controller 需要通过 `ActionMutationEndpointRule` 架构测试。

owner executor 只做业务所有权内的校验和执行，返回结构化 `ActionOwnerDispatchResponse`。guard 失败必须返回 `ActionErrorCategory.GUARD`，并包含 guard code/message。

## 前端接入

业务变更页面必须使用 `useActionDispatch().dispatchAction()`。Action Client 负责：

- 默认 source、actor、locale
- confirmation
- submit
- normalized status
- structured error
- success refresh

前端不得恢复已删除的 mutation wrapper，例如 `startProcessInstance`、`approveTask`、`runRuntimeApplicationAction`、`retryClosureEffect`。`action-mutation-guard.test.ts` 会阻止这些 wrapper 和旧 endpoint 字符串重新出现。

LUI/Agent 只能产生 `ActionCandidate`。候选动作必须通过注册定义和 schema 校验，敏感或 critical 动作必须确认后才能 dispatch。
