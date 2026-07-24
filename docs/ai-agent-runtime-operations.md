# AI Agent Runtime 运维手册

## 运行边界

`ai-agent-orchestrator` 负责 LangGraph 对话与人工介入状态，不是业务事实来源。业务状态变更只能通过 `service-action`，跨服务长流程只能由 Spring Boot 内嵌 Temporal Worker 执行。

当前正式场景只启用默认租户 `default`。请假应用使用运行时标识 `leave / leave`；费用报销使用 `expense_report / expense`，由独立功能开关控制。知识问答和多 Agent 默认关闭。

## 本地依赖

- Python 3.12
- PostgreSQL 16 + pgvector
- Redis 7（LLM Gateway 缓存默认关闭）
- platform-gateway 8080
- service-auth 8081
- service-lowcode 8085
- service-workflow-engine 8086
- service-action 8089
- ai-llm-gateway 8002
- ai-rag-service 8003
- ai-agent-orchestrator 8004

LLM 密钥只能通过 `OPENAI_API_KEY` 或 `DEEPSEEK_API_KEY` 环境变量交给 LLM Gateway，不能配置在 Agent 服务、代码或日志中。

## 关键配置

| 环境变量 | 生产要求 |
|---|---|
| `AGENT_ENVIRONMENT` | `production` |
| `AGENT_CHECKPOINT_BACKEND` | 必须为 `postgres` |
| `AGENT_DATABASE_URL` | 独立受控数据库连接 |
| `AGENT_LLM_GATEWAY_URL` | 必须指向 platform-gateway，不能直连模型或绕开第一层脱敏 |
| `AGENT_PLATFORM_GATEWAY_URL` | platform-gateway 的 `/api/v1` 根地址 |
| `AGENT_DEFAULT_MODEL` | LLM Gateway 已登记的模型 |
| `AGENT_OTLP_ENDPOINT` | OTLP/Jaeger Collector 地址 |
| `AGENT_CHECKPOINT_RETENTION_DAYS` | 由安全与合规确认，默认 30 天 |
| `AGENT_ENABLED` | 生产灰度开关；回滚时设为 `false` 停止创建新 Run |
| `AGENT_MAX_CONCURRENT_INVOCATIONS` | 同时执行的图调用数，默认 16 |
| `AGENT_MAX_QUEUED_INVOCATIONS` | 执行中与排队中的调用上限，默认 64；超限返回 `AGENT_CAPACITY_EXCEEDED` |
| `VITE_AGENT_ASSISTANT_ENABLED` | 前端生产构建显式设为 `true` 才显示全局 AI 助手；开发环境默认开启，可设 `false` 关闭 |

生产模式使用 memory checkpoint 会在启动时被拒绝。

## 启动与探针

```powershell
docker compose -f docker/docker-compose.yml up -d postgres redis jaeger ai-llm-gateway ai-rag-service ai-agent-orchestrator
```

- 存活：`GET http://localhost:8004/health`
- 就绪：`GET http://localhost:8004/ready`
- 指标：`GET http://localhost:8004/metrics`

业务请求必须经过 `http://localhost:8080/api/v1/agent/**`，不得让浏览器直接访问 8004。

Windows 本地以 `python -m ai_agent_orchestrator.main` 启动，入口会切换到 Psycopg 异步连接池要求的 Selector 事件循环；不要直接用 `python -m uvicorn ...` 绕过该入口。

## 指标与告警

基础指标：

- `triobase_agent_runs_total{graph_id,status}`
- `triobase_agent_events_total{event_type}`
- `triobase_agent_run_duration_seconds`
- `triobase_agent_node_updates_total{graph_id,node}`
- `triobase_agent_tool_calls_total{tool,kind,status}` 与 `triobase_agent_tool_retries_total`
- `triobase_agent_model_calls_total`、`triobase_agent_model_tokens_total`、`triobase_agent_model_estimated_cost_usd_total`
- `triobase_agent_interrupts_total`、`triobase_agent_cancellations_total`
- `triobase_agent_candidate_validations_total`、`triobase_agent_action_outcomes_total`

Grafana 可导入 `docs/observability/ai-agent-runtime-grafana-dashboard.json`，覆盖 Run 成功率/P95、LLM 首 Token、Token/成本、缓存、重试、人工中断、候选校验和最终 Action 结果。

建议告警：

- 5 分钟内 `FAILED / (COMPLETED + FAILED)` 超过批准阈值。
- P95 run duration 或首个业务事件时间连续超阈值。
- `ACTION_AUTHORIZATION_DENIED`、`AGENT_RUN_TIMEOUT` 突增。
- checkpoint 数据库不可用、SSE 重连率持续升高。
- 任一未注册工具、非默认租户或敏感数据门禁命中。

日志只能记录运行标识、模型、脱敏 hash、长度、状态和有限错误码；禁止记录原始 Prompt、Authorization、密钥、身份证、银行卡和未脱敏业务正文。

## 故障处理

### LLM Gateway 不可用

`AGENT_LLM_MODE=auto` 时，请假常见字段可使用确定性提取降级；不能确定的字段必须追问，不得猜测。`gateway` 模式直接返回有限错误。

### Action 提交结果未知

不要重新生成新的幂等键。使用候选动作中的原幂等键查询 Global Action；LangGraph 不自动重试状态变更。

### SSE 断线

客户端以最后 `sequence` 作为 cursor 重连。重连只重放事件，不创建新 Run、不重新 dispatch Action。

### checkpoint 恢复失败

检查 graph version 与 state schema version。禁止把旧 checkpoint 静默加载到不兼容版本；启用旧图版本或执行已评审迁移。

## 灰度与回滚

1. 仅为内部普通员工测试角色启用全局 AI 助手。
2. 验证请假成功、缺参、取消、越权、重复确认、服务重启和断线恢复。
3. 第一阶段仅启用请假；请假稳定后再独立开启费用报销灰度，知识问答继续关闭。
4. 回滚时关闭 Agent 功能开关并停止创建新 Run；已经提交的 Global Action 和 Java Temporal Workflow继续由各自运行时完成。
5. 保留旧 graph version 到对应 thread 超出保留期。
