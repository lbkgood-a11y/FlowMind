# TrioBase 权限配置手册

**适用版本：** 当前主干版本  
**运行模式：** 默认租户 `default`（单租户优先）  
**适用对象：** 系统管理员、实施人员、业务管理员、后端与低代码开发人员

## 1. 手册目的

本手册说明如何在 TrioBase 中配置菜单、功能、数据范围、字段规则和业务守卫，并通过决策预览验证最终权限。当前系统以“角色 → 资源 → 动作”为核心授权模型，功能授权事实统一存储在 `sys_auth_grant`，菜单只负责导航展示。

> 权限配置原则：默认拒绝、按需授权、Deny 优先、前后端共同执行、业务服务最终兜底。

## 2. 当前运行约束

- 当前正式运行租户固定为 `default`。
- 管理页面配置时不应切换到其他租户。
- 所有资源、角色授权、数据策略和字段策略都应归属 `default`。
- 系统暂不承诺生产级多租户隔离；未来启用多租户后再按租户分别配置。
- `ADMIN` 当前承担平台管理职责，可访问已注册资源；普通角色必须获得明确授权。

## 3. 权限模型

```text
用户
  └─ 角色
      ├─ 菜单导航：能否看到入口
      └─ 功能授权 Grant
          └─ 资源 Resource + 动作 Action
              ├─ 数据范围 Data Scope
              ├─ 字段规则 Field Policy
              └─ 业务守卫 Guard
```

一次业务操作只有同时满足以下条件才能执行：

1. 用户属于有效角色。
2. 资源和动作已注册。
3. 存在匹配的 Allow，且不存在匹配的 Deny。
4. 数据记录处于允许的数据范围内。
5. 读写字段符合字段规则。
6. 业务服务本地守卫检查通过。

## 4. 核心概念

### 4.1 菜单导航

菜单决定用户是否能在界面看到页面入口，不是最终业务授权依据。不要为了每个按钮或单据动作创建隐藏菜单。

### 4.2 资源

资源是受保护的业务对象，必须使用稳定编码。

| 资源类型 | 示例 | 说明 |
|---|---|---|
| 低代码应用 | `LOWCODE_APP:EXPENSE` | 一个发布后的低代码应用 |
| 低代码表单 | `LOWCODE_FORM:EXPENSE` | 表单定义及其实例 |
| 自定义单据 | `CUSTOM_DOC:CONTRACT` | 手写服务中的合同等业务单据 |
| 工作流动作 | 以注册中心实际编码为准 | 待办、审批等流程操作 |
| 系统接口 | `/api/v1/users` 等 | 系统管理类接口兼容编码 |

资源编码发布后应保持稳定，不应包含数据库主键、版本号或页面临时路由。

### 4.3 动作

动作描述用户可以对资源做什么。

| 动作 | 含义 |
|---|---|
| `VIEW` | 查看列表或详情 |
| `CREATE` | 新建业务对象 |
| `EDIT` | 编辑已有对象 |
| `DELETE` | 删除对象 |
| `SUBMIT` | 提交单据或发起流程 |
| `APPROVE` | 审批通过 |
| `REJECT` | 驳回 |
| `EXPORT` | 导出数据 |
| `DESIGN` | 设计低代码或流程定义 |
| `PUBLISH` | 发布 |
| `OFFLINE` | 下线 |

新增动作时优先复用标准动作，不要为同一语义创建 `READ`、`QUERY`、`DETAIL` 等多个近义编码。

### 4.4 授权效果

- `ALLOW`：允许主体执行资源动作。
- `DENY`：明确拒绝；优先级高于所有匹配的 Allow。

Deny 只用于例外限制。常规权限应通过是否授予 Allow 来控制，避免权限关系难以解释。

## 5. 配置前准备

配置前确认：

- 用户已创建并处于启用状态。
- 角色已创建，用户已分配到正确角色。
- 目标低代码应用、表单或业务服务已经发布并同步授权资源。
- 管理员拥有 `/api/v1/authz/**` 对应的 GET、POST、PUT、DELETE 权限。
- 页面位于“系统管理 → 企业授权”（以当前菜单配置为准）。

如果资源列表中找不到目标资源，应先解决资源注册问题，不要手工用近似编码代替。

## 6. 推荐配置顺序

```text
创建角色
  → 分配用户
  → 配置菜单导航
  → 配置功能权限
  → 配置数据范围
  → 配置字段规则
  → 检查业务守卫
  → 决策预览
  → 使用真实账号验收
```

## 7. 配置菜单导航

1. 进入角色管理并选择目标角色。
2. 打开角色授权配置。
3. 在“菜单导航”中勾选允许展示的目录和页面。
4. 保存后重新登录或刷新动态路由。

注意：

- 勾选菜单不代表用户已经获得页面内的查看、编辑、审批权限。
- 页面菜单应与已注册资源动作保持合理映射。
- 父级目录可以因子菜单可见而自动展示，不需要创建额外功能授权。

## 8. 配置功能权限

1. 进入“企业授权 → 功能权限”。
2. 选择主体类型，常规配置选择 `ROLE`。
3. 选择或填写角色编码。
4. 从资源树选择目标资源。
5. 选择动作，例如 `VIEW`、`EDIT`、`SUBMIT`。
6. 效果选择 `ALLOW`。
7. 保存授权。

推荐最小授权示例：

| 角色 | 资源 | 动作 |
|---|---|---|
| 费用填报人 | `LOWCODE_FORM:EXPENSE` | `VIEW`、`CREATE`、`EDIT`、`SUBMIT` |
| 费用审批人 | `LOWCODE_FORM:EXPENSE` | `VIEW`、`APPROVE`、`REJECT` |
| 审计人员 | `LOWCODE_FORM:EXPENSE` | `VIEW`、`EXPORT` |

不要仅因为角色可以进入页面就授予全部动作。

### 8.1 API 示例

```http
POST /api/v1/authz/grants
Content-Type: application/json

{
  "tenantId": "default",
  "subjectType": "ROLE",
  "subjectId": "R_EXPENSE_APPROVER",
  "resourceCode": "LOWCODE_FORM:EXPENSE",
  "actionCode": "APPROVE",
  "effect": "ALLOW"
}
```

删除授权：

```http
DELETE /api/v1/authz/grants/{grantId}
```

## 9. 配置数据范围

数据范围决定用户在拥有功能权限后能操作哪些记录。

| 范围 | 稳定编码 | 典型过滤方式 |
|---|---|---|
| 本人 | `SELF` | 创建人、所有人或提交人为当前用户 |
| 本组织 | `OWN_ORG` | 记录所属组织为当前组织 |
| 本组织及下级 | `OWN_ORG_AND_CHILDREN` | 所属组织位于当前组织树内 |
| 指定组织 | `ASSIGNED_ORGS` | 所属组织位于管理员选定集合 |
| 我参与的 | 以配置选项返回值为准 | 当前用户是参与人 |
| 我的待办候选 | 以配置选项返回值为准 | 当前用户为候选人或办理人 |
| 全部 | `ALL` | 当前租户内全部记录 |

配置步骤：

1. 选择角色。
2. 选择资源和动作，通常从 `VIEW` 开始。
3. 选择数据范围。
4. 若为指定组织，选择具体组织节点。
5. 保存并用决策预览检查解析结果。

重要约束：业务服务如果无法把某个范围安全转换为数据库条件，必须返回无数据或拒绝操作，不能自动扩大为 `ALL`。

## 10. 配置字段规则

字段规则同时控制读取与写入。

### 10.1 读取模式

| 模式 | 行为 |
|---|---|
| `VISIBLE` | 返回完整字段值 |
| `MASKED` | 返回脱敏值 |
| `HIDDEN` | 响应中不返回该字段 |

### 10.2 写入模式

| 模式 | 行为 |
|---|---|
| `EDITABLE` | 允许写入 |
| `READONLY` / `READ_ONLY` | 只读，服务端拒绝未授权修改 |
| `DENIED` | 禁止写入 |

配置步骤：

1. 进入“字段规则”。
2. 选择包含字段元数据的资源。
3. 选择字段、主体类型和角色或用户。
4. 设置读取模式、写入模式。
5. 使用 `MASKED` 时选择或填写脱敏策略。
6. 保存后通过决策预览查看 `fieldRules`。

字段隐藏和只读不能只靠前端控制。业务服务必须在返回 DTO 前脱敏/移除字段，并在写入时再次校验。

### 10.3 API 示例

```http
POST /api/v1/authz/field-policies
Content-Type: application/json

{
  "tenantId": "default",
  "resourceCode": "CUSTOM_DOC:CONTRACT",
  "fieldKey": "amount",
  "subjectType": "ROLE",
  "subjectId": "R_CONTRACT_VIEWER",
  "readMode": "MASKED",
  "writeMode": "READONLY",
  "maskStrategy": "LAST4",
  "description": "合同金额仅允许脱敏查看"
}
```

## 11. 业务守卫

业务守卫处理依赖业务状态、不能只靠静态角色判断的条件。

常见守卫：

- `DOCUMENT_STATUS`：当前单据状态允许该操作。
- `ARCHIVED_LOCK`：归档单据禁止编辑或提交。
- `WORKFLOW_CANDIDATE`：用户是待办候选人或办理人。
- `NO_SELF_APPROVAL`：禁止审批自己提交的单据。

权限中心只返回“需要执行哪些守卫”，实际判断由资源所属业务服务完成。管理员通常只检查和启停已注册模板，不应把任意脚本作为守卫注入系统。

最终判定遵循：

```text
中央权限允许 AND 全部本地守卫通过 = 操作允许
```

## 12. 决策预览与验收

每次新增角色或大范围调整权限后，都应执行决策预览。

1. 进入“决策预览”。
2. 输入真实用户 ID。
3. 选择资源和动作。
4. 涉及具体单据时填写业务对象 ID。
5. 租户使用 `default`。
6. 执行预览。

重点检查：

- `allowed`：最终是否允许。
- `effect`：命中的 Allow 或 Deny。
- `matchedGrantId`：命中的授权记录。
- `reasons`：允许或拒绝原因。
- `dataScope`：解析后的数据范围。
- `fieldRules`：字段读写规则。
- `guardRequirements`：业务服务还需执行的守卫。
- 各策略版本号：用于排查缓存和一致性问题。

API 示例：

```http
POST /api/v1/authz/decisions/preview
Content-Type: application/json

{
  "tenantId": "default",
  "userId": "U001",
  "resourceCode": "LOWCODE_FORM:EXPENSE",
  "actionCode": "APPROVE",
  "businessObjectId": "EXPENSE_202607_001"
}
```

预览成功后仍需使用普通业务账号完成以下验收：

- 菜单是否正确显示。
- 页面按钮是否按权限显示或禁用。
- 列表是否只返回允许范围的数据。
- 详情敏感字段是否隐藏或脱敏。
- 绕过前端直接调用接口时，后端是否仍然拒绝非法操作。
- 审批、自审批、归档编辑等业务守卫是否生效。

## 13. 权限判定优先级

当前授权引擎按以下原则判定：

1. 租户边界不匹配：拒绝。
2. 资源或动作未注册：拒绝，fail-closed。
3. 命中显式 Deny：拒绝。
4. 命中显式 Allow：中央功能权限允许。
5. `ADMIN` 对已注册资源具有管理兼容能力。
6. 无匹配授权：拒绝。
7. 中央允许后，数据、字段和业务守卫继续收窄权限。

任何下游组件都不能把拒绝结果扩大为允许。

## 14. 低代码资源配置

低代码表单和应用发布时会同步资源、动作、字段和守卫元数据。配置流程：

1. 完成表单或应用设计。
2. 发布资源。
3. 在授权资源树中确认资源已经出现。
4. 为角色授予标准动作。
5. 对敏感字段设置字段规则。
6. 使用决策预览验证。

如果修改了字段或动作元数据，应重新发布或执行幂等资源同步，再检查过期资源列表。

## 15. 手写业务服务接入

非低代码业务服务应通过授权 Manifest 声明资源：

```json
{
  "tenantId": "default",
  "serviceName": "service-contract",
  "documents": [
    {
      "code": "CUSTOM_DOC:CONTRACT",
      "documentType": "CONTRACT",
      "displayName": "合同单据",
      "actions": [
        { "actionCode": "VIEW" },
        { "actionCode": "EDIT", "guardCodes": ["DOCUMENT_STATUS", "ARCHIVED_LOCK"] },
        { "actionCode": "APPROVE", "guardCodes": ["NO_SELF_APPROVAL"] },
        { "actionCode": "EXPORT" }
      ],
      "fields": [
        {
          "fieldKey": "amount",
          "fieldLabel": "合同金额",
          "fieldType": "number",
          "sensitivityClassification": "FINANCIAL",
          "defaultMaskStrategy": "LAST4"
        }
      ]
    }
  ]
}
```

开发约束：

- DTO 字段名作为 `fieldKey`，不要使用数据库列名。
- 服务启动或发布阶段同步 Manifest，不要在每次业务请求中注册资源。
- 业务接口必须使用已认证的用户和租户上下文调用决策服务。
- 未注册、决策服务异常或不支持的数据范围必须 fail-closed。
- 守卫由资源所属服务本地执行。

## 16. 常见问题排查

| 原因码/现象 | 含义 | 处理方式 |
|---|---|---|
| `AUTHZ_RESOURCE_ACTION_NOT_REGISTERED` | 资源或动作未注册 | 发布/重新同步资源，核对编码 |
| `AUTHZ_GRANT_NOT_FOUND` | 没有匹配授权 | 核对用户角色和角色 Grant |
| `AUTHZ_DENY_GRANT_MATCHED` | 命中显式拒绝 | 检查并删除或修正 Deny |
| `AUTHZ_ALLOW_GRANT_MATCHED` | 命中 Allow | 若仍失败，继续检查数据、字段和守卫 |
| `AUTHZ_GUARD_DENIED` | 业务守卫拒绝 | 检查单据状态、候选人、自审批等条件 |
| `AUTHZ_FIELD_DENY_POLICY` | 字段策略拒绝 | 检查字段读取/写入模式 |
| `AUTHZ_FUNCTION_DENIED` | 功能权限拒绝 | 检查资源动作 Grant |
| 菜单不可见但接口允许 | 缺少菜单投影 | 检查菜单及 `permission_code` 映射 |
| 菜单可见但按钮不可用 | 只有导航权限 | 配置对应资源动作 Grant |
| 修改权限后未立即生效 | 版本或缓存未刷新 | 重新校验 Token/会话，检查授权版本头 |
| 能看到超范围数据 | 服务未执行数据范围 | 检查查询谓词和 fail-closed 行为 |

## 17. 审计与变更管理

- 正式环境权限调整应记录申请人、审批人、角色、资源、动作、变更原因和生效时间。
- 优先通过管理页面或正式 API 修改，不直接操作数据库。
- 调整前后分别保存决策预览结果。
- 定期审查长期未使用角色、显式 Deny、全量数据权限和敏感字段可见权限。
- 决策日志只记录字段键、策略结果、版本和 TraceId，不记录敏感字段原始值。

## 18. 管理员检查清单

### 上线前

- [ ] 用户、角色和用户角色关系正确。
- [ ] 菜单导航只包含必要入口。
- [ ] 资源和动作均已注册。
- [ ] 每个角色遵循最小权限原则。
- [ ] 数据范围不是误配的 `ALL`。
- [ ] 敏感字段已配置隐藏或脱敏。
- [ ] 审批类动作具备必要业务守卫。
- [ ] 决策预览符合预期。
- [ ] 使用普通账号完成端到端验收。
- [ ] 后端接口绕过前端测试仍能正确拒绝。

### 定期复查

- [ ] 离职或转岗用户的角色已撤销。
- [ ] 废弃资源和过期授权已清理。
- [ ] 全量数据权限有明确业务依据。
- [ ] 显式 Deny 仍有必要。
- [ ] 决策拒绝率和异常原因无明显突增。
- [ ] 授权资源同步无长期 stale 记录。

## 19. 相关接口

| 方法 | 路径 | 用途 |
|---|---|---|
| GET | `/api/v1/authz/resources/tree` | 查询授权资源树 |
| GET | `/api/v1/authz/configuration-options` | 查询配置选项 |
| GET | `/api/v1/authz/roles/{roleId}/authorization-profile` | 查询角色完整授权档案 |
| POST | `/api/v1/authz/grants` | 保存功能授权 |
| DELETE | `/api/v1/authz/grants/{id}` | 删除功能授权 |
| POST | `/api/v1/authz/field-policies` | 保存字段策略 |
| DELETE | `/api/v1/authz/field-policies/{id}` | 删除字段策略 |
| GET | `/api/v1/authz/guard-templates` | 查询守卫模板 |
| POST | `/api/v1/authz/decisions/preview` | 单项决策预览 |
| POST | `/api/v1/authz/decisions/batch-preview` | 批量决策预览 |
| GET | `/api/v1/authz/decision-logs` | 查询决策日志 |

内部运行时决策接口仅允许受信任服务使用，不应暴露给浏览器或外部调用方。
