# OpenAPI 受管应用试点

试点应用为 `pilot-erp`，固定到 PROD 客户端、API 产品 `pilot-order-integration@1.0.0` 和 Vault secret reference。试点资产清单位于 `service-openapi/src/test/resources/pilot/managed-application-pilot.json`，不包含任何凭证明文，公开运行时保持关闭。

试点覆盖两条路径：

1. `pilot.orders.get`：只读、单连接器、400ms 超时的同步调用，使用规范请求/响应结构和已存储映射契约测试。
2. `pilot.orders.submit`：状态变更的 Temporal 多步骤流程，经过 transform、invoke、wait-callback、finalize，并以认证、去重、持久化优先的回调恢复工作流。

验收命令：

```bash
bash cicd/scripts/verify-openapi-acceptance.sh
```

该命令验证网关鉴权与 429、敏感字段脱敏、应用/订阅/策略准入、同步调用、出站 TraceId、Temporal Activity 与 replay、回调签名/nonce/信号重试、凭证撤销、策略漂移、回滚迁移以及试点清单。全部通过后仍需由平台管理员显式启用目标网关 runtime route；默认配置不会自动开放公共流量。
