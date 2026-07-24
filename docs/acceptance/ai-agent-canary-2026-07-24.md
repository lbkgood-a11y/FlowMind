# AI Agent 受控灰度验收记录（2026-07-24）

## 范围与环境

- 环境：本地生产模式基线，默认租户 `default`
- 统一入口：`platform-gateway /api/v1/agent/**`
- Agent：LangGraph 1.x + PostgreSQL checkpoint，当前灰度模型模式 `AGENT_LLM_MODE=mock`
- 业务链路：`ai-agent-orchestrator -> service-action -> service-lowcode -> service-workflow-engine -> Temporal`
- 灰度原则：所有首期业务写动作必须停在候选确认点；只有携带可信用户确认信息的 ActionCandidate 才允许进入 Global Action

## 验收结论

请假与费用报销两条灰度业务链路均已完成验收。费用报销首次启动暴露了授权目录和工作流数据库迁移漂移问题；修复后通过既有 `lowcode.workflow.retry` 动作恢复同一业务单据，没有绕过 Global Action，也没有重复创建报销单。

| 检查项 | 结果 | 证据 |
|---|---|---|
| 请假应用运行时映射 | 通过 | `leave / leave`，发布动作 `CREATE` |
| 请假候选、确认、授权与写入 | 通过 | Run `33f89d7b-3141-4a9e-95a8-3a193c679205`，Action `act_84fb2422913840f490102ffd035e589a` |
| 请假结果关联 | 通过 | 表单实例 `01KY9Q0KQRT9E6ZPDPXYW86K7K`，Run 状态 `COMPLETED`，Action 与实例均进入 `actionRefs` |
| 请假 UTF-8 数据 | 通过 | 日期 `2026-07-25`、申请人 `admin`、理由“家中有事”均按原文存储 |
| 报销应用运行时映射 | 通过 | `expense_report / expense`，发布动作 `submitAndLaunch` |
| 报销候选与用户确认 | 通过 | Run `f7b022d5-d4bd-48c9-a080-78c7c2377f2f`；金额 `128`、部门“研发部”、事由“灰度测试” |
| Action 失败反馈 | 通过 | 首次 Action `act_83454a3efad4470ca3d1e5eeb9652394` 失败后，Agent Run 正确进入 `FAILED`，不再误报 `COMPLETED` |
| 报销受控恢复 | 通过 | 恢复 Action `act_060672d2186a4e388a978f9022455342`，状态 `SUCCEEDED` |
| 报销单据不重复 | 通过 | 始终复用表单实例 `01KY9NSYJDS45DZ7PB4PXMEPMX`，恢复后 `workflowStatus=RUNNING` |
| Java/Temporal 流程启动 | 通过 | 流程实例 `01KY9PFZGY0PJPW980Z7BB3FE4`，Workflow ID `process-01KY9PFZGY0PJPW980Z7BB3FE4` |
| 审批待办落地 | 通过 | 待办 `01KY9PG35RVJ0NJ55F7K3XJN8W`，节点“部门审批”，状态 `PENDING` |
| 默认租户隔离 | 通过 | 候选 actor、target、context 及业务实例均为租户 `default` |
| 资源级授权 | 通过 | 请假使用 `LOWCODE_FORM:LEAVE/CREATE`；报销使用 `LOWCODE_FORM:EXPENSE/SUBMIT|EDIT` |
| 后端回滚开关 | 通过 | `AGENT_ENABLED=false` 时健康信息显示禁用，新建 Run 返回 HTTP 503；恢复后 checkpoint 中的候选仍可查询 |
| 服务恢复 | 通过 | `service-lowcode`、`service-workflow-engine` 重建后均通过实际 API 就绪检查 |

## 灰度中发现并修复的问题

1. 请假应用仍使用旧占位标识，已改为实际发布标识 `leave / leave`。
2. 候选动作曾固定为 `lowcode.form.submit`，现按应用动作优先级动态映射 Global Action。
3. ActionCandidate 缺少可信 actor 与表单级资源码，现由 Gateway 上下文注入 `U001/admin`，并携带 `LOWCODE_FORM:<FORM_KEY>`。
4. Gateway 与 Agent 的用户名请求头命名不一致，现兼容平台标准头 `X-Username`。
5. 费用报销授权目录缺少表单与应用资源，已通过 `V71`、`V72` 注册资源、动作与字段。
6. 首次报销启动时，`wf_process_instance` 等表缺少已被 Flyway 历史标记成功的 Action 元数据列；`V73` 以幂等迁移修复数据库漂移。
7. Agent 曾把失败的 Global Action 映射为成功，现将 `FAILED/REJECTED` 映射为 Agent Run `FAILED`，并保留可重试与安全错误信息。
8. 低代码到工作流的内部 JSON 契约曾丢失 Action 元数据；现透传 Action、actor、TraceId 与 correlationId，并在工作流启动时写入流程实例、注入 Temporal Trace 上下文。
9. PowerShell 5 验收脚本曾错误解码无 charset 的中文响应，产生一条乱码请假测试记录。正式验收改用显式 UTF-8 客户端并重新执行；该历史测试数据未绕过 Global Action 直接修改。
10. Docker Compose 默认项目名与本机其他项目冲突，已固定为 `triobase`。

## 验证结果

- `ai-agent-orchestrator`：`23 passed`
- `service-lowcode + service-workflow-engine`：全量 Maven 测试完成，无失败
- `ProcessInstanceLaunchIdempotencyTest`：`1 passed`
- Java 发布构建：`service-lowcode`、`service-workflow-engine` 均 `BUILD SUCCESS`
- 数据库：`V71`、`V72`、`V73` 均成功应用
- 运行态：请假 Run `COMPLETED`；报销表单 `RUNNING`；报销流程 `RUNNING`；部门审批待办 `PENDING`

## 发布与回滚

- 灰度开启：`AGENT_ENABLED=true`，通过 Gateway 暴露 Agent 路由。
- 快速止血：设置 `AGENT_ENABLED=false` 并重启 Agent；业务 GUI、Global Action、低代码与 Java Workflow 可继续独立运行。
- 路由级回滚：关闭 Gateway Agent 路由开关；已有 Run/checkpoint 保留，不自动重放业务动作。
- 恢复要求：重新开启前确认 Agent、Action、Lowcode、Workflow、PostgreSQL、Temporal 就绪，并从原 cursor 恢复 SSE。

验收结论：OpenSpec 任务 8.5 满足，允许结束本次受控灰度。真实外部模型供应商接入仍需单独的模型合规、成本与质量灰度，不包含在本次 `mock` 模式验收内。
