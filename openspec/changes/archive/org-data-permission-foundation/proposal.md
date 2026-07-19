## Why

TrioBase 需要把组织从“部门树”提升为企业级权限、流程、核算和分析的基础设施。现有 RBAC 只能回答用户能否访问菜单、按钮和 API，无法统一表达多组织维度下的数据范围，也无法保障所有微服务用同一套权限模型执行。

## What Changes

- 新增组织基础能力：支持组织单元、组织维度、维度内树关系、用户组织归属和主组织。
- 新增数据权限能力：支持角色级数据策略，按资源、动作、组织维度、范围类型和组合规则描述数据可见范围。
- 新增统一权限执行基础：固化网关与微服务共享的 AuthContext、功能权限和数据权限执行规则，避免各服务自定义鉴权模型。
- 新增前端组织管理页面：在系统管理下维护组织维度、组织树和用户组织归属。
- 新增前端数据权限页面：在系统管理下为角色配置数据权限策略。
- 更新 RBAC 菜单和 API 授权数据：为组织管理、数据权限管理补齐菜单、按钮和 API 权限码。

## Capabilities

### New Capabilities

- `organization-management`: 组织单元、组织维度、组织树关系和用户组织归属管理。
- `data-permission-policy`: 角色级数据权限策略管理，支持多组织维度交叉范围。
- `unified-authorization-runtime`: 微服务统一功能权限与数据权限运行时，包含 AuthContext、权限快照和服务侧执行约束。

### Modified Capabilities

<!-- No archived specs exist yet. RBAC behavior is documented in docs/rbac-design.md and will be implemented through the new runtime capability. -->

## Impact

| 影响范围 | 详情 |
|----------|------|
| `service-org` | 扩展组织模型、迁移脚本、Controller/Service/DTO、权限注解和测试 |
| `service-auth` | 新增数据权限策略表、管理 API、角色数据权限配置、菜单/权限种子数据 |
| `common-core` 或 `common-security` | 新增统一 AuthContext、数据范围模型和权限执行基础组件 |
| `platform-gateway` | 后续可接入权限快照与 AuthContext 头透传；本阶段先兼容现有 JWT/权限头链路 |
| `trio-base-frontend/apps/web-antd` | 新增系统管理下的组织管理、数据权限页面和授权按钮控制 |
| `docs/` | 已固化组织模型、数据权限和微服务统一权限设计，后续实现需保持一致 |
| 数据库 | 新增组织维度/关系/用户归属扩展表和数据权限策略表；补齐菜单/API 权限迁移 |
