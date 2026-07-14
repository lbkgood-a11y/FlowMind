## Context

当前 `service-workflow-engine` 已包含流程包、流程实例、任务、节点记录、Temporal Workflow/Activity 和基础审批接口，前端也已有 X6 设计器、流程包、实例和任务页面。但当前实现仍存在以下结构性缺口：

- ROLE/DEPT 参与者只被序列化为未解析占位信息，普通审批任务没有真实候选人。
- 条件路由只识别字面量 `true`，费用报销示例中的数值条件不会执行。
- 流程包以 `process_key` 单列唯一，发布后无法派生新版本，前端编辑动作也没有后端契约。
- 流程发起依赖手写 JSON，没有复用流程包中的表单 Schema，也没有服务端 Schema 校验。
- 转办和加签直接修改数据库或创建任务，没有完整的 Workflow 状态协调与操作审计。
- Java 模块没有测试，流程前端无法通过 typecheck，当前代码只能证明可编译，不能证明闭环行为。

流程运行时必须遵守 Temporal 确定性、Workflow 禁止 I/O、Activity 幂等、Worker 与 Spring Boot 共生命周期以及 TraceId 贯通等工程铁律。

## Goals / Non-Goals

**Goals:**

- 跑通费用报销的端到端审批闭环，并将其固化为自动化验收场景。
- 建立可持续演进的流程包版本模型和不可变发布语义。
- 实现 ROLE、USER、DEPT 参与者解析、候选人快照、待办可见性和办理授权。
- 实现安全、确定结果的条件分支，并保持 Temporal Replay 兼容。
- 统一审批、会签、驳回、转办和并行加签的状态流转与审计记录。
- 在 Vue/Vben 前端中基于 JSON Schema 渲染流程发起表单，并由服务端再次校验。
- 建立后端、Temporal、前端类型检查和端到端测试门禁。

**Non-Goals:**

- 不实现 NOTIFY、TIMER、SUB_PROCESS、SERVICE_TASK、SAGA、EVENT 或通用网关节点。
- 不实现通用页面搭建器、数据源编排、打印引擎和流程分析看板。
- 不实现 AI 生成流程包或 Agent 自动触发流程。
- 不实现运行中实例迁移到新流程版本。
- 不提供任意脚本执行能力。

## Decisions

### 1. 流程包由 workflow-engine 管理，表单在发布时快照

`service-workflow-engine` 继续拥有流程包定义、版本、发布状态和实例绑定。`service-lowcode` 只拥有可编辑的表单定义及其字段元数据。流程包草稿可以引用一个低代码表单定义，但发布时必须把 `schemaJson` 和 `uiSchemaJson` 固化到流程包版本中。

这样运行中的 Workflow 只依赖自身启动参数和流程包快照，不会因低代码表单后续修改或服务不可用而改变行为。备选方案是把流程包迁移到 `service-lowcode`，但会引入运行时跨服务依赖和更模糊的所有权，因此不采用。

### 2. 发布版本不可变，草稿通过派生创建新版本

数据库唯一约束从 `process_key` 调整为 `(process_key, version)`。同一 `process_key` 同时最多存在一个 DRAFT：

- 新流程从 version 1 DRAFT 开始。
- DRAFT 可以通过更新接口修改。
- 发布后该记录不可修改，只允许下架。
- 编辑已发布流程时，通过“创建新版本”复制最新版本为 `version + 1` 的 DRAFT。
- 流程实例继续保存 `process_package_id` 和 `version`，始终执行启动时版本。

发布动作必须在同一事务中完成结构校验、表单快照校验和状态更新。

### 3. 普通审批使用候选人快照，会签使用独立任务

新增 `wf_task_candidate` 保存普通审批任务在节点进入时解析出的用户快照。普通审批节点只创建一个任务：

- USER 通常产生一个候选人。
- ROLE 和 DEPT 可以产生多个候选人。
- 任一候选人首次办理时，通过条件更新原子认领任务并写入 `assignee_id`。
- 非候选人即使拥有接口权限也不能办理该任务。

COUNTERSIGN 节点为每个参与者创建独立任务，ALL/ANY 计票由 Workflow 状态管理。参与者解析结果同时写入节点记录的 `assignee_snapshot`，组织或角色变化不会影响已进入节点的任务。

如果解析结果为空，系统不得创建无主任务；节点记录为 FAILED，流程实例转为 SUSPENDED，并记录可诊断原因。

### 4. 参与者查询只在 Activity 中执行

Workflow 代码不直接调用认证或组织服务。`ProcessActivity` 通过短超时的 OpenFeign 只读接口完成：

- ROLE：认证服务按角色编码返回启用用户。
- USER：认证服务校验用户存在且启用。
- DEPT：组织服务解析组织单元，并返回有效成员。

调用必须配置连接/读取超时、有限重试和明确错误分类，目标仍是小于 500ms 的纯查询。Activity 使用由 `instanceId + nodeId + participantVersion` 构成的业务幂等键，重试不得重复写候选人或任务。

认证、组织和低代码服务提供不经过平台网关暴露的 `/internal/v1/**` 批量只读端点。内部端点通过 Nacos 配置的服务令牌鉴权，并校验调用方服务名；后续可以平滑替换为 mTLS 或统一服务身份。候选人数上限作为 `workflow.participants.max-candidates` 配置项提供，默认值为 200。

### 5. 条件表达式在 Activity 中通过受限 JEXL 执行

采用 Apache Commons JEXL 的 restricted permissions/sandbox 模式，表达式上下文只暴露已反序列化的表单字段和少量只读内建函数。禁止类加载、构造器、反射、Bean 访问、文件、网络和任意方法调用。

条件评估作为 Activity 执行，结果记录在 Temporal History 中。这样升级表达式库不会改变已记录事件的 Replay 结果。支持 MVP 所需的比较、布尔、空值和基础算术运算；每组条件必须包含最后的 `true` 默认分支。表达式解析或执行失败时节点失败，不能静默选择最后一条边。

### 6. 所有任务动作由 Workflow 信号协调

审批、驳回、转办和加签都先做数据库级授权/幂等检查，再发送带 `operationId` 的 Workflow Signal。新增 `wf_task_operation` 记录操作人、动作、来源任务、目标用户、意见、TraceId 和时间。

- 审批：候选人原子认领并完成任务，重复 `operationId` 返回原结果。
- 驳回：只能终止流程或退回已经访问过且允许退回的节点。
- 转办：原任务标记 TRANSFERRED，创建关联的新 PENDING 任务并记录候选人快照。
- 加签：MVP 只支持“并行必审加签”；Workflow 把新增任务加入当前节点等待集合，全部必审任务完成后才能离开节点。

数据库写入 Activity 必须幂等。Signal 只更新 Workflow 内存状态或触发 Activity，不能在 Workflow 中执行 I/O。

### 7. 动态表单使用组件注册表和前后端双重校验

前端基于现有 Vben Form/Ant Design Vue 组件建立固定字段注册表，第一版支持 string、textarea、number、money、integer、boolean、enum/select 和 date。`uiSchema` 只能选择注册组件及受控属性，禁止动态组件字符串、HTML 注入或 `eval()`。

前端提交前执行 JSON Schema 校验；`service-workflow-engine` 在创建流程实例前使用同一份发布快照再次校验。服务端校验失败时不创建实例，也不启动 Temporal Workflow。

未知字段类型在设计器发布校验时被拒绝，而不是在运行时静默降级。前端提交的 `processPackageId`/version 只用于并发检查，服务端仍按已发布记录解析实际版本。

### 8. 发布前执行流程定义静态校验

发布校验至少包含：

- 恰好一个 START，至少一个 END。
- 节点 ID 唯一，所有连线目标存在，从 START 可达所有运行节点。
- 本次 MVP 只允许 START、APPROVAL、COUNTERSIGN、END；设计器可以展示未来节点，但发布时必须阻止未实现节点。
- APPROVAL/COUNTERSIGN 必须配置有效参与者，COUNTERSIGN 必须配置 ALL 或 ANY。
- 条件分支必须有唯一默认分支，表达式必须可解析。
- 表单 Schema 和 UI Schema 必须合法且只引用受支持组件。

### 9. 测试以可恢复的费用报销场景为主线

使用 Temporal `TestWorkflowEnvironment` 验证 Workflow、Signal、Replay 和计票逻辑；使用 PostgreSQL Testcontainers 验证迁移、候选人认领和事务幂等；使用前端 typecheck、组件测试和 Playwright 验证动态表单与任务操作。

费用报销验收覆盖 3000 元直接结束、8000 元进入财务审批、驳回、转办、并行加签、无候选人挂起，以及 Worker 重启后继续办理。

## Risks / Trade-offs

- [跨服务参与者查询可能超时] → 只在节点进入 Activity 中调用，设置短超时和有限重试，并把失败转为可诊断的 SUSPENDED 状态。
- [角色包含大量用户导致候选人膨胀] → MVP 设置单节点候选人数上限并记录指标，后续再引入候选组或延迟展开。
- [JEXL 表达式存在安全面] → 使用 restricted permissions、禁用方法与类访问、限制长度和执行时间，并只暴露表单值。
- [数据库状态与 Temporal Signal 之间存在短暂不一致] → 使用 operationId、幂等 Activity 和可重放操作日志；失败时允许安全重试。
- [并行加签增加 Workflow 状态复杂度] → MVP 只支持并行必审，不提供前加签、后加签或减签。
- [前端设计器已经存在类型债务] → 先恢复依赖安装和 typecheck，再增加新交互，禁止带着类型错误进入验收。

## Migration Plan

1. 新增数据库迁移：将流程包唯一键改为 `(process_key, version)`，为现有记录回填 version 1，并增加单草稿约束、候选人表和操作日志表。
2. 先部署兼容旧读接口的新后端；已有流程包和实例无需转换 process JSON。
3. 增加参与者查询接口或客户端，并在测试环境完成 ROLE/USER/DEPT 解析验证。
4. 启用新发布校验和版本 API；旧的创建、查询、发布、下架接口保持兼容。
5. 部署动态表单与任务中心前端，确认 typecheck 和端到端场景通过后再开放菜单。
6. 回滚时可回退应用版本并保留新增表；只有在不存在重复 process_key 版本时才恢复旧单列唯一索引。

## Open Questions

当前没有阻断实施的开放问题。服务身份从共享服务令牌升级为 mTLS/OAuth2 Client Credentials 的时机留待平台安全基线 change 决定。
