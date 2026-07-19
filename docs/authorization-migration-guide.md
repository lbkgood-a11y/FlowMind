# 授权模型迁移指南

本文档面向需要在 TrioBase 企业授权模型上线后保持兼容的开发者。新授权模型以"角色 → 资源 → 动作"三元组为核心，取代旧式的菜单派生权限模式。

## 单源授权承诺

| 旧模式 | 新模式 | 当前要求 |
|--------|--------|----------|
| `@RequirePermission` | `AuthorizationDecisionService.decide()` | 旧注解可继续作为代码侧声明，但授权事实必须来自 `sys_auth_grant` |
| `@RequireDataScope` | `DataPolicyService.resolveEffective()` | 数据范围仍由授权中心解析，并校验资源/动作已注册 |
| `sys_menu.permission_code` | 菜单可见性投影 | 只用于从 `sys_auth_grant` 反推导航，不再作为独立授权来源 |
| 低代码 `FORM_KEY:ACTION` | `LOWCODE_FORM:FORM_KEY` 资源 | 业务功能统一注册为资源+动作，不保留双轨授权 |

## 迁移步骤

### 第一步：确认服务注册

新应用发布时会自动调用 `AuthorizationResourceSyncClient.syncPublishedApplication()` 将其授权资源注册到 `service-auth`。

验证方式：
```sql
SELECT resource_code, resource_type, owner_service, lifecycle_status
FROM sys_auth_resource
WHERE tenant_id = '<your-tenant-id>'
ORDER BY resource_code;
```

### 第二步：为角色赋予功能权限

使用管理面板 **系统管理 → 企业授权 → 功能权限** 为角色赋予资源动作权限。

等价于调用：
```http
POST /api/v1/authz/grants
{
  "tenantId": "tenant-a",
  "subjectType": "ROLE",
  "subjectId": "R_MANAGER",
  "resourceCode": "LOWCODE_FORM:EXPENSE",
  "actionCode": "VIEW",
  "effect": "ALLOW"
}
```

### 第三步：配置数据范围

使用 **系统管理 → 企业授权 → 数据范围** 为角色配置可见范围。

### 第四步：验证决策

在 **决策预览** 页输入用户、资源、动作，检查决策结果。

或通过 API 查验：
```http
POST /internal/v1/authz/decide
X-Internal-Service: service-lowcode
X-Internal-Token: <token>
{
  "tenantId": "tenant-a",
  "userId": "U001",
  "resourceCode": "LOWCODE_FORM:EXPENSE",
  "actionCode": "VIEW"
}
```

### 第五步：手工单据服务集成

对于不在低代码体系内的手工单据服务（如计费、合同、CRM），参见 `docs/authorization-custom-document-patterns.md`。

## 关键概念映射

| 旧概念 | 新概念 | 说明 |
|--------|--------|------|
| `sys_menu` 菜单权限码 | `sys_auth_resource` + `sys_auth_grant` | 菜单只保存权限码用于投影，持久化授权契约只在 `sys_auth_grant` |
| `@RequirePermission("xxx")` | `AuthorizationDecisionResponse.isAllowed()` | 运行时查询，不再仅依赖静态注解 |
| `@RequireDataScope` | `AuthzDataScopeResult.scopeTypes` | 数据范围策略在 auth 服务端解析 |
| 角色 → 菜单 | 角色 → 资源 → 动作 | 细粒度到单个业务动作 |

## Deny 优先规则

授权引擎按以下顺序决策：
1. 资源+动作未注册 → **Deny**（fail-closed）
2. 显式 Deny 授权 → **Deny**（优先于任何 Allow）
3. 显式 Allow 授权 → **Allow**
4. 管理员角色（ADMIN）→ **Allow**（即使无显式授权）
5. 无匹配 → **Deny**

## 回退约束

当前版本已经退役旧权限 CRUD、`sys_permission` 和 `sys_role_menu` 写入链路，不支持再回退到多套授权事实并行。允许的回退动作是撤销或修正 `sys_auth_grant`、字段策略、数据策略和资源动作注册。

1. **不删除数据库迁移** — 所有迁移是增量的，必须保留 V60/V64/V65 的单源收敛结果
2. **修正 Grant** — 在企业授权中删除或新增角色授权 Grant
3. **修正资源动作** — 资源/动作注册错误时通过同步接口或迁移补齐，保持 fail-closed
4. **状态管理** — 授权、数据策略、字段策略变更会 bump 对应版本，无需重启服务

## 调试信号

| 决策理由码 | 含义 |
|-----------|------|
| `AUTHZ_RESOURCE_ACTION_NOT_REGISTERED` | 资源未同步或 action 未注册 |
| `AUTHZ_DENY_GRANT_MATCHED` | 显式 Deny 生效 |
| `AUTHZ_ALLOW_GRANT_MATCHED` | 显式 Allow 生效 |
| `AUTHZ_ADMIN_REGISTERED_RESOURCE` | 管理员绕过 Grant 检查 |
| `AUTHZ_GRANT_NOT_FOUND` | 无匹配授权且非管理员 |
| `AUTHZ_GUARD_DENIED` | 领域守卫拒绝该操作 |
| `AUTHZ_FIELD_DENY_POLICY` | 字段级别拒绝策略 |
| `AUTHZ_FUNCTION_DENIED` | 功能权限被拒绝导致字段不可见 |

## 规则一览

1. **资源码格式**：`{资源类型}:{业务标识}`（如: `LOWCODE_FORM:EXPENSE`）
2. **动作码**：使用产品规范语言（VIEW/CREATE/EDIT/DELETE/SUBMIT/APPROVE/REJECT/EXPORT）
3. **授权原子性**：Deny 高于 Allow，无需额外配置
4. **守卫分离**：中心决策只返回"需要哪些守卫"，守卫由业务服务本地评估
5. **字段策略**：基于角色/用户的字段读写模式，敏感字段自动脱敏
6. **日志审计**：所有决策记录写入 `sys_auth_decision_log`，包含版本号用于一致性校验
