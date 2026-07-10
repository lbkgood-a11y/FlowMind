# TrioBase RBAC 权限体系设计

> 版本: v1.0
> 适用于: service-auth (Spring Boot) + trio-base-frontend (Vue Vben Admin)
> 最后更新: 2026-07-10

---

## 目录

1. [架构概览](#1-架构概览)
2. [数据模型](#2-数据模型)
3. [认证流程](#3-认证流程)
4. [授权流程](#4-授权流程)
5. [权限码体系](#5-权限码体系)
6. [菜单层级模型](#6-菜单层级模型)
7. [后端权限拦截](#7-后端权限拦截)
8. [前端权限集成](#8-前端权限集成)
9. [Flyway 迁移规范](#9-flyway-迁移规范)
10. [开发指南](#10-开发指南)
11. [已知问题与技术债](#11-已知问题与技术债)

---

## 1. 架构概览

### 1.1 请求链路

```
浏览器 → Gateway (8080) → service-auth (8081)
              │
              ├─ JwtAuthFilter: 验证 JWT → 注入 X-User-Id / X-Username / X-User-Permissions 头
              ├─ DataMaskingFilter: AI 路径敏感数据扫描
              └─ TraceIdFilter: 注入 X-B3-TraceId

下游服务:
  AuditSecurityFilter → SecurityContextHolder (ThreadLocal)
    → Controller (@RequirePermission)
      → PermissionAspect: permissions.contains(required) ? proceed() : 403
```

### 1.2 两层鉴权

| 层 | 位置 | 机制 | 失败返回 |
|----|------|------|----------|
| 网关层 | platform-gateway | JwtAuthFilter 验证 token 有效性 | 401 |
| 服务层 | service-auth | @RequirePermission + PermissionAspect | 403 (code 1009) |

网关负责"你是谁"，服务层负责"你能做什么"。

---

## 2. 数据模型

### 2.1 核心表（7 张）

```
sys_user ──┐
           ├── sys_user_role ── sys_role ── sys_role_menu ── sys_menu
           │                                                  │
           │                                          permission_id (FK)
           │                                                  │
           │                                                  ▼
           │                                          sys_permission
           │
           └── (用户权限码由 role → menu → permission 推导)
```

| 表 | 职责 | 关键字段 |
|----|------|----------|
| **sys_user** | 用户账户 | `username`, `password` (BCrypt), `email`, `phone`, `status` |
| **sys_role** | 角色定义 | `role_code`, `role_name`, `status` |
| **sys_permission** | 权限码注册表 | `resource`, `action` → 唯一约束，组合为 `resource:action` 权限码 |
| **sys_menu** | 菜单树（UI 载体） | `parent_id` (自引用), `menu_type` (catalog/menu/button/link/embedded), `permission_id` (FK→sys_permission), `permission_code` (冗余，待删除) |
| **sys_user_role** | 用户←→角色 | `user_id`, `role_id` (唯一约束) |
| **sys_role_menu** | 角色←→菜单 | `role_id`, `menu_id` (唯一约束) |
| ~~**sys_role_permission**~~ | 已删除 (V12) | 原角色←→权限直接关联，被 sys_role_menu 替代 |

### 2.2 种子数据 ID 约定

| 前缀 | 用途 | 示例 |
|------|------|------|
| `U` | 用户 | `U001` = admin |
| `R` | 角色 | `R001` = ADMIN, `R002` = TENANT_ADMIN, `R003` = USER |
| `P` | API 权限 | `P001` = `/api/v1/users:GET` |
| `BP` | 按钮权限 | `BP001` = `System:Menu:Create` |
| `M` | 菜单路由 | `M004` = 用户管理页面 |
| `01HK...` | 按钮菜单 | `01HK153X100000000000000010` = 菜单管理-新增按钮 |

运行时通过 API 创建的行使用 ULID（26 位 Crockford's base32）。

### 2.3 当前数据量（V13 之后）

| 表 | 行数 | 说明 |
|----|------|------|
| sys_user | 1 | admin |
| sys_role | 3 | ADMIN, TENANT_ADMIN, USER |
| sys_permission | 28 | 10 API (V1) + 8 API (V2) + 9 按钮 (V9) + 1 menus:PUT (V9) |
| sys_menu | ~25 | 目录/菜单/按钮混合 |
| sys_role_menu | 全量 | ADMIN 拥有所有菜单 |

---

## 3. 认证流程

### 3.1 登录

```
POST /api/v1/auth/login { username, password }
  → AuthService.login()
    → BCrypt 验证密码
    → 查询用户角色 (sys_user_role → sys_role)
    → 生成 accessToken (HS256, 默认 300s) + refreshToken (默认 1800s)
    → refreshToken 写入 Redis (key: "refresh:{userId}:{jti}")
    → 返回 { accessToken, refreshToken, roles }
```

### 3.2 Token 刷新

```
POST /api/v1/auth/refresh { refreshToken }
  → AuthService.refresh()
    → 验证 refreshToken 签名 + 类型 + Redis 中存在
    → 删除旧 Redis key (防重放)
    → 生成新 token 对
```

### 3.3 Token 验证（网关调用）

```
GET /api/v1/auth/validate (Authorization: Bearer <token>)
  → AuthService.validate()
    → 验证签名 + 过期 + 未被吊销
    → 查询用户权限码: UserMapper.selectPermissionsByUserId()
    → 返回 { valid: true, userId, username, permissions: [...] }
```

### 3.4 登出

```
POST /api/v1/auth/logout { accessToken, refreshToken }
  → accessToken jti 写入 Redis 黑名单 (TTL = 剩余有效期)
  → refreshToken 从 Redis 删除
```

---

## 4. 授权流程

### 4.1 权限码查询

**UserMapper.selectPermissionsByUserId()** 是权限查询的唯一入口：

```sql
SELECT DISTINCT code FROM (
    SELECT COALESCE(NULLIF(m.permission_code, ''), p.resource || ':' || p.action) AS code
    FROM sys_role_menu rm
    JOIN sys_menu m ON m.id = rm.menu_id
    LEFT JOIN sys_permission p ON p.id = m.permission_id
    JOIN sys_user_role ur ON rm.role_id = ur.role_id
    JOIN sys_role r ON r.id = ur.role_id
    WHERE ur.user_id = #{userId} AND r.status = 1
) permissions WHERE code IS NOT NULL AND code <> ''
```

**查询链路:** user → user_role → role → role_menu → menu → permission → resource:action

**权限码优先级:**
1. `menu.permission_code` 如果非空（按钮权限，如 `System:Menu:Create`）
2. 否则用 `permission.resource || ':' || permission.action`（API 权限，如 `/api/v1/users:GET`）
3. 菜单无 permission_id 则不产生权限码（如仪表盘）

### 4.2 角色授权（管理操作）

```
管理员在 UI 勾选菜单树 → 前端传 menuIds
  → RoleService.create/update()
    → replaceMenus(): 写入 sys_role_menu (delete + insert)
    → syncRolePermissionsFromMenus(): 从菜单推导 permissionIds
      → 写入 sys_role_permission (注: V12 已删此表，此函数需要清理)
```

**注意:** `CreateRoleRequest` 和 `UpdateRoleRequest` 中定义了 `permissionIds` 字段但当前未使用。未来用于分配无菜单关联的纯 API 权限。

---

## 5. 权限码体系

### 5.1 两种权限码格式

| 类型 | 格式 | 示例 | 来源 |
|------|------|------|------|
| **API 权限** | `/api/v1/{resource}:{HTTP_METHOD}` | `/api/v1/users:GET`, `/api/v1/users/*:PUT` | sys_permission.resource + action |
| **按钮权限** | `{Module}:{Entity}:{Action}` | `System:Menu:Create`, `System:User:Delete` | sys_menu.permission_code (来自 sys_permission) |

### 5.2 API 权限码清单

| 权限码 | ID | 对应菜单 |
|--------|-----|----------|
| `/api/v1/users:GET` | P001 | 用户管理 (M004) |
| `/api/v1/users:POST` | P002 | 用户管理-新增按钮 |
| `/api/v1/users/*:PUT` | P003 | 用户管理-修改按钮 |
| `/api/v1/users/*:DELETE` | P004 | 用户管理-删除按钮 |
| `/api/v1/roles:GET` | P005 | 角色管理 (M005) |
| `/api/v1/roles:POST` | P006 | 角色管理-新增按钮 |
| `/api/v1/roles/*:PUT` | P007 | 角色管理-修改按钮 |
| `/api/v1/roles/*:DELETE` | P008 | 角色管理-删除按钮 |
| `/api/v1/ai/chat:POST` | P009 | 无 |
| `/api/v1/rag/**:GET` | P010 | 无 |
| `/api/v1/forms:GET` | P011 | 表单管理 (M002) |
| `/api/v1/forms:POST` | P012 | 新建表单 (M003) |
| `/api/v1/menus:GET` | P013 | 菜单管理 (M006) |
| `/api/v1/menus:POST` | P014 | 菜单管理-新增按钮 |
| `/api/v1/menus/*:DELETE` | P015 | 菜单管理-删除按钮 |
| `/api/v1/permissions:GET` | P016 | 授权管理 (M007) |
| `/api/v1/permissions:POST` | P017 | - |
| `/api/v1/permissions/*:DELETE` | P018 | - |
| `/api/v1/menus/*:PUT` | - | 菜单管理-修改按钮 |

### 5.3 按钮权限码清单

| 权限码 | ID | 菜单 | 描述 |
|--------|-----|------|------|
| `System:Menu:Create` | BP001 | 菜单管理-新增 | 菜单管理页新增按钮 |
| `System:Menu:Edit` | BP002 | 菜单管理-修改 | 菜单管理页修改按钮 |
| `System:Menu:Delete` | BP003 | 菜单管理-删除 | 菜单管理页删除按钮 |
| `System:User:Create` | BP004 | 用户管理-新增 | 用户管理页新增按钮 |
| `System:User:Edit` | BP005 | 用户管理-修改 | 用户管理页修改按钮 |
| `System:User:Delete` | BP006 | 用户管理-删除 | 用户管理页删除按钮 |
| `System:Role:Create` | BP007 | 角色管理-新增 | 角色管理页新增按钮 |
| `System:Role:Edit` | BP008 | 角色管理-修改 | 角色管理页修改按钮 |
| `System:Role:Delete` | BP009 | 角色管理-删除 | 角色管理页删除按钮 |

---

## 6. 菜单层级模型

### 6.1 五种菜单类型

| 类型 | menu_type | path | component | 权限 | 典型场景 |
|------|-----------|------|-----------|------|----------|
| **catalog** | `catalog` | 必填 | null | 无 | 分组折叠节点，如"系统管理" |
| **menu** | `menu` | 必填 | 必填 | 可选 | 侧栏可点击页面，如"用户管理" |
| **button** | `button` | null | null | 必填 (permission_code) | 页面内操作按钮，`hide_in_menu=1` |
| **link** | `link` | null | 外部 URL | 可选 | 外链跳转 |
| **embedded** | `embedded` | 必填 | 外部 URL | 可选 | iframe 内嵌页面 |

### 6.2 当前菜单树

```
仪表盘 (M001, catalog)
├── 分析页 (M002, menu)
└── 工作台 (M003, menu)

系统管理 (M008, catalog)
├── 用户管理 (M004, menu) → P001
│   ├── 新增 (button) → BP004
│   ├── 修改 (button) → BP005
│   └── 删除 (button) → BP006
├── 角色管理 (M005, menu) → P005
│   ├── 新增 (button) → BP007
│   ├── 修改 (button) → BP008
│   └── 删除 (button) → BP009
├── 菜单管理 (M006, menu) → P013
│   ├── 新增 (button) → BP001
│   ├── 修改 (button) → BP002
│   └── 删除 (button) → BP003
├── 部门管理 (M009, menu) → 无权限
└── 授权管理 (M007, menu) → P016 (隐藏)

表单 (独立)
├── 表单管理 (M002, menu) → P011
└── 新建表单 (M003, menu) → P012
```

### 6.3 关键字段说明

| 字段 | 用途 |
|------|------|
| `hide_in_menu` | 为 1 时不在侧栏渲染（button 类型强制为 1） |
| `hide_in_tab` | 为 1 时不在标签页显示 |
| `keep_alive` | 为 1 时页面切换保持缓存 |
| `affix_tab` | 为 1 时标签页固定不可关闭 |
| `status` | 0=禁用（前端不渲染该路由）, 1=启用 |
| `visible` | 0=隐藏, 1=可见（与 status 联动） |

---

## 7. 后端权限拦截

### 7.1 @RequirePermission 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    String value();  // 权限码，如 "/api/v1/users:GET"
}
```

### 7.2 PermissionAspect 切面

```java
@Around("@annotation(requirePermission)")
public Object checkPermission(ProceedingJoinPoint joinPoint,
                              RequirePermission requirePermission) throws Throwable {
    String required = requirePermission.value();
    List<String> permissions = SecurityContextHolder.getPermissions();
    if (permissions == null || !permissions.contains(required)) {
        throw new BizException(AuthErrorCode.PERMISSION_DENIED);  // code=1009
    }
    return joinPoint.proceed();
}
```

### 7.3 当前注解覆盖

| Controller | 方法数 | @RequirePermission 覆盖 |
|------------|--------|-------------------------|
| UserController | 7 | 全部 (7) |
| RoleController | 8 | 全部 (8) |
| MenuController | 8 | 全部 (8) |
| PermissionController | 4 | 全部 (4) |
| AuthController | 7 | 无（公开端点） |
| MenuRuntimeController | 1 | 无（使用 SecurityContextHolder 过滤） |
| OrgUnitController | 5 | 无（跨服务，暂无权限条目） |
| FormController | 6 | 无（跨服务，暂无权限条目） |

### 7.4 权限码映射规则

Controller 方法上的 `@RequirePermission` 值需与 `sys_permission` 中的 `resource:action` 一致：

| HTTP 方法 | URL 模式 | 权限码 |
|-----------|----------|--------|
| GET (列表/详情) | `/api/v1/users`, `/api/v1/users/{id}` | `/api/v1/users:GET` |
| POST | `/api/v1/users` | `/api/v1/users:POST` |
| PUT | `/api/v1/users/{id}`, `/api/v1/users/{id}/status` | `/api/v1/users/*:PUT` |
| DELETE | `/api/v1/users/{id}` | `/api/v1/users/*:DELETE` |

---

## 8. 前端权限集成

### 8.1 权限码获取

登录后前端调用 `GET /auth/codes` 获取用户权限码列表，存入 `useAccessStore.accessCodes`。

```typescript
// api/core/auth.ts
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/auth/codes');
}
```

### 8.2 v-access 指令

```html
<!-- 按权限码控制显示 -->
<Button v-access:code="'System:User:Create'">新增用户</Button>
<Button v-access:code="['System:User:Edit', 'System:User:Delete']">批量操作</Button>
```

权限码列表在 `accessCodes` 中，不匹配则移除 DOM 元素。

### 8.3 菜单授权 UI

角色管理页面 (`views/system/role/list.vue`) 使用 Ant Design Vue Tree 组件：

- **编辑/创建角色:** 左侧表单 + 右侧菜单 Tree（多选勾选）
- **授权模式:** 点击"授权"按钮，只显示菜单 Tree，隐藏其他表单字段
- **数据流:** 勾选的 `menuIds` → `POST/PUT /api/v1/roles` → 后端写入 `sys_role_menu`

```typescript
const payload: SystemRoleApi.SaveRoleParams = {
  menuIds: selectedMenuIds(),
  roleName: formModel.roleName,
  status: formModel.status,
};
```

### 8.4 菜单路由动态生成

```
用户登录 → fetch accessCodes + userInfo
  → generateAccess() → 根据 accessCodes 过滤路由
    → 前端静态路由 (modules/) + 后端动态菜单 (GET /menu/all)
      → 注入 Vue Router → 侧栏渲染
```

后端 `MenuRuntimeController.listAllRoutes()` 根据当前用户权限过滤菜单树（只返回有权限的菜单），前端将菜单转换为 Vue Router 路由记录。

---

## 9. Flyway 迁移规范

### 9.1 文件命名

```
V{序号}__{描述性名称_小写下划线}.sql

正确:
  V1__auth_schema.sql
  V9__fix_permissions_and_seed_button_permissions.sql
  V11__role_menu_authorization.sql

错误:
  V1__Auth_Schema.sql         (大写)
  V9-fix-permissions.sql      (连字符)
  V1__init.sql                 (描述不够具体)
```

### 9.2 幂等性模式

所有 DDL 使用 `IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`：

```sql
CREATE TABLE IF NOT EXISTS sys_menu (...);
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
```

所有种子数据使用冲突处理：

```sql
-- 简单忽略
INSERT INTO sys_permission(id, resource, action, description) VALUES (...)
ON CONFLICT DO NOTHING;

-- 更新特定列
INSERT INTO sys_menu (...) VALUES (...)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    menu_name = EXCLUDED.menu_name,
    ...;

-- 唯一约束冲突忽略
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by) VALUES (...)
ON CONFLICT (role_id, menu_id) DO NOTHING;
```

### 9.3 开发规则

1. **绝不修改已提交的迁移文件** — 始终新建迁移
2. 迁移文件名中的序号必须连续递增
3. 种子数据 ID 使用有意义的短前缀（P/BPM/U/R），避免裸 ULID
4. 每个迁移写注释说明目的
5. `application.yml` 中 `validate-on-migrate: false`（校验和不验证）

### 9.4 迁移历史

| 文件 | 功能 |
|------|------|
| V1 | RBAC 五表 + 种子角色/权限/用户 |
| V2 | sys_menu 表 + 种子菜单 + 更多权限 |
| V3 | sys_user 添加 phone 列 |
| V4 | sys_menu 扩展（15 列：component/icon/menu_type 等） |
| V5 | 替换种子菜单数据（Vben 格式路由） |
| V6 | sys_role 添加 status 列 |
| V7 | 所有表添加审计字段 (created_by/updated_by)，关联表改单列 PK |
| V8 | 种子按钮菜单（菜单管理-新增/修改/删除） |
| V9 | 补充 menus:PUT 权限 + 9 个按钮权限 + 6 个按钮菜单 + ADMIN 全权限 |
| V10 | 删除孤儿菜单 |
| V11 | 创建 sys_role_menu 表，从 sys_role_permission 迁移数据 |
| V12 | 删除 sys_role_permission 表 |
| V13 | 扩大 icon 列 + Vben Demo 路由 + ADMIN 全菜单授权 |

---

## 10. 开发指南

### 10.1 添加新页面 + 权限

**假设要添加"操作日志"页面，路径 `/system/audit-log`：**

**Step 1 — 迁移文件（新建 `V14__add_audit_log_menu.sql`）：**

```sql
-- 1. 注册 API 权限
INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P019', '/api/v1/audit-logs', 'GET', '查看操作日志')
ON CONFLICT DO NOTHING;

-- 2. 添加菜单项
INSERT INTO sys_menu (id, parent_id, menu_key, menu_name, path, component, ...) VALUES
    ('M010', 'M008', 'audit-log', '操作日志', '/system/audit-log',
     '/system/audit-log/list', 'mdi:file-document', ...)
ON CONFLICT (id) DO UPDATE SET ...;

-- 3. ADMIN 自动获得新菜单
INSERT INTO sys_role_menu(id, role_id, menu_id, created_by, updated_by)
SELECT '01' || upper(substr(md5('R001' || ':' || 'M010'), 1, 24)),
       'R001', 'M010', 'SYSTEM', 'SYSTEM'
ON CONFLICT (role_id, menu_id) DO NOTHING;
```

**Step 2 — 后端 Controller：**

```java
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @RequirePermission("/api/v1/audit-logs:GET")
    public R<PageResult<AuditLog>> list(...) { ... }
}
```

**Step 3 — 前端页面：**

在 `apps/web-antd/src/views/system/audit-log/list.vue` 新建页面（参照 `role/list.vue` 的模式）。

**Step 4 — 前端路由：**

在 `apps/web-antd/src/router/routes/modules/system.ts` 添加路由定义。

### 10.2 添加新按钮权限

**为已有页面添加"导出"按钮：**

**Step 1 — 迁移文件：**

```sql
INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('BP010', 'System:AuditLog', 'Export', '操作日志-导出')
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (id, parent_id, menu_key, menu_name, menu_type, permission_code, ...) VALUES
    ('01HK...', 'M010', 'SystemAuditLogExport', '导出', 'button',
     'System:AuditLog:Export', ...)
ON CONFLICT (id) DO UPDATE SET ...;
```

**Step 2 — 前端使用：**

```html
<Button v-access:code="'System:AuditLog:Export'" @click="handleExport">
  导出
</Button>
```

### 10.3 Controller 权限注解规则

```
GET    /api/v1/{resource}       → /api/v1/{resource}:GET
GET    /api/v1/{resource}/{id}  → /api/v1/{resource}:GET
POST   /api/v1/{resource}       → /api/v1/{resource}:POST
PUT    /api/v1/{resource}/{id}  → /api/v1/{resource}/*:PUT
DELETE /api/v1/{resource}/{id}  → /api/v1/{resource}/*:DELETE
```

子资源操作（如 `/api/v1/users/{id}/roles`）使用父资源的权限：

```java
@PostMapping("/{id}/roles")
@RequirePermission("/api/v1/users/*:PUT")  // 分配角色 = 编辑用户
public R<Void> assignRoles(...) { ... }
```

---

## 11. 已知问题与技术债

### 11.1 待清理

| 问题 | 严重度 | 说明 |
|------|--------|------|
| `RoleService.syncRolePermissionsFromMenus()` | **运行时错误** | V12 已删除 `sys_role_permission` 表，但此方法仍在写入。重启后创建/更新角色会报错 |
| `PermissionService.delete()` 引用 `RolePermissionMapper` | **运行时错误** | 同上，删除权限时会尝试删 `sys_role_permission` 行 |
| `sys_menu.permission_code` 冗余 | 中 | 可通过 `JOIN sys_permission` 计算。UserMapper 的 COALESCE 逻辑可简化 |
| `SysRolePermission.java` 实体未删除 | 低 | 表已删除但 Java 文件仍在 |
| `RolePermissionMapper.java` 未删除 | 低 | 同上 |
| `CreateRoleRequest.permissionIds` 字段未使用 | 低 | 定义了但 Service 忽略它 |
| OrgUnitController / FormController 无 @RequirePermission | 低 | 暂无对应 sys_permission 条目 |

### 11.2 设计决策待确认

| 议题 | 状态 | 说明 |
|------|------|------|
| `sys_role_permission` vs `sys_role_menu` | 已决策 | 只保留 `sys_role_menu`。权限码从 menu→permission 推导。无菜单的纯 API 权限方案待定 |
| 权限码格式统一 | 已决策 | 全部使用 `resource:action`，注解、前端指令、JWT 中一致 |
| 按钮权限是否需要 `sys_permission` 行 | 已决策 | 是。UserMapper 的 JOIN 要求 `sys_permission` 行存在才能推导出权限码 |
