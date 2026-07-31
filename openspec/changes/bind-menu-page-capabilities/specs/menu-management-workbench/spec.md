## MODIFIED Requirements

### Requirement: 主从式菜单管理工作台
系统 SHALL 将菜单管理页面呈现为左侧导航树和右侧节点详情的主从式工作台，并 SHALL 在未选中节点时展示明确的空状态。页面类型节点的详情 SHALL 展示其显式绑定的后端页面及由 Page Capability Catalog 返回的页面功能。

#### Scenario: 进入菜单管理页面
- **WHEN** 管理员进入菜单管理页面且菜单数据加载成功
- **THEN** 系统在左侧展示导航树，并在右侧展示当前选中节点的只读详情或空状态

#### Scenario: 选择绑定页面的导航节点
- **WHEN** 管理员选择一个已经绑定 `pageCode` 的页面菜单
- **THEN** 系统在右侧展示基本信息、路由信息、显示设置及后端目录返回的 ACCESS、READ 和 OPERATION 页面功能

#### Scenario: 选择未绑定页面的导航节点
- **WHEN** 管理员选择一个没有绑定 `pageCode` 的页面菜单
- **THEN** 系统明确提示“未绑定后端页面”且不把历史按钮节点冒充为 Page Capability 目录

### Requirement: 导航节点与权限节点分离
系统 SHALL 仅展示可形成导航结构的目录、菜单、外链和内嵌节点。系统 SHALL 将后端 Page Capability Catalog 作为页面可授权功能的唯一目录来源，并 SHALL NOT 接受或展示 button 菜单权限节点。

#### Scenario: 页面存在已注册功能
- **WHEN** 业务菜单绑定的页面存在活动 Page Capability
- **THEN** 右侧页面功能区域按后端目录展示功能名称、类别、依赖和 readiness，而不要求管理员填写权限码

#### Scenario: 旧权限节点退出工作台
- **WHEN** 数据库升级到新的页面能力目录模型
- **THEN** 历史 button 菜单节点被清理，菜单工作台不再展示兼容区域

#### Scenario: 页面功能目录加载失败
- **WHEN** 前端无权读取或无法加载后端页面功能目录
- **THEN** 系统展示明确错误状态且不得将失败结果解释为该页面没有可授权功能

### Requirement: 分组编辑菜单配置
系统 SHALL 将菜单编辑字段分为基本信息、页面与路由配置和显示设置，并 SHALL 使用“菜单标识”和“显示名称”作为字段术语。页面类型节点 SHALL 从后端 Page Capability Catalog 形成的页面选项中选择并保存稳定 `pageCode`；前端 SHALL NOT 要求实施人员手工创建按钮权限或填写其底层 API 权限码。

#### Scenario: 新增业务菜单并选择页面
- **WHEN** 管理员新增页面类型菜单并从页面选项中选择一个后端页面
- **THEN** 系统保存该页面的稳定 `pageCode` 并在详情中加载对应页面功能

#### Scenario: 编辑高级路由信息
- **WHEN** 具备菜单修改权限的管理员编辑已绑定页面的菜单
- **THEN** 系统允许维护路由、组件、上级、排序和显示设置，但不允许修改 Owner 服务发布的页面功能定义

#### Scenario: 创建目录或外链
- **WHEN** 管理员创建不承载业务页面的目录或外链节点
- **THEN** 系统允许 `pageCode` 为空且不要求配置页面功能

## ADDED Requirements

### Requirement: 页面功能完整性展示
系统 SHALL 按 Page Capability readiness 展示页面功能完整性，只有 `READY` 功能可进入新的授权配置；`PARTIAL`、`BROKEN` 和 `UNMAPPED` 功能 SHALL 保持可诊断但不可授权。

#### Scenario: 展示已就绪功能
- **WHEN** 页面功能的 readiness 为 `READY`
- **THEN** 系统将其标记为可授权并展示业务名称和必要依赖

#### Scenario: 展示未就绪功能
- **WHEN** 页面功能的 readiness 不是 `READY`
- **THEN** 系统禁用其授权选择并展示后端返回的 readiness 状态和说明
