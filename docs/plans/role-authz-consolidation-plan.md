# 角色管理 + 企业授权 + 数据权限 三合一整合方案

## 1. 背景与动机

### 1.1 现状问题

当前三个前端页面各自管理角色授权的不同维度，但后端模型是统一的：

```
后端统一模型:  RoleAuthorizationProfileResponse
                    ├── functionGrants
                    ├── dataPolicies
                    └── fieldPolicies

前端分散入口:
  /system/role            角色 CRUD + 内嵌授权 Tab（功能/数据/字段/守卫/预览）
  /system/authz           功能授权 + 字段规则 + 守卫 + Bundle + 决策预览
  /system/data-permission 行级数据范围（独立页面，资源来源不一致）
```

**具体痛点：**

| 问题 | 影响 |
|------|------|
| 同一角色的授权配在 3 个地方 | 用户心智模型混乱，不知道去哪配什么 |
| 3 套独立的角色树、加载逻辑 | 代码重复 ~1500 行，维护成本高 |
| 资源数据源不一致 | `FORM:` vs `LOWCODE_FORM:` 前缀 Bug，导致动作下拉为空 |
| 授权 Tab 在角色抽屉里 | 1180px 宽度挤 6 个 Tab，体验差 |
| 数据权限不在企业授权页 | 企业授权号称"企业授权"却管不了数据行权限 |
| 角色 CRUD 耦合在授权页 | 2816 行巨型组件，难以独立迭代 |

### 1.2 目标

一个页面完成一个角色的全部生命周期管理：

```
┌──────────────────────────────────────────────────────────────────┐
│  角色与授权   （/system/authz）                                    │
│                                                                  │
│  ┌─────────────────┐  ┌─────────────────────────────────────────┐│
│  │  角色列表        │  │  Tab 栏                                 ││
│  │                 │  │  ┌─────┬──────┬──────┬──────┬──────┐   ││
│  │  [ + 新建角色 ]  │  │  │基本  │功能  │数据  │字段  │……   │   ││
│  │                 │  │  ├─────┴──────┴──────┴──────┴──────┤   ││
│  │  ● 管理员       │  │  │                                  │   ││
│  │  ● 部门经理     │  │  │  选中 Tab 的配置内容               │   ││
│  │  ● 普通员工     │  │  │                                  │   ││
│  │  ● 审批员       │  │  │                                  │   ││
│  │                 │  │  │                                  │   ││
│  └─────────────────┘  └─────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

---

## 2. 当前代码结构分析

### 2.1 文件规模

| 文件 | 行数 | 职责 |
|------|------|------|
| `role/list.vue` | 2816 | 角色 CRUD + 授权 Drawer（6 Tab） |
| `authz/index.vue` | 432 | 角色树 + 企业授权（7 Tab） |
| `data-permission/list.vue` | 1046 | 数据权限策略 CRUD |
| `authz/components/FunctionGrantsTab.vue` | 207 | 功能授权（Drawer 表单 + 表格） |
| `authz/components/FieldPoliciesTab.vue` | 223 | 字段规则（Drawer 表单 + 表格） |
| `authz/components/GuardTemplatesTab.vue` | 205 | 守卫模板（启用/禁用/重置） |
| `authz/components/DecisionPreviewTab.vue` | 178 | 决策预览（角色模拟） |
| `authz/components/AcceptanceTab.vue` | 163 | 上线验收 |
| `authz/components/DiagnosticsTab.vue` | 88 | 页面功能映射诊断 |
| `authz/components/LowcodeBundlesTab.vue` | 141 | 低代码应用授权预设包 |
| `role/components/PageCapabilityAuthorizationWorkbench.vue` | - | 能力目录引导式授权工作台 |
| `role/components/AuthorizationStageNavigation.vue` | - | 授权阶段导航 |
| `role/components/FieldEnforcementCapabilities.vue` | - | 字段执行能力标签 |
| **合计** | **~5500** | |

### 2.2 数据流分析

```
                            ┌─── role/list.vue ───┐
                            │                      │
  getAuthorizationResourceTree ──→ authorizationTree
  getAuthorizationAdminOptions ──→ authorizationOptions
  getRoleList ────────────────────→ roles (完整)
  getRoleAuthorizationProfile ───→ authorizationProfile
  getOrgDimensions ──────────────→ orgDimensions
  getOrgTree ────────────────────→ orgOptionsMap
                            │                      │
                            └──────────────────────┘

                            ┌─── authz/index.vue ──┐
                            │                       │
  getAuthorizationResourceTree ──→ resourceTree
  getAuthorizationAdminOptions ──→ adminOptions
  getRoleList ────────────────────→ roleList (简化)
  getRoleAuthorizationProfile ───→ (各 Tab 内部调用)
                            │                       │
                            └─── provide('authzContext') ──→ Tab 子组件
                                    inject('authzContext')

                            ┌─── data-permission/list.vue ──┐
                            │                                │
  getRoleList ───────────────────→ roles
  getFormDataResources ──────────→ formDataResources (FORM: 前缀!)
  getAuthorizationResourceTree ──→ resourceTree (仅用于取动作)
  getOrgDimensions ──────────────→ dimensions
  getOrgTree ────────────────────→ orgOptionsMap
                            │                                │
                            └────────────────────────────────┘
```

**共享数据（可合并）：**

| 数据 | role/list | authz/index | data-permission |
|------|-----------|-------------|-----------------|
| `getAuthorizationResourceTree` | ✅ | ✅ | ✅ |
| `getAuthorizationAdminOptions` | ✅ | ✅ | ❌ |
| `getRoleList` | ✅ | ✅ | ✅ |
| `getRoleAuthorizationProfile` | ✅ | ✅ (Tab 内) | ❌ |
| `getOrgDimensions` | ✅ | ❌ | ✅ |
| `getOrgTree` | ✅ | ❌ | ✅ |
| `getFormDataResources` | ❌ | ❌ | ✅ (Bug) |
| `createDataPolicy` | ✅ (内嵌) | ❌ | ✅ |
| `saveAuthorizationGrant` | ❌ | ✅ | ❌ |
| `saveAuthorizationFieldPolicy` | ✅ (内嵌) | ✅ | ❌ |

### 2.3 关键差异

| 维度 | role/list.vue | authz/index.vue | data-permission/list.vue |
|------|--------------|-----------------|--------------------------|
| 角色 CRUD | 完整（表单+校验+唯一性检查） | 无 | 无 |
| 角色选择方式 | 表格 + Drawer | 左侧 Tree | 左侧 Tree |
| 功能授权 UI | Tree 勾选（全量同步模式） | Table + Drawer（逐条 CRUD） | 无 |
| 数据权限 UI | 内嵌行内表单（简化版） | 无 | 完整表格 + Drawer（高级版） |
| 字段规则 UI | 内嵌行内表单（简化版） | Drawer 表单 + 表格 | 无 |
| 守卫模板 UI | 只读查看 | 启用/禁用/新增/重置 | 无 |
| 决策预览 | 实际用户 + 角色模拟 | 独立 Tab（功能更全） | 无 |
| 低代码 Bundle | 有（PageCapabilityWorkbench） | 有（LowcodeBundlesTab） | 无 |
| 上线验收 | 无 | 有 | 无 |
| 页面诊断 | 无 | 有 | 无 |
| 资源数据源 | resourceTree | resourceTree | formDataResources + 硬编码 |

---

## 3. 整合方案

### 3.1 新页面结构

```
trio-base-frontend/apps/web-antd/src/views/system/authz/
├── index.vue                          # 入口：角色树 + Tab 容器
│
├── components/
│   ├── RoleBasicTab.vue               # 【新建】角色基本：CRUD 表单 + 用户分配
│   ├── FunctionGrantsTab.vue          # 【保留】功能授权（树形勾选，从 role/list 迁移）
│   ├── DataPoliciesTab.vue            # 【新建】数据权限策略（从 data-permission 迁移）
│   ├── FieldPoliciesTab.vue           # 【保留】字段规则
│   ├── GuardTemplatesTab.vue          # 【保留】守卫模板
│   ├── LowcodeBundlesTab.vue          # 【保留】低代码应用授权
│   ├── DecisionPreviewTab.vue         # 【保留】决策预览
│   ├── AcceptanceTab.vue              # 【保留】上线验收
│   └── DiagnosticsTab.vue             # 【保留】页面功能映射诊断
│
├── composables/
│   ├── useAuthzResources.ts           # 【新建】资源树 + adminOptions 加载
│   ├── useRoleList.ts                 # 【新建】角色列表 CRUD 逻辑
│   ├── useFunctionGrants.ts           # 【新建】功能授权树勾选逻辑
│   ├── useDataPolicies.ts             # 【新建】数据策略 CRUD 逻辑
│   └── useFieldPolicies.ts           # 【新建】字段策略 CRUD 逻辑
│
└── types.ts                           # 【新建】共享类型定义
```

### 3.2 完整 Tab 列表

| Tab Key | 组件 | 来源 | 说明 |
|---------|------|------|------|
| `basic` | RoleBasicTab | **新建** | 角色名称/编码/状态/描述 + 分配用户 |
| `function` | FunctionGrantsTab | role/list 迁移 | 功能授权（Tree 勾选 + 派生菜单预览） |
| `data` | DataPoliciesTab | data-permission 迁移 | 行级数据范围（角色→资源→动作→维度→组织） |
| `field` | FieldPoliciesTab | authz 现有 | 字段读写/掩码规则 |
| `guard` | GuardTemplatesTab | authz 现有 | 业务约束模板 |
| `bundles` | LowcodeBundlesTab | authz 现有 | 低代码预设包一键授权 |
| `preview` | DecisionPreviewTab | authz 现有 | 实际用户 + 角色模拟决策预览 |
| `acceptance` | AcceptanceTab | authz 现有 | 上线验收检查 |
| `diagnostics` | DiagnosticsTab | authz 现有 | 页面功能映射诊断 |

### 3.3 组件树

```
index.vue
├── RoleTreePanel (左侧)
│   ├── 新建角色按钮 → 触发 basic Tab + 新建模式
│   ├── roleTree (Tree 组件)
│   └── 角色提示文字
│
└── TabContent (右侧)
    ├── Tabs (v-model:activeKey)
    │   ├── TabPane key="basic"
    │   │   └── RoleBasicTab
    │   │       ├── 角色表单（编码/名称/状态/描述）
    │   │       ├── 用户分配（Table + 添加/移除）
    │   │       └── 删除角色（危险操作按钮）
    │   │
    │   ├── TabPane key="function"
    │   │   └── FunctionGrantsTab
    │   │       ├── 资源动作 Tree（checkable）
    │   │       ├── 派生菜单预览
    │   │       └── 保存按钮
    │   │
    │   ├── TabPane key="data"
    │   │   └── DataPoliciesTab
    │   │       ├── 策略表格（columns: 资源/动作/效果/维度/状态）
    │   │       ├── 新增策略 Drawer
    │   │       │   ├── 资源选择（来自 resourceTree）
    │   │       │   ├── 动作选择（级联）
    │   │       │   ├── 效果 (ALLOW/DENY)
    │   │       │   ├── 维度配置（可增删）
    │   │       │   │   ├── 维度选择
    │   │       │   │   ├── 范围类型 (SELF/OWN_ORG/…)
    │   │       │   │   └── 指定组织（ASSIGNED_ORGS 时）
    │   │       │   └── 描述
    │   │       └── 删除确认
    │   │
    │   ├── TabPane key="field"
    │   │   └── FieldPoliciesTab（现有逻辑迁移到 useFieldPolicies）
    │   │
    │   ├── TabPane key="guard"
    │   │   └── GuardTemplatesTab（现有）
    │   │
    │   ├── TabPane key="bundles"
    │   │   └── LowcodeBundlesTab（现有）
    │   │
    │   ├── TabPane key="preview"
    │   │   └── DecisionPreviewTab（现有）
    │   │
    │   ├── TabPane key="acceptance"
    │   │   └── AcceptanceTab（现有）
    │   │
    │   └── TabPane key="diagnostics"
    │       └── DiagnosticsTab（现有）
    │
    └── Tab 底部操作栏（可选：保存/取消，按 Tab 显示）
```

### 3.4 数据流设计

#### provide / inject 模型（扩展现有 authzContext）

```typescript
// types.ts
interface AuthzContext {
  // 核心状态
  selectedRoleId: Ref<string | undefined>;
  selectedRole: ComputedRef<RoleDetail | undefined>;
  roleList: Ref<Role[]>;

  // 资源数据（统一来源：getAuthorizationResourceTree）
  resourceTree: Ref<ResourceTree | undefined>;
  adminOptions: Ref<AdminOptions | undefined>;

  // 组织维度（从 service-org 加载）
  orgDimensions: Ref<OrgDimension[]>;
  orgOptionsMap: Ref<Record<string, { label: string; value: string }[]>>;

  // 派生数据
  resourceList: ComputedRef<ResourceNode[]>;
  resourceOptions: ComputedRef<Option[]>;
  fieldResourceOptions: ComputedRef<Option[]>;

  // 方法
  findResource: (resourceCode: string) => ResourceNode | undefined;
  actionOptionsForResource: (resourceCode: string) => Option[];
  fieldOptionsForResource: (resourceCode: string) => Option[];
  ensureOrgOptions: (dimensionCode: string) => Promise<void>;

  // 权限
  canQuery: ComputedRef<boolean>;
  canCreate: ComputedRef<boolean>;
  canUpdate: ComputedRef<boolean>;
  canDelete: ComputedRef<boolean>;

  // 全局状态
  loading: Ref<boolean>;
  saving: Ref<boolean>;

  // 刷新回调
  refreshRoleList: () => Promise<void>;
  refreshResources: () => Promise<void>;
}
```

#### 数据加载时序

```
index.vue onMounted:
  ├── refreshResources()
  │   ├── getAuthorizationResourceTree()
  │   ├── getAuthorizationAdminOptions()
  │   └── getOrgDimensions()
  └── refreshRoleList()
      └── getRoleList()

选择角色后 → 各 Tab watch(selectedRoleId):
  basic    → getRoleDetail(roleId)
  function → getRoleAuthorizationProfile(roleId)
  data     → getRoleAuthorizationProfile(roleId)  // 统一 API
  field    → getRoleAuthorizationProfile(roleId)  // 统一 API
```

**关键改进：** 数据权限 Tab 不再单独调用 `getFormDataResources()`，而是从 `resourceTree` 统一获取资源列表。这同时修复了 `FORM:` vs `LOWCODE_FORM:` 前缀 Bug。

### 3.5 统一资源数据源（修复 Bug）

```diff
- // data-permission/list.vue (当前 - 有问题)
- const resourceOptions = computed(() => [
-   { label: '平台内置资源', options: [{ label: '用户 USER', value: 'USER' }, { label: '组织 ORG_UNIT', value: 'ORG_UNIT' }] },
-   { label: '已发布低代码表单', options: formDataResources.value.map(...) },  // FORM: 前缀
- ]);

+ // DataPoliciesTab.vue (整合后 - 正确)
+ const resourceOptions = computed(() =>
+   ctx.resourceList.value.map(r => ({
+     label: `${r.displayName || r.resourceCode} · ${r.resourceType}`,
+     value: r.resourceCode,  // LOWCODE_FORM: 前缀，与 actionOptions 一致
+   }))
+ );
```

同时需要修复 `FormDefinitionService.toDataResource()` 的后端前缀：
```diff
- response.setResourceCode("FORM:" + definition.getFormKey()...);
+ response.setResourceCode("LOWCODE_FORM:" + definition.getFormKey()...);
```

---

## 4. 实施步骤

### 阶段 1：基础设施（不影响现有页面）

**Step 1.1: 创建类型定义文件**
- 新建 `authz/types.ts`
- 从 `role/list.vue` 和 `authz/index.vue` 提取共享类型
- 定义 `AuthzContext` 接口

**Step 1.2: 创建 composables**
- `useAuthzResources.ts`：封装 `getAuthorizationResourceTree` + `getAuthorizationAdminOptions` + `getOrgDimensions` 加载逻辑
- `useRoleList.ts`：从 `role/list.vue` 提取角色 CRUD 逻辑（queryForm, pagination, loadRoles, createRole, updateRole, deleteRole, roleCodeExists）
- `useFunctionGrants.ts`：从 `role/list.vue` 提取 Tree 勾选 + `replaceRoleFunctionGrants` 逻辑
- `useDataPolicies.ts`：从 `data-permission/list.vue` 提取 data policy CRUD 逻辑
- `useFieldPolicies.ts`：从 `role/list.vue` 和 `authz/FieldPoliciesTab.vue` 提取字段策略 CRUD 逻辑

**Step 1.3: 修复后端资源码前缀**
- `FormDefinitionService.toDataResource()`: `"FORM:"` → `"LOWCODE_FORM:"`
- 确保 `getFormDataResources()` 返回的 `resourceCode` 与 authz tree 一致

### 阶段 2：新建组件（不删旧代码）

**Step 2.1: 创建 RoleBasicTab.vue**
- 角色表单：编码（新建可填，编辑不可改）、名称、状态、描述
- 用户分配：表格展示 → 添加/移除对话框
- 删除角色：危险操作（确认 + 二次确认）
- 保存按钮：create 或 update

**Step 2.2: 创建 DataPoliciesTab.vue**
- 从 `data-permission/list.vue` 迁移核心逻辑
- 改为使用 `inject('authzContext')` 获取资源列表
- 保留完整的策略表格 + Drawer 表单
- 移除独立的角色树（使用父级角色树）
- 移除 `builtInResourceOptions` 硬编码

**Step 2.3: 重构 FunctionGrantsTab.vue**
- 从现有 `authz/components/FunctionGrantsTab.vue` 的逐条 CRUD 模式 → 改为 Tree 勾选模式（role/list.vue 的授权树）
- 合并两种 UI 的优点：Tree 全览 + 派生菜单预览 + 一键保存
- 使用 `useFunctionGrants` composable

**Step 2.4: 重构 FieldPoliciesTab.vue**
- 将 `role/list.vue` 中内嵌的字段规则表单与 authz 的 FieldPoliciesTab 合并
- 使用 `useFieldPolicies` composable
- 增加 FieldEnforcementCapabilities 展示

### 阶段 3：组装入口

**Step 3.1: 重写 authz/index.vue**
- 保留角色树面板（现有的 authz 角色树）
- Tab 栏替换为完整 9 个 Tab
- provide 扩展后的 AuthzContext（包含 composables 的方法）

**Step 3.2: 更新路由**
```typescript
// system.ts
{
  path: 'authz',
  name: 'SystemAuthz',
  meta: { icon: 'lucide:shield-user', title: '角色与授权' },
  component: () => import('#/views/system/authz/index.vue'),
},
{
  // 保留旧路由为重定向（兼容书签）
  path: 'role',
  redirect: '/system/authz?tab=basic',
  meta: { hideInMenu: true },
},
{
  path: 'data-permission',
  redirect: '/system/authz?tab=data',
  meta: { hideInMenu: true },
},
{
  path: 'authorization',
  redirect: '/system/authz',
  meta: { hideInMenu: true },
},
```

### 阶段 4：清理

**Step 4.1: 删除旧文件**
- `role/list.vue` → 删除（逻辑已迁移到 composables + RoleBasicTab）
- `data-permission/list.vue` → 删除（逻辑已迁移到 DataPoliciesTab）
- `authz/AuthorizationRedirect` 路由 → 删除
- `role/components/` 中不再需要的子组件 → 归档

**Step 4.2: 更新菜单国际化**
- 菜单名从"企业授权" → "角色与授权"
- 删除"角色管理"、"数据权限"菜单项（或转为隐藏重定向）

---

## 5. 风险与缓解

| 风险 | 等级 | 缓解措施 |
|------|------|----------|
| 功能回归 | 中 | 保持旧路由重定向，分阶段上线，先灰度 |
| 角色 CRUD 逻辑遗漏 | 中 | composable 从 role/list.vue 代码逐函数提取，保持签名不变 |
| 数据权限 resourceCode 变更 | 低 | 仅改前缀格式，且与 authz tree 对齐，向前兼容 |
| 大型 PR 难以 Review | 中 | 分为 4 个阶段独立 PR，每个 < 800 行变更 |
| 用户习惯变更 | 低 | 保留旧 URL 重定向，新页面布局与现有 authz 页面一致 |

---

## 6. 工作量估算

| 阶段 | 内容 | 估时 |
|------|------|------|
| 阶段 1 | composables + 类型提取 | 3-4h |
| 阶段 2 | 新建/重构 4 个 Tab 组件 | 6-8h |
| 阶段 3 | 入口组装 + 路由更新 | 2-3h |
| 阶段 4 | 清理 + 回归测试 | 2-3h |
| 后端修复 | FormDefinitionService 前缀修正 | 0.5h |
| **合计** | | **14-19h** |

---

## 7. 菜单重命名对照

| 现状 | 整合后 |
|------|--------|
| 🗑️ 角色管理 `/system/role` | → 重定向到 `/system/authz?tab=basic` |
| ✏️ 企业授权 `/system/authz` | → **角色与授权** `/system/authz`（Tab 0 = 基本） |
| 🗑️ 数据权限 `/system/data-permission` | → 重定向到 `/system/authz?tab=data` |
