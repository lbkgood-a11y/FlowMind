# TrioBase OpenAPI 集成服务

`service-openapi` 提供受治理的外部集成管理面与运行面。所有公共流量必须经过 `platform-gateway`；多步骤、状态变更、重试、等待和回调流程由嵌入服务进程的 Temporal Worker 执行。

## 应用接入

1. 创建接入应用并登记负责人、用途、风险等级和联系人。
2. 为 DEV、TEST、PROD 分别创建客户端；生产客户端需要应用负责人和平台管理员双重审批。
3. 绑定 Vault 凭证引用。服务只返回生成凭证的一次性交付值，不返回已存凭证明文。
4. 申请并审批固定 API 产品版本的订阅，选择最小 scopes；版本升级需单独审批。
5. 发布流控和安全策略快照，确认网关与运行时的 applied version 均为 CURRENT。
6. 使用 `X-Client-Key`、环境头和相应认证材料调用网关公开地址。

## 资产生命周期

- 结构、映射、连接器、路由、编排、回调配置：`DRAFT -> PUBLISHED -> DEPRECATED -> ARCHIVED`。
- 发布版本不可修改；变更必须创建新草稿版本。
- 路由发布生成不可变 release snapshot，固定结构、映射、值映射、连接器、编排和 secret reference。
- 回滚仅原子切换 active release 指针，不修改历史快照。
- 应用/客户端：`DRAFT -> PENDING_APPROVAL -> ACTIVE -> SUSPENDED | EXPIRED | REVOKED`。
- 产品订阅固定到精确语义版本，升级不会自动漂移。

## Mapping DSL

映射规则只接受 JSON Pointer 和白名单操作：`COPY`、常量、默认值、类型转换、拼接、日期、集合和值映射。禁止 JavaScript、SpEL、OGNL、动态类、凭证和 Authorization 常量。发布时执行必填字段覆盖检查和存储的契约测试。

## Orchestration DSL v1

```json
{
  "schemaVersion": "1",
  "start": "submit",
  "steps": [
    {
      "key": "submit",
      "type": "INVOKE",
      "connectorVersionId": "connector-version-id",
      "compensationStep": "cancel-submit",
      "next": "wait-result"
    },
    {
      "key": "wait-result",
      "type": "WAIT",
      "signalName": "partner-result",
      "timeoutSeconds": 3600,
      "next": "end"
    },
    {
      "key": "cancel-submit",
      "type": "COMPENSATE",
      "connectorVersionId": "cancel-connector-version-id"
    },
    { "key": "end", "type": "END" }
  ]
}
```

支持 `INVOKE`、`TRANSFORM`、`BRANCH`、`PARALLEL`、`WAIT`、`COMPENSATE` 和 `END`。条件只能比较 JSON Pointer 值；并行子步骤必须是原子步骤；循环仅允许带 `maxIterations` 的 WAIT 回边。Workflow 合同和 Activity 合同均为 JSON 字符串，所有数据库和网络 I/O 位于 Activity。

## 回调配置

回调地址使用不可猜测的 `callbackKey`：`POST /api/v1/openapi/callbacks/{callbackKey}`。生产配置使用 HMAC 或 RSA 原始字节签名，固定应用客户端、请求结构、可选入站映射、事件 ID Pointer、关联 Pointer、Temporal signal、时间窗、nonce 策略、限额和固定应答。

签名原文为：

```text
timestamp + "." + nonce + "." + raw_request_body
```

有效回调先写入 PostgreSQL 收件箱并完成事件去重，再返回固定应答。Temporal 不可用时记录保持 `SIGNAL_PENDING` 并指数退避重试。未知、歧义、晚到或终态关联进入隔离区，由授权操作员关联、重试或丢弃。

## 流控与安全策略

策略按 tenant、environment、application client、product、route、operation、subscription 分层解析，覆盖请求速率、burst、日配额、body 大小、同步并发、活动工作流、回调量、TLS、来源网络、签名时间窗和敏感字段限制。下层覆盖只能更严格，不能扩权。

策略以不可变签名快照分发。凭证撤销或订阅撤销后，新策略未被网关/运行时确认应用时采用 fail-closed。

## 运行接口

- 同步调用：`/api/v1/openapi/runtime/{routeKey}`。
- 启动编排：`POST /api/v1/openapi/runtime/{routeKey}/orchestrations`，必须提供 `Idempotency-Key`。
- 编排状态：`GET /api/v1/openapi/runtime/orchestrations/{executionId}`。
- 编排结果：`GET /api/v1/openapi/runtime/orchestrations/{executionId}/result`。
- 取消编排：`POST /api/v1/openapi/runtime/orchestrations/{executionId}/cancel`。
- 回调接收：`POST /api/v1/openapi/callbacks/{callbackKey}`。

管理接口统一位于 `/api/v1/openapi/management/**`，涵盖 structures、mappings、value-maps、connectors、routes、releases、orchestrations、callback-profiles、products、applications、subscriptions、policies、executions 和 callback-quarantine。

## 数据留存

- 执行元数据、脱敏错误和步骤证据默认保留 180 天。
- 默认不保存请求/响应 body。
- 显式授权的诊断捕获在敏感字段、凭证、签名和 Authorization 被移除后保存，最长 7 天。
- 定时任务按外键顺序清理诊断、nonce、回调、幂等记录和执行元数据。
