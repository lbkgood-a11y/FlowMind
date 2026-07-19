# 授权模型迁移指南

本文档面向需要在 TrioBase 企业授权模型上线后保持兼容的开发者。新授权模型以"角色 → 资源 → 动作"三元组为核心，取代旧式的菜单派生权限模式。

## 兼容性承诺

| 旧模式 | 新模式 | 兼容性 |
|--------|--------|--------|
| `@RequirePermission` | `AuthorizationDecisionService.decide()` | ✅ 旧注解继续有效 |
| `@RequireDataScope` | `DataPolicyService.resolveEffective()` | ✅ 旧注解继续有效 |
| `sys_menu.permission_code` | 资源授权 Grant | ✅ 无显式 Deny 时降级到旧菜单权限码 |
| 低代码 `FORM_KEY:ACTION` | `LOWCODE_FORM:FORM_KEY` 资源 | ✅ 迁移期间双轨运行 |

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

使用管理面板 **系统管理 → 授权管理 → 功能权限** 为角色赋予资源动作权限。

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

使用 **系统管理 → 授权管理 → 数据范围** 为角色配置可见范围。

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
| `sys_menu` 菜单权限码 | `sys_auth_resource` + `sys_auth_grant` | 资源授权是持久化授权契约 |
| `@RequirePermission("xxx")` | `AuthorizationDecisionResponse.isAllowed()` | 运行时查询，不再仅依赖静态注解 |
| `@RequireDataScope` | `AuthzDataScopeResult.scopeTypes` | 数据范围策略在 auth 服务端解析 |
| 角色 → 菜单 | 角色 → 资源 → 动作 | 细粒度到单个业务动作 |

## Deny 优先规则

授权引擎按以下顺序决策：
1. 资源+动作未注册 → **Deny**（fail-closed）
2. 显式 Deny 授权 → **Deny**（优先于任何 Allow）
3. 显式 Allow 授权 → **Allow**
4. 管理员角色（ADMIN）→ **Allow**（即使无显式授权）
5. 旧式菜单权限码降级 → **Allow**（仅当无 Deny 时）
6. 无匹配 → **Deny**

## Rollback 方案

如需回退到旧式权限模式：

1. **不删除数据库迁移** — 所有迁移是增量的，向后兼容
2. **移除 Grant** — 在管理面板删除角色的授权 Grant，旧菜单权限码降级自动生效
3. **状态管理** — 无需重启服务

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
