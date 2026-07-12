## Why

TrioBase 已经具备用户、角色、菜单、组织和数据权限的基础能力，但还缺少企业中后台必须具备的治理闭环：谁登录、谁操作、平台枚举从哪里来、系统策略如何调整。没有这些能力，权限体系难以审计，业务配置容易硬编码，后续微服务接入也缺少统一治理基线。

## What Changes

- 新增操作审计日志能力：记录关键管理操作的操作者、资源、动作、请求摘要、结果、耗时、TraceId 和错误信息。
- 新增登录日志与会话管理能力：记录登录成功/失败、刷新、退出等认证事件，并提供当前会话查询和强制失效的基础模型。
- 新增数据字典能力：统一维护组织类型、用户状态、资源动作等平台枚举，前端从后端读取选项，逐步移除硬编码。
- 新增系统参数能力：统一维护密码策略、Token TTL、默认首页、默认组织维度等平台参数，支持按权限查看和更新。
- 补齐菜单、按钮、API 权限种子：将审计日志、会话管理、数据字典、系统参数接入系统管理菜单。
- 前端新增治理页面：在系统管理下新增审计日志、登录日志/会话、数据字典、系统参数页面，并按 API 权限码控制按钮。

## Capabilities

### New Capabilities

- `platform-audit-log`: 平台操作审计日志，覆盖管理类 API 的操作记录、查询与详情查看。
- `platform-session-management`: 登录日志和会话管理，覆盖登录事件记录、在线会话查询、退出与强制失效。
- `platform-dictionary-management`: 数据字典类型和字典项管理，统一平台枚举来源。
- `platform-system-config`: 系统参数配置管理，支持平台级配置项查看、更新和缓存友好的读取。

### Modified Capabilities

<!-- No archived specs exist yet. Existing auth/org/data-permission implementations will be integrated through the new governance capabilities. -->

## Impact

| 影响范围 | 详情 |
|----------|------|
| `service-auth` | 新增审计日志、登录日志/会话、字典、系统参数表与管理 API；认证服务写入登录日志 |
| `common-core` | 扩展审计过滤/上下文模型，统一读取 TraceId、用户、权限和请求元数据 |
| `platform-gateway` | 保持现有鉴权链路兼容，后续可透传会话 ID 和权限版本 |
| `trio-base-frontend/apps/web-antd` | 新增治理页面和 API 模块，接入系统管理菜单与按钮权限 |
| `docs/` | 固化平台治理设计、权限码和页面边界 |
| 数据库 | 新增治理相关表和菜单/API 权限种子；不破坏现有权限、组织、数据权限表 |
