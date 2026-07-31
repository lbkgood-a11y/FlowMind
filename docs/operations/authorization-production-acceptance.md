# 权限生产上线验收

本流程用于把权限管理模式从 `MIGRATION` 切换到 `PAGE_CAPABILITY`。上线依据必须来自服务端重新计算的兼容性证据，不能用人工勾选或前端状态替代。

## 1. 准备租户目录

系统页面能力清单是租户中立模板，启动时只为显式配置的租户生成目录：

```text
TRIOBASE_AUTHORIZATION_MANIFEST_TENANTS=tenant-a,tenant-b
```

应用配置项为 `triobase.authorization.manifest-tenants`。未配置时启动同步会跳过并告警，不会落入 `default` 租户。

## 2. 修复历史表单组织归属

新表单实例从受治理的授权主体证据记录主组织。历史记录保持 `UNRESOLVED`，使用内部接口分批修复：

```http
POST /internal/v1/form-ownership/reconcile?tenantId=tenant-a&limit=200
X-Internal-Service: service-auth
X-Internal-Token: <internal-token>
```

接口是幂等的，仅更新组织归属为空或来源为 `UNRESOLVED` 的记录。组织服务无法确认主组织时记录继续保持未确认，组织范围访问会排除该记录。

未确认数量：

```http
GET /internal/v1/form-ownership/unresolved-count?tenantId=tenant-a
```

上线验收看板会读取此数量；低代码诊断不可用或未确认数量大于零时，服务端拒绝切换。

## 3. PostgreSQL 强制验收

发布流水线执行：

```powershell
mvn -pl trio-base-services/service-auth -Pproduction-acceptance test
```

默认使用 Testcontainers PostgreSQL 16。Docker 不可用时测试失败而不是跳过。也可以使用专用验收数据库：

```text
AUTH_ACCEPTANCE_DB_URL=jdbc:postgresql://host:5432/triobase_acceptance
AUTH_ACCEPTANCE_DB_USERNAME=postgres
AUTH_ACCEPTANCE_DB_PASSWORD=***
```

该数据库必须是隔离的验收库。测试运行真实 Flyway 迁移，并验证双租户隔离、发布证据不可变、跨租户外键拒绝和上线约束。

## 4. 切换前检查

- 页面能力全部为 `READY`。
- 每个启用角色都有活动发布版本。
- 发布证据与运行时授权完全一致，无缺失、无额外扩权。
- 扩权审查、开放漂移、低代码未确认组织归属均为零。
- 兼容性看板的数据库查询数不随角色数增长，角色明细最多返回 500 条。
- `production-acceptance` 流水线通过。

切换成功后普通管理接口不能回退到 `MIGRATION` 或 `LEGACY`。故障恢复使用不可变发布版本回滚，不重新开启旧权限写入。
