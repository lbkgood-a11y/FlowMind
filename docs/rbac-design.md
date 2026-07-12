# TrioBase RBAC 权限体系设计

> 版本: v1.2
> 适用于: service-auth (Spring Boot) + platform-gateway + common-security + 各业务微服务 + trio-base-frontend (Vue Vben Admin)
> 最后更新: 2026-07-12

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
12. [微服务统一权限体系](#12-微服务统一权限体系)
13. [组织与数据权限边界](#13-组织与数据权限边界)

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
| `BP` | 历史前端按钮权限 | `BP001` = `System:Menu:Create`（存量兼容，新增后端受保护操作不再使用此格式） |
| `M` | 菜单路由 | `M004` = 用户管理页面 |
| `01HK...` | 按钮菜单 | `01HK153X100000000000000010` = 菜单管理-新增按钮 |

运行时通过 API 创建的行使用 ULID（26 位 Crockford's base32）。

### 2.3 当前数据量（V16 之后）

| 表 | 行数 | 说明 |
|----|------|------|
| sys_user | 1 | admin |
| sys_role | 3 | ADMIN, TENANT_ADMIN, USER |
| sys_permission | 动态演进 | API 权限为主；用户管理、角色管理、菜单管理已对齐为 API 权限码 |
| sys_menu | 动态演进 | 目录/菜单/按钮混合；button 节点作为页面内操作的授权载体 |
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
1. `menu.permission_code` 如果非空（菜单/按钮节点可直接固化 API 权限码，如 `/api/v1/users:POST`）
2. 否则用 `permission.resource || ':' || permission.action`（API 权限，如 `/api/v1/users:GET`）
3. 菜单无 permission_id 则不产生权限码（如仪表盘）

### 4.2 角色授权（管理操作）

```
管理员在 UI 勾选菜单树 → 前端传 menuIds
  → RoleService.create/update()
    → normalizeMenuIds(): 校验菜单存在，并自动补齐已选节点的父级菜单
    → replaceMenus(): 写入 sys_role_menu (delete + insert)
    → 用户权限码由 sys_role_menu → sys_menu → sys_permission 实时推导
```

**注意:** V12 之后只保留 `sys_role_menu`。角色不再直接保存 `permissionIds`，无菜单关联的纯 API 权限需要先设计载体后再引入。

---

## 5. 权限码体系

### 5.1 权限码格式

| 类型 | 格式 | 示例 | 来源 |
|------|------|------|------|
| **API 权限（标准）** | `/api/v1/{resource}:{HTTP_METHOD}` | `/api/v1/users:GET`, `/api/v1/users/*:PUT` | sys_permission.resource + action，必须与 `@RequirePermission` 一致 |
| **前端 UI 权限（例外）** | `{Module}:{Entity}:{Action}` | `System:Menu:Create` | 仅用于没有后端受保护接口的纯前端交互或存量兼容 |

**设计约束:**

- 后端 Controller 已用 `@RequirePermission` 保护的能力，前端必须复用同一个 API 权限码。
- 不为同一个后端能力再创建一套 `System:*` 前端权限码，否则会出现“前端按钮显示/隐藏”和“后端 403”两套规则不一致。
- 历史 `System:*` 按钮权限作为存量兼容逐步清理；用户管理、角色管理、菜单管理已经通过 V15/V16 统一为 API 权限码。

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
| `/api/v1/menus/*:PUT` | 01HK153X0M000000000000000M | 菜单管理-修改按钮 |
| `/api/v1/org/units:GET` | P019 | 组织管理 (M009) |
| `/api/v1/org/units:POST` | P020 | 组织管理-新增按钮 |
| `/api/v1/org/units/*:PUT` | P021 | 组织管理-修改按钮 |
| `/api/v1/org/units/*:DELETE` | P022 | 组织管理-删除按钮 |
| `/api/v1/data-policies:GET` | P023 | 数据权限 (M010) |
| `/api/v1/data-policies:POST` | P024 | 数据权限-新增按钮 |
| `/api/v1/data-policies/*:PUT` | P025 | 数据权限-修改按钮 |
| `/api/v1/data-policies/*:DELETE` | P026 | 数据权限-删除按钮 |

### 5.3 页面读权限与按钮写权限边界

页面菜单节点承担“进入页面 + 读取页面基础数据”的权限。页面内按钮节点只承载独立的后端动作权限，尤其是写操作。

| 交互类型 | 权限归属 | 是否创建 button 节点 | 原因 |
|----------|----------|----------------------|------|
| 页面访问、列表初始化 | 页面菜单 GET 权限 | 否 | 进入页面必须具备读取权限 |
| 搜索、刷新、分页、回车筛选 | 页面菜单 GET 权限 | 否 | 都是同一个列表 GET 能力的不同交互入口 |
| 详情查看 | 页面菜单 GET 权限 | 否 | 与列表读取同属只读能力 |
| 新增、编辑、删除、启停、分配角色 | button 节点 API 权限 | 是 | 每个动作对应独立后端写接口 |
| 纯前端 UI 开关（无后端接口） | 前端 UI 权限 | 按需 | 仅控制 UI，不作为后端安全边界 |

**用户管理示例:**

| UI 行为 | 后端接口 | 权限码 | 菜单节点 |
|---------|----------|--------|----------|
| 进入用户管理、列表、搜索、刷新、分页、详情 | `GET /api/v1/users`, `GET /api/v1/users/{id}` | `/api/v1/users:GET` | 用户管理页面 `M004` |
| 新增用户、保存新增弹窗 | `POST /api/v1/users` | `/api/v1/users:POST` | 用户管理-新增 button |
| 编辑用户、保存编辑弹窗、启停、分配角色 | `PUT /api/v1/users/{id}`, `PUT /api/v1/users/{id}/status`, `POST /api/v1/users/{id}/roles` | `/api/v1/users/*:PUT` | 用户管理-修改 button |
| 删除用户 | `DELETE /api/v1/users/{id}` | `/api/v1/users/*:DELETE` | 用户管理-删除 button |

因此，取消“用户管理-新增”只应移除 `/api/v1/users:POST`，不应影响 `/api/v1/users:GET`；用户仍可进入页面并使用搜索。若产品确实要求“能看列表但不能点搜索”，需要另行定义纯前端 UI 权限，但后端安全边界仍然是 `/api/v1/users:GET`。

### 5.4 历史按钮权限码

历史迁移中曾使用 `System:{Entity}:{Action}` 作为按钮码，例如 `System:User:Create`。该格式不能直接满足后端 `@RequirePermission("/api/v1/users:POST")` 校验，容易导致“前端已授权但后端 403”。新开发和用户管理功能不再使用该格式承载后端受保护操作。

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
│   ├── 新增 (button) → P002 (/api/v1/users:POST)
│   ├── 修改 (button) → P003 (/api/v1/users/*:PUT)
│   └── 删除 (button) → P004 (/api/v1/users/*:DELETE)
├── 角色管理 (M005, menu) → P005
│   ├── 新增 (button) → P006 (/api/v1/roles:POST)
│   ├── 修改 (button) → P007 (/api/v1/roles/*:PUT)
│   └── 删除 (button) → P008 (/api/v1/roles/*:DELETE)
├── 菜单管理 (M006, menu) → P013
│   ├── 新增 (button) → P014 (/api/v1/menus:POST)
│   ├── 修改 (button) → 01HK153X0M000000000000000M (/api/v1/menus/*:PUT)
│   └── 删除 (button) → P015 (/api/v1/menus/*:DELETE)
├── 组织管理 (M009, menu) → P019
│   ├── 新增 (button) → P020 (/api/v1/org/units:POST)
│   ├── 修改 (button) → P021 (/api/v1/org/units/*:PUT)
│   └── 删除 (button) → P022 (/api/v1/org/units/*:DELETE)
├── 数据权限 (M010, menu) → P023
│   ├── 新增 (button) → P024 (/api/v1/data-policies:POST)
│   ├── 修改 (button) → P025 (/api/v1/data-policies/*:PUT)
│   └── 删除 (button) → P026 (/api/v1/data-policies/*:DELETE)
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
<Button v-access:code="'/api/v1/users:POST'">新增用户</Button>
<Button v-access:code="['/api/v1/users/*:PUT', '/api/v1/users/*:DELETE']">批量操作</Button>
```

权限码列表在 `accessCodes` 中，不匹配则移除 DOM 元素。

### 8.3 菜单授权 UI

角色管理页面 (`views/system/role/list.vue`) 使用 Ant Design Vue Tree 组件：

- **编辑/创建角色:** 左侧表单 + 右侧菜单 Tree（多选勾选）
- **授权模式:** 点击"授权"按钮，只显示菜单 Tree，隐藏其他表单字段
- **勾选模式:** Tree 必须使用 `checkStrictly=true`，页面节点和按钮节点独立授权
- **数据流:** 勾选的 `menuIds` → `POST/PUT /api/v1/roles` → 后端写入 `sys_role_menu`

父子节点独立的原因：页面 GET 权限和按钮写权限不是同一件事。取消“新增”按钮时，不应级联取消父级“用户管理”页面节点，否则用户会同时丢失 `/api/v1/users:GET`，搜索、刷新和列表读取都会消失。

后端 `RoleService.normalizeMenuIds()` 会自动补齐被选中节点的父级菜单，用于保证路由祖先完整；这只是兜底，不代表前端可以用级联勾选替代明确授权。

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
3. 种子数据 ID 使用有意义的短前缀（P/M/U/R），按钮菜单可使用稳定 ULID，避免无注释的随机 ID
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
| V14 | 补充管理类接口的隐藏 API 权限按钮节点 |
| V15 | 对齐用户管理按钮权限为 API 权限码，清理重复/废弃用户管理按钮权限 |
| V16 | 对齐角色管理、菜单管理按钮权限为 API 权限码，清理重复/废弃按钮权限 |
| V17 | 新增组织管理、数据权限菜单/API 权限和数据权限策略表 |

---

## 10. 开发指南

### 10.1 添加新页面 + 权限

**假设要添加"操作日志"页面，路径 `/system/audit-log`：**

**Step 1 — 迁移文件（示例：新建 `V17__add_audit_log_menu.sql`）：**

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

先判断该按钮是否对应独立后端能力：

- 如果只是搜索、刷新、分页、展开/收起、列设置等页面内读交互，不新增按钮权限，复用页面 GET 权限。
- 如果会调用独立后端接口（导出、创建、编辑、删除、启停、审批等），创建 button 菜单节点，并绑定该接口的 API 权限码。

**Step 1 — 迁移文件：**

```sql
INSERT INTO sys_permission(id, resource, action, description) VALUES
    ('P020', '/api/v1/audit-logs/export', 'POST', '导出操作日志')
ON CONFLICT DO NOTHING;

INSERT INTO sys_menu (id, parent_id, menu_key, menu_name, menu_type, permission_code, ...) VALUES
    ('01HK...', 'M010', 'SystemAuditLogExport', '导出', 'button',
     '/api/v1/audit-logs/export:POST', ...)
ON CONFLICT (id) DO UPDATE SET ...;
```

**Step 2 — 前端使用：**

```html
<Button v-access:code="'/api/v1/audit-logs/export:POST'" @click="handleExport">
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
| `sys_menu.permission_code` 冗余 | 中 | 可通过 `JOIN sys_permission` 计算。UserMapper 的 COALESCE 逻辑可简化 |
| 历史 `System:*` 按钮权限 | 低 | 用户管理、角色管理、菜单管理已对齐；后续新增功能继续按“API 权限码优先”策略执行 |
| FormController 无 @RequirePermission | 低 | 暂无对应 sys_permission 条目 |

### 11.2 设计决策待确认

| 议题 | 状态 | 说明 |
|------|------|------|
| `sys_role_permission` vs `sys_role_menu` | 已决策 | 只保留 `sys_role_menu`。权限码从 menu→permission 推导。无菜单的纯 API 权限方案待定 |
| 权限码格式统一 | 已决策 | 后端受保护能力统一使用 `resource:action`，注解、前端指令、JWT 中一致 |
| 搜索/刷新/分页是否是按钮权限 | 已决策 | 否。它们复用页面 GET 权限，不额外创建 button 节点 |
| 按钮权限是否需要 `sys_permission` 行 | 已决策 | 需要。button 节点承载后端动作时应关联对应 API 权限，`permission_code` 必须与 `@RequirePermission` 一致 |
| 角色授权树是否级联勾选 | 已决策 | 否。前端 Tree 使用 `checkStrictly=true`，页面读权限与按钮写权限独立授权 |

---

## 12. 微服务统一权限体系

TrioBase 微服务架构下，所有服务必须走同一套权限体系。权限主数据集中在 `service-auth`，权限执行分布在 `platform-gateway` 和各业务微服务。

### 12.1 集中管理，分布执行

```text
service-auth
  用户 / 角色 / 菜单 / API 权限 / 数据策略
        │
        │ 权限快照 / 版本号 / 变更事件
        ▼
platform-gateway
  token 校验 + 入口 API 粗粒度拦截 + AuthContext 注入
        │
        ▼
业务微服务
  common-security 本地校验功能权限 + 生成数据权限过滤条件
```

约束：

1. `service-auth` 是权限主数据唯一来源。
2. `platform-gateway` 负责入口身份校验和粗粒度 API 拦截。
3. 所有 Java 微服务必须依赖统一的 `common-security` 权限组件。
4. 业务服务不能自定义另一套角色、菜单、按钮或 API 权限模型。
5. 网关不是唯一安全边界。内部服务调用、Temporal Activity、AI Agent、定时任务都必须在服务侧经过同一套权限执行逻辑。

### 12.2 权限执行规范

所有 Controller 默认需要鉴权：

```java
@RequirePermission("/api/v1/users:GET")
```

公开端点必须显式声明：

```java
@PublicEndpoint
```

`@PublicEndpoint` 已在 `common-core` 中提供，作为后续 CI 扫描 Controller 授权覆盖率的显式豁免标记。

数据查询接口需要声明资源与动作：

```java
@RequireDataScope(resource = "USER", action = "QUERY")
```

当前公共层已提供：

| 组件 | 职责 |
|------|------|
| `@RequireDataScope` | 标记查询接口需要执行数据范围解析 |
| `DataScopeProvider` | 由各服务实现，负责解析当前用户在资源/动作下的有效数据策略 |
| `DataScopeAspect` | 在 Controller 方法进入前解析 `DataScope`，写入请求级上下文 |
| `DataScopeContextHolder` | 业务 Service 从这里读取当前查询的数据范围 |

落地规则：

1. Controller 同时声明 `@RequirePermission` 与 `@RequireDataScope`。
2. `@RequireDataScope` 不直接拼业务 SQL，只负责生成请求级 `DataScope`。
3. 业务 Service 必须把 `DataScope` 显式应用到查询条件。
4. 没有策略时默认为 restrictive，查询结果应收窄为空。
5. 运行时解析接口使用独立权限码 `/api/v1/data-policies/effective:GET`，不复用数据权限管理页面的 `/api/v1/data-policies:GET`。

第一条已落地的业务查询是用户列表：

```java
@GetMapping
@RequirePermission("/api/v1/users:GET")
@RequireDataScope(resource = "USER", action = "QUERY")
public R<PageResult<UserInfoPayload>> list(...) { ... }
```

内置基础策略：

| 角色 | 资源 | 动作 | 范围 |
|------|------|------|------|
| `ADMIN` | `USER` | `QUERY` | `ALL` |
| `USER` | `USER` | `QUERY` | `SELF` |

组织型范围解析规则：

| 范围 | 运行时解析 |
|------|------------|
| `OWN_ORG` | 当前用户在该维度下的主组织 ID |
| `OWN_ORG_AND_CHILDREN` | 主组织 ID + `sys_org_relation.tree_path` 展开的全部下级组织 ID |
| `ASSIGNED_ORGS` | 策略配置中保存的组织 ID 集合 |
| `ALL` / `SELF` | 不做组织 ID 展开，由业务查询按全量/本人语义处理 |

CI 应扫描 Controller：

| 检查项 | 规则 |
|--------|------|
| Controller 方法无权限注解 | 失败，除非标记 `@PublicEndpoint` |
| `@RequirePermission` 未注册到 `sys_permission` | 失败 |
| 前端按钮权限码与后端注解不一致 | 失败 |
| 业务查询接口缺少数据范围声明 | 失败 |

### 12.3 权限快照与缓存

禁止每个请求远程调用 `service-auth` 或查询权限数据库。推荐使用权限快照。

Token 只保存轻量上下文：

```text
userId
tenantId
tokenId
authVersion
roleVersion
dataPolicyVersion
```

权限快照保存在 Redis，并在服务本地使用 Caffeine 做 L1 缓存：

```text
auth:snapshot:{tenantId}:{userId}:{authVersion}
  ├─ apiPermissions
  ├─ menuPermissions
  ├─ buttonPermissions
  ├─ dataPolicies
  └─ orgContext
```

权限变更后：

```text
管理员修改角色/菜单/权限/组织/数据策略
  ↓
service-auth 更新版本号
  ↓
发布 AuthzChangedEvent
  ↓
网关和微服务清理本地缓存
  ↓
下一次请求按新版本加载权限快照
```

---

## 13. 组织与数据权限边界

组织模型和数据权限详细设计见 [组织模型与数据权限设计](./org-data-permission-design.md)。

本 RBAC 文档固化以下边界：

1. **菜单、按钮、API 权限只控制功能入口**：决定用户能不能访问页面、按钮和接口。
2. **组织维度只控制数据范围**：决定用户进入功能后能看到哪些数据。
3. **搜索、刷新、分页、详情复用页面 GET 权限**：不额外创建按钮权限。
4. **新增、修改、删除、导出、审批等独立后端动作使用 button 节点绑定 API 权限码**。
5. **多组织交叉数据权限通过 Data Policy 表达**，不写死在角色、菜单或业务 SQL 中。

多维组织权限推荐规则：

| 规则 | 默认策略 |
|------|----------|
| 同一组织维度内多个组织 | OR |
| 不同组织维度之间 | AND |
| 多个角色之间 | OR |
| 显式拒绝 | DENY 优先 |

典型示例：

```text
功能权限:
  /api/v1/expenses:GET

数据策略:
  资源: EXPENSE
  动作: QUERY
  维度:
    LEGAL: 北京公司、上海公司
    ACCOUNTING: 华东成本中心
  组合: AND
```

业务服务最终生成查询条件：

```sql
WHERE legal_org_id IN (...)
  AND accounting_org_id IN (...)
```

`service-auth` 不拼接业务 SQL，不查询业务数据。它只提供策略和权限快照；业务微服务通过统一组件将策略转换成自身资源的查询条件。
