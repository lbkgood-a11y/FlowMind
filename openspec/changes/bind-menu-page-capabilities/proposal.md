## Why

菜单管理当前允许实施人员手工创建按钮/API 权限节点并填写权限码，但页面真实可授权操作已经由后端 Page Capability Catalog 声明，二者缺少显式关联并可能漂移。需要让菜单显式绑定后端页面能力，并由后端目录向前端提供可配置操作，确保实施人员只选择经过验证的业务功能。

## What Changes

- Ensure the system Page Capability Catalog is materialized for the default tenant at startup and lazily repaired for the authenticated tenant before catalog-dependent menu or role workflows run.
- Add a dedicated read-only Page Capability Catalog navigation entry and workbench for catalog lifecycle, page capabilities, readiness, dependencies, and target diagnostics.

- 菜单页面节点显式绑定稳定的 `pageCode`，不再仅依赖 `menuKey` 猜测页面能力关系。
- Page Capability 查询支持按 `pageCode` 获取页面能力，并保持租户、活动目录和 readiness 约束。
- 菜单管理右侧将手工维护的“权限配置”主路径替换为后端驱动的“页面功能”列表，展示 ACCESS、READ 和 OPERATION 能力及完整性状态。
- 新建/编辑菜单时从后端页面能力目录选择业务页面，自动建立页面关联；高级路由信息继续可维护。
- 可授权页面操作由 Owner 服务清单注册，前端不得创造新的权限事实；非 READY 能力只能诊断，不能用于授权配置。
- 保留历史 button 菜单节点的兼容读取，但不再将“新增权限节点”作为新配置入口。

## Capabilities

### New Capabilities

<!-- No new standalone capability. -->

### Modified Capabilities

- `menu-management-workbench`: 菜单节点显式绑定后端页面目录，并以只读、后端驱动的页面功能列表替代手工按钮权限维护主路径。
- `enterprise-authorization-model`: Page Capability Catalog 成为可授权页面操作的权威来源，并支持按页面检索和 readiness 限制。

## Impact

- `service-auth` 菜单实体、请求/响应 DTO、菜单服务、数据库迁移和 Page Capability 查询接口。
- `web-antd` 菜单 API 类型、菜单工作台详情与编辑抽屉、页面能力目录调用。
- 菜单及页面能力相关单元、契约和前端组件测试。
- 历史 button 节点继续兼容读取；现有授权事实仍只存储在 `sys_auth_grant`，不会新增平行授权表。
