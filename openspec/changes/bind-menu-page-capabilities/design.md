## Context

TrioBase 已经由 Owner 服务通过 Page Capability Manifest 注册页面、ACCESS/READ/OPERATION 能力、依赖和后端 Target，并通过活动租户目录供角色授权工作台消费。菜单管理仍将 `sys_menu` button 节点作为可编辑权限对象，页面能力仅通过 manifest 的 `menuKey` 反查菜单，导致导航元数据、页面功能和真实按钮之间没有显式闭环。

本变更横跨 `service-auth` 数据模型/API 和 Vue 菜单工作台，同时必须兼容现有菜单、历史 button 节点和正在生产化的 Page Capability 发布链路。

## Goals / Non-Goals

**Goals:**

- 以 Page Capability Catalog 作为可授权页面操作的唯一目录来源。
- 建立菜单到页面的稳定 `pageCode` 关联，并为既有系统菜单回填关联。
- 让实施人员在菜单编辑时选择后端已注册页面，在详情中查看后端返回的功能及 readiness。
- 防止前端选择或创造未注册、未就绪的可授权操作。
- 保留历史数据的读取兼容，避免升级时破坏动态路由。

**Non-Goals:**

- 本变更不改变角色授权草稿、编译和发布协议。
- 本变更不把搜索、刷新、打开抽屉等本地 UI 行为注册为可授权能力。
- 本变更不删除历史 button 菜单行，也不新增第二套按钮授权事实表。
- 本变更不允许菜单管理修改 Owner 服务发布的能力定义。

## Decisions

### 0. Catalog bootstrap is infrastructure, not an administrator workflow

System manifests are materialized for the default tenant at startup. Catalog-dependent APIs also perform an idempotent tenant-scoped bootstrap when no active catalog exists, so a newly introduced tenant cannot enter a menu/authorization dependency loop. Bootstrap failures remain visible through catalog lifecycle and diagnostics queries; administrators inspect them but do not manually create catalog facts.

The capability catalog receives its own read-only system menu. Menu management remains responsible only for navigation bindings, while role authorization consumes READY capabilities from the active catalog.

System manifests reference the persisted stable menu keys (`SystemUser`, `SystemRole`, and `SystemMenu`) rather than historical seed aliases. A forward-only repair migration backfills `page_code` for databases where the earlier alias-based migration has already executed.

### 1. 在 `sys_menu` 保存可空的 `page_code`

页面类型菜单可绑定一个稳定 `pageCode`；目录、外链和纯容器节点可以为空。数据库迁移根据活动 manifest 已知的 `menu_key` 回填系统页面。选择显式字段而不是持续使用 `menuKey` 推断，因为菜单标识属于导航配置，重命名不应破坏页面能力关联。

备选方案是建立 menu-capability 多对多表，但菜单只需要绑定页面，页面下面的能力由目录派生；额外关系表会复制目录事实并增加漂移风险。

### 2. 复用 Page Capability Catalog，不复制 button 节点

菜单前端调用受菜单查询权限保护的 `GET /api/v1/menus/page-capabilities?pageCode=...` 获取能力，该端点只读委托现有 Page Capability Catalog；企业授权工作台继续使用 `/api/v1/authz/page-capabilities`。两者仍由活动租户目录、状态和服务端排序约束，并返回业务名称、类别、依赖与 readiness。页面功能列表为只读目录视图；角色选择继续保存 capability selection 并编译为 Grant。

历史 button 节点继续出现在兼容信息中，但新菜单工作流不提供“新增权限”入口。这样避免一次升级同时删除历史数据，又能阻止继续产生双写事实。

### 3. 页面选择项由后端能力目录聚合

菜单抽屉根据能力响应按 `pageCode` 分组生成页面选项，显示 `pageName`，保存 `pageCode`。首版不新增独立页面目录端点，以降低 API 面积；现有能力接口已包含形成选项所需的数据。后端为 `pageCode` 增加精确过滤，避免详情加载整个目录。

### 4. readiness 决定可配置性

`READY` 能力正常展示；`PARTIAL`、`BROKEN`、`UNMAPPED` 展示诊断状态和服务端说明，但不得作为新的角色授权选择。菜单详情展示所有状态，以便实施人员发现缺口。菜单绑定页面不因个别能力未就绪而失败，但必须清晰提示。

### 5. 目录能力与页面运行时按钮使用同一稳定编码

页面实现仍以服务端编译后的资源/Action 授权结果控制按钮。菜单工作台只展示能力目录，不通过 DOM 扫描发现按钮，也不生成权限码。契约测试验证页面声明、后端 Target 和受控前端操作之间的稳定映射，避免仅按按钮文字模糊匹配。

## Risks / Trade-offs

- [旧菜单没有 `pageCode`] → 数据库回填已知系统页面；未匹配菜单保持为空并在工作台显示“未绑定页面”。
- [同一页面被多个菜单复用] → 允许多个菜单绑定相同 `pageCode`，能力目录仍只有一份，不复制授权事实。
- [现有 button 节点与目录能力名称不一致] → 只做兼容展示，不参与新配置；后续独立迁移确认无消费者后再清理。
- [目录接口权限不足导致详情空白] → 前端区分“没有能力”和“无权读取/加载失败”，不将失败解释为页面无功能。
- [与正在进行的 Page Capability 生产化修改冲突] → 只扩展现有 DTO/查询和菜单关联，不改发布、证据或决策核心路径。

## Migration Plan

1. 新增 `sys_menu.page_code` 可空列和索引，并按已知 `menu_key` 回填系统页面。
2. 扩展菜单实体、请求/响应和服务保存逻辑，同时保持旧客户端不传字段时兼容。
3. 扩展 Page Capability 查询的 `pageCode` 精确过滤。
4. 更新菜单工作台为页面选择和后端驱动的页面功能列表，移除新增/编辑权限节点主入口。
5. 部署后通过目录诊断检查未绑定菜单和非 READY 能力；回滚前端不要求回滚数据库可空列。

## Open Questions

- 历史 button 节点的物理清理时间留待后续迁移，在确认动态路由和旧授权消费者全部退役后决定。
