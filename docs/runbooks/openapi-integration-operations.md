# OpenAPI 集成运维手册

## 发布前检查

- 结构、映射、值映射、连接器、路由/编排、回调配置均为 PUBLISHED。
- 所有必填映射覆盖、契约测试和 URL/网络策略校验通过。
- secret reference 可从 Vault 解析，但日志和 API 不出现明文。
- PROD 产品/订阅完成资产负责人和平台管理员双重审批。
- `platform-gateway` 与 `service-openapi` 上报的策略版本均为 CURRENT。
- Temporal task queue 必须等于 `service-openapi`，Worker 指标无积压。

## 常见告警处理

- `OpenApiPolicyLag`：停止高权限发布，检查策略分发和 applied-version 上报，恢复前保持 fail-closed。
- `OpenApiCallbackBacklog`：确认 Temporal 可用、Worker 在轮询、收件箱 `SIGNALING` 超时记录已被重置。
- `OpenApiCallbackQuarantine`：按 event ID、correlation、应用和时间检查；只可关联到同租户同客户端且处于 `WAITING_CALLBACK` 的执行。
- `OpenApiPartnerFailures`：检查连接器目标、DNS/出口、TLS、凭证有效期和伙伴 5xx；不要临时开放任意 URL。
- `OpenApiCompensationFailure`：立即人工确认外部副作用状态，记录处置证据，禁止盲目重复非幂等操作。
- `OpenApiCacheErrors`：运行时会回退 PostgreSQL；检查 Redis 后确认 active release 与 snapshot hash 一致。

## 回滚

选择同 route/environment 的历史 PUBLISHED release，执行原子 rollback，填写原因并确认 `RELEASE_ROLLED_BACK` 审计事件。随后检查缓存 eviction、网关策略版本和首批请求 TraceId。

## 凭证事件

发现泄露时立即 revoke binding 和 secret manager 中的版本，发布新策略快照并确认两处 enforcement point 已应用。轮换时允许短重叠窗口，窗口结束后必须 retirement 旧版本。

## Callback 重放或签名攻击

检查时间戳、nonce、body hash 和 profile version；不要向调用方暴露关联是否存在。达到安全违规阈值时暂停客户端。重复 partner event ID 且 body hash 相同返回幂等应答；相同事件 ID 不同 body 必须拒绝。

## 灾难恢复

PostgreSQL 是元数据与回调收件箱的事实来源；Redis 可重建。恢复 PostgreSQL 后先执行 Flyway validate，再恢复 Temporal Worker，确认历史可回放，最后启用公开路由。Temporal 恢复后 callback dispatcher 会继续投递 `SIGNAL_PENDING` 记录。

## Trace 排障

从网关 `X-B3-TraceId` 或 `traceparent` 开始，依次检查 admission、release resolution、workflow header、Activity attempt、outbound request 和 callback inbox。执行查询接口只返回脱敏错误与证据；需要 body 时必须使用授权诊断捕获，最长保留七天。

## 发布前交付验收

运行 `bash cicd/scripts/verify-openapi-contract.sh` 与 `bash cicd/scripts/verify-openapi-acceptance.sh`。验收包括负载/配额/并发、策略漂移、凭证撤销、伙伴失败、秘密泄漏、SSRF、回调 replay、Temporal replay 兼容、release rollback 与数据库恢复。任何门禁失败时保持 `OPENAPI_RUNTIME_ENABLED=false`，且不得启用网关 public runtime route。
