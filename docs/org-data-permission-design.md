# TrioBase 组织模型与数据权限设计

> 版本: v0.1
> 适用于: service-org + service-auth + common-security + 各业务微服务
> 最后更新: 2026-07-12

---

## 1. 设计目标

组织能力不只用于通讯录或部门树。它是后续数据权限、流程审批、报表核算、法人主体隔离和业务视角分析的基础设施。

本设计遵循以下原则：

1. **组织对象与组织树分离**：组织单元表示“是谁”，组织维度表示“从哪个视角看”。
2. **功能权限与数据权限分离**：RBAC 决定能否进入功能和调用接口，数据权限决定能看到哪些数据。
3. **权限主数据集中，权限执行分布式**：权限规则统一由 `service-auth` 管理，各微服务通过 `common-security` 本地执行。
4. **不在业务 SQL 中硬编码权限规则**：业务服务只消费统一的数据权限策略和组织上下文。
5. **性能优先使用权限快照与缓存**：请求链路不得每次远程查询权限中心或权限数据库。

---

## 2. 核心概念

### 2.1 组织对象

组织对象表示一个真实或虚拟组织实体，例如集团、子公司、部门、成本中心、利润中心、项目组、门店。

建议模型：

```text
sys_org_unit
  id
  tenant_id
  unit_code
  unit_name
  unit_type        -- COMPANY / DEPARTMENT / TEAM / COST_CENTER / PROFIT_CENTER / PROJECT / STORE
  status
  description
  created_at
  updated_at
  created_by
  updated_by
```

### 2.2 组织维度

组织维度表示一套组织观察视角或组织关系树。

建议内置维度：

| 维度 | code | 典型用途 |
|------|------|----------|
| 行政组织 | `ADMIN` | 用户归属、通讯录、默认审批线 |
| 法人组织 | `LEGAL` | 合同主体、开票主体、法务边界 |
| 核算组织 | `ACCOUNTING` | 成本中心、利润中心、财务核算 |
| 业务组织 | `BUSINESS` | 销售区域、事业部、业务看板 |
| 项目组织 | `PROJECT` | 项目组、虚拟团队、矩阵协作 |

建议模型：

```text
sys_org_dimension
  id
  tenant_id
  dimension_code
  dimension_name
  is_default
  status
```

### 2.3 组织关系

同一个组织对象在不同组织维度下可以有不同父级，因此组织树关系需要独立建模。

```text
sys_org_relation
  id
  tenant_id
  dimension_id
  parent_unit_id
  child_unit_id
  tree_path
  level
  sort_order
```

示例：

```text
同一个“华东销售中心”

行政组织:
总部
  └─ 销售中心
       └─ 华东销售中心

核算组织:
集团核算主体
  └─ 华东利润中心
       └─ 华东销售成本中心

业务组织:
客户增长事业群
  └─ 区域销售线
       └─ 华东销售中心
```

### 2.4 用户组织归属

用户可以同时归属多个组织，但应有一个主组织。默认主组织建议使用 `ADMIN` 行政组织。

```text
sys_user_org_unit
  id
  tenant_id
  user_id
  dimension_id
  org_unit_id
  is_primary
  position_id
  position_name
  is_leader
  effective_from
  effective_to
  status
```

不建议只在用户表上放一个 `dept_id`。真实企业中，一个用户可能同时属于行政部门、项目组、业务条线和核算主体。

---

## 3. 功能权限与数据权限边界

功能权限回答“能不能操作”，数据权限回答“能操作哪些数据”。

```text
RBAC 功能权限:
  菜单 / 按钮 / API
  示例: /api/v1/users:GET

Data Policy 数据权限:
  资源 / 动作 / 组织维度 / 范围 / 组合规则
  示例: USER:QUERY 只能查看本行政组织及下级
```

搜索、刷新、分页、详情等只读交互复用页面 GET 权限，不额外建按钮权限。新增、修改、删除、导出、审批等独立后端动作使用按钮节点绑定 API 权限码。

---

## 4. 数据权限策略模型

数据权限通过策略表达，不直接把大量组织 ID 硬绑到角色逻辑里。

```text
sys_data_policy
  id
  tenant_id
  subject_type      -- ROLE / USER
  subject_id
  resource_code     -- USER / CONTRACT / ORDER / EXPENSE
  action_code       -- QUERY / CREATE / UPDATE / APPROVE / EXPORT
  effect            -- ALLOW / DENY
  combine_mode      -- AND / OR
  status

sys_data_policy_dimension
  id
  policy_id
  dimension_code    -- ADMIN / LEGAL / ACCOUNTING / BUSINESS / PROJECT
  scope_type        -- SELF / OWN_ORG / OWN_ORG_AND_CHILDREN / ASSIGNED_ORGS / ALL
  org_unit_ids      -- 指定组织范围时使用
```

推荐规则：

| 规则 | 推荐默认值 | 说明 |
|------|------------|------|
| 同一维度内多个组织 | OR | 北京公司或上海公司 |
| 不同维度之间 | AND | 法人范围和业务范围同时满足 |
| 多个角色之间 | OR | 用户拥有多个角色时取允许范围并集 |
| 显式拒绝 | DENY 优先 | 高敏场景可明确排除 |

示例：

```text
资源: EXPENSE
动作: QUERY
维度:
  LEGAL: 北京公司、上海公司
  ACCOUNTING: 华东成本中心
组合: AND
```

生成查询条件：

```sql
WHERE legal_org_id IN (...)
  AND accounting_org_id IN (...)
```

---

## 5. 资源到组织维度的映射

第一阶段不要求所有资源都支持所有组织维度。每类资源应声明一个主组织维度，并按需增加约束维度。

| 资源 | 主维度 | 可选约束维度 |
|------|--------|--------------|
| 用户 | `ADMIN` | 无 |
| 通讯录 | `ADMIN` | 无 |
| 合同 | `LEGAL` | `BUSINESS` |
| 费用单 | `ACCOUNTING` | `LEGAL` |
| 销售订单 | `BUSINESS` | `LEGAL` |
| 审批任务 | `ADMIN` | 流程定义可覆盖 |
| 报表指标 | 按指标定义 | `LEGAL` / `ACCOUNTING` / `BUSINESS` |

业务表建议直接携带必要组织维度字段：

```text
tenant_id
admin_org_id
legal_org_id
accounting_org_id
business_org_id
project_org_id
```

这些字段需要按查询频率建立组合索引，避免数据权限过滤成为慢查询来源。

---

## 6. 微服务统一权限执行

TrioBase 采用“集中管理、分布执行”的权限体系。

```text
service-auth
  维护用户、角色、菜单、API 权限、数据策略
        │
        │ 生成权限快照 / 版本号 / 变更事件
        ▼
platform-gateway
  校验 token，入口 API 粗粒度拦截，注入 AuthContext
        │
        ▼
业务微服务
  common-security 校验接口权限，生成数据权限过滤条件
```

统一约束：

1. 所有 Java 微服务必须依赖 `common-security`。
2. 所有 Controller 默认需要鉴权。
3. 公开接口必须显式标记 `@PublicEndpoint`。
4. 后端受保护接口必须使用 `@RequirePermission`。
5. 数据查询接口必须声明资源和动作，例如 `@RequireDataScope(resource = "USER", action = "QUERY")`。
6. API 权限码由后端接口声明，统一注册到 `service-auth`。
7. CI 应扫描 Controller，发现缺失权限注解或公开标记的接口时失败。

网关不是唯一安全边界。内部调用、Temporal Activity、AI Agent 和定时任务都可能绕过网关，因此业务服务必须在本地执行同一套权限规则。

---

## 7. 权限快照与缓存

请求链路不得每次远程调用 `service-auth` 查询权限。推荐使用权限快照。

Token 中只放轻量上下文：

```text
userId
tenantId
tokenId
authVersion
dataPolicyVersion
roleVersion
```

权限快照缓存：

```text
auth:snapshot:{tenantId}:{userId}:{authVersion}
  apiPermissions
  menuPermissions
  buttonPermissions
  dataPolicies
  orgContext
```

推荐缓存层级：

```text
L1: 服务本地缓存 Caffeine
L2: Redis 权限快照
DB: service-auth 权限主库
```

权限变更流程：

```text
管理员修改角色、菜单、权限、组织或数据策略
  ↓
service-auth 更新对应版本号
  ↓
发布 AuthzChangedEvent
  ↓
网关和微服务清理本地缓存
  ↓
下一次请求按新版本加载权限快照
```

---

## 8. 数据权限查询性能

组织范围展开需要按场景选择策略。

| 场景 | 推荐实现 |
|------|----------|
| 组织范围较小 | `org_id IN (...)` |
| 组织树深、节点多 | `tree_path LIKE '/xxx/%'` |
| 高频复杂查询 | 组织闭包表 `ancestor_id / descendant_id` |
| 报表分析 | 预计算授权范围或宽表字段 |
| 多维交叉过滤 | 多组织维度字段建索引，按 AND/OR 组合 |

禁止把整棵大组织树每次展开成超长 `IN` 条件。高频查询应使用闭包表、预计算范围或组织路径字段。

当前运行时执行约定：

1. `common-core` 提供 `@RequireDataScope`、`DataScopeProvider`、`DataScopeAspect` 和 `DataScopeContextHolder`。
2. `service-auth` 通过本地 `DataPolicyService` 实现 `DataScopeProvider`。
3. 运行时解析接口权限码为 `/api/v1/data-policies/effective:GET`，默认授予基础角色，用于解析当前登录用户自身策略。
4. 管理接口权限码 `/api/v1/data-policies:GET` 只用于数据权限管理页面，不作为业务运行时解析权限。
5. 没有有效数据策略时返回 restrictive，业务查询必须按空范围处理。
6. 用户列表已作为第一条样例接入 `USER:QUERY` 数据范围：管理员 `ALL`，普通用户 `SELF`。
7. `OWN_ORG` 解析为用户在对应组织维度下的主组织；没有主组织时按有效归属的第一条兜底。
8. `OWN_ORG_AND_CHILDREN` 在 `OWN_ORG` 基础上通过 `sys_org_relation.tree_path` 展开当前组织及下级组织。
9. `ASSIGNED_ORGS` 使用策略维度中显式配置的组织 ID 集合。

---

## 9. 分阶段落地

### 第一阶段：组织基础能力

1. 保留当前 `sys_org_unit`，补充 `tenant_id`、`unit_type` 等字段。
2. 新增 `sys_org_dimension`，内置 `ADMIN` / `LEGAL` / `ACCOUNTING` / `BUSINESS`。
3. 新增 `sys_org_relation`，逐步替代 `sys_org_unit.parent_id/tree_path`。
4. 用户归属默认绑定 `ADMIN` 行政组织。
5. 系统管理新增“组织管理”菜单 `M009`，页面读权限为 `/api/v1/org/units:GET`。

### 第二阶段：统一权限执行

1. 抽取 `common-security`。
2. 网关注入标准 `AuthContext`。
3. 各 Java 微服务使用统一 `@RequirePermission`。
4. CI 扫描权限注解覆盖率。

### 第三阶段：数据策略

1. 引入 `sys_data_policy` 和 `sys_data_policy_dimension`。
2. 支持资源级数据范围。
3. 为用户、合同、费用单等资源定义主组织维度。
4. common-security 输出标准数据过滤条件。
5. 系统管理新增“数据权限”菜单 `M010`，页面读权限为 `/api/v1/data-policies:GET`。

### 第四阶段：流程与分析

1. 审批流按组织维度查找负责人。
2. 报表按法人、核算、业务组织切换分析视角。
3. 数据服务使用相同的数据策略做查询过滤。

---

## 10. 固化原则

1. **组织类型不等于组织树类型**：组织对象类型描述实体性质，组织维度描述树关系视角。
2. **同一个组织对象可以出现在多棵组织树中**：不同维度下允许不同父级。
3. **RBAC 不承载数据范围**：功能授权决定功能入口，菜单可见性由功能授权推导；数据策略决定数据可见范围。
4. **多维交叉权限默认使用 AND**：财务、合同、审批等高敏数据必须收窄范围。
5. **权限中心不查询业务数据**：`service-auth` 只管理策略，不拼业务 SQL。
6. **各服务使用同一套权限执行组件**：不能每个微服务自定义鉴权模型。
7. **权限快照和版本失效保障效率**：避免每请求远程鉴权。
