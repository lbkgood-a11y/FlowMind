## Why

当前平台已经具备用户、角色、菜单、组织、数据权限和治理类能力，但中后台日常运营还缺少统一的通知公告、站内消息、文件、导入导出和后台任务能力。继续补齐这组基础能力，可以让后续业务微服务复用同一套权限体系、文件与异步任务模型，避免各服务重复建设。

## What Changes

- 新增运营支撑服务能力，覆盖通知公告、站内消息、文件中心、导入导出任务、后台任务调度。
- 新增统一的运营服务 `service-ops`，负责运营业务数据、文件元数据、异步任务记录和调度任务执行记录。
- 继续由 `service-auth` 维护菜单和按钮权限元数据，为运营能力种子化菜单、接口权限和按钮权限。
- 网关新增运营服务路由，所有运营 API 仍通过网关鉴权、审计和 TraceId 透传。
- 前端新增运营支撑相关管理页面，并沿用现有菜单权限、按钮权限和表格/表单交互模式。

## Capabilities

### New Capabilities

- `platform-announcement-management`: 管理平台通知公告的草稿、发布、下线、目标范围和阅读状态。
- `platform-message-center`: 提供站内消息收件箱、已读未读、消息投递和管理员查询能力。
- `platform-file-center`: 管理文件上传、下载、元数据、业务引用和权限校验。
- `platform-import-export-task`: 管理异步导入导出任务、模板下载、执行进度、结果文件和失败明细。
- `platform-job-scheduler`: 管理后台任务定义、启停、手动触发和执行日志。

### Modified Capabilities

- None.

## Impact

- 后端新增 `trio-base-services/service-ops` Spring Boot 模块和数据库迁移。
- `service-auth` 新增运营菜单、接口权限和按钮权限种子数据。
- `platform-gateway` 新增运营 API 路由。
- 前端 `trio-base-frontend/apps/web-antd` 新增运营页面、API 客户端、路由本地化和按钮权限绑定。
- 本地开发环境需要新增运营服务端口与配置，第一版使用本地文件存储，后续可替换为对象存储。
