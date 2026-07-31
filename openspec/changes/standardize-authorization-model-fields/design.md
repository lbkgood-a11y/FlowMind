## Context

TrioBase 的权限决策已经同时依赖主体、租户、资源、动作、数据范围、字段规则、业务守卫、Global Action 和审计版本。当前各表的字段由业务模块分别演进，`SysRole` 等模型仍可能缺少租户归属，部分数据范围只能通过约定列名发现，平台全局数据有时又与默认租户混合。字段标准必须能够支持严格隔离和自动化检查，同时不能要求不可变事件、关系表和普通聚合机械继承同一 Java 基类。

## Goals / Non-Goals

**Goals:**

- 给出模型分类和每类字段最小集合。
- 让租户、数据范围、字段权限、并发控制、Global Action 和审计具备稳定的数据库承载字段。
- 通过 Schema 契约测试和架构门禁自动发现遗漏。
- 为现有模型提供可分批、可回滚的迁移顺序。

**Non-Goals:**

- 不统一所有业务表的领域字段。
- 不要求所有表都支持软删除、组织范围或 Global Action。
- 不把审计日志、事件、投影强制继承普通可变实体基类。
- 不允许权限中心通过跨库读取这些字段；字段仍由 owner 服务拥有。

## Decisions

### 1. 采用“基础必备 + 条件必备”而不是万能基类

所有持久化模型都需要稳定身份和时间语义，但 `tenant_id`、`version`、所有者、组织、软删除、Action 关联只在对应能力启用时强制。Java 可以提供 `BaseEntity`、`TenantEntity`、`VersionedEntity` 等组合接口或嵌入对象，Schema 契约才是最终判定依据。

替代方案是所有实体继承一个包含全部字段的基类；该方案会在关系表、事件和投影中制造大量无意义列，因此不采用。

### 2. 租户作用域必须显式且非空

租户业务行使用真实 `tenant_id`。平台全局定义使用约定的 `GLOBAL` 值或独立 `scope_type=GLOBAL`，不使用 null/空串表示全局。所有租户自然键均把 `tenant_id` 放入唯一约束，跨实体引用由服务校验同租户，关键关系在可行时使用复合外键。

### 3. 数据范围只编译注册过的类型化列

模型元数据注册 SELF 和 ORG 的列映射；默认候选只能作为兼容期机制。权限拦截器无法找到已声明范围对应列时 fail-closed。JSONB 可以存放动态业务字段，但不得作为唯一的租户或组织边界。

### 4. 版本字段服务于不同并发域

业务聚合使用行级 `version`；授权目录和策略可以使用行级版本加全局/租户策略版本；投影使用源 offset/version。版本必须单调，并通过数据库条件更新，而不是依赖 JVM 缓存或 `updated_at` 相等判断。

### 5. 审计字段区分“谁修改”与“什么动作导致”

`created_by/updated_by` 表达直接操作者，`action_id` 表达受治理的业务动作，`trace_id` 表达技术链路，`process_instance_id` 表达长流程关联。它们不能互相替代。服务自动操作使用注册的 service actor。

### 6. 授权核心模型采用明确的最小字段集

- 用户：`id, tenant_id, username/login_identifier, password_hash/credential_ref, status, created_at, updated_at, created_by, updated_by`。
- 角色：`id, tenant_id, role_code, role_name, status, created_at, updated_at, created_by, updated_by`。
- 主体关系：`id 或复合键, tenant_id, user_id/subject_id, role_id/group_id, created_at, created_by`。
- 资源：`id, tenant_id, resource_code, resource_type, owner_service, lifecycle_status, version, audit fields`。
- 动作：`id, tenant_id, resource_code/resource_id, action_code, action_category, status, audit fields`。
- 字段目录：`id, tenant_id, resource_code/resource_id, field_key, classification, status, audit fields`。
- 授权：`id, tenant_id, subject_type, subject_id, resource_code, action_code, effect, status, version, audit fields`。
- 数据/字段/守卫策略：授权公共键，加 `policy_type, combine_mode, policy payload/reference, status, version, audit fields`。
- 决策日志：主体、租户、资源、动作、结果、原因、版本、Action/Trace、owner、发生时间；不可变且不存敏感原值。

### 7. 门禁以数据库 Schema 为主、代码映射为辅

新增 schema contract test 读取 PostgreSQL information_schema 或 Testcontainers 实例，校验列、类型、nullability、索引和唯一约束；ArchUnit 校验受治理实体实现分类接口并映射必要字段；Flyway 测试验证从空库和上一稳定版本升级。显式 waiver 必须注明 owner、原因和到期日期。

## Risks / Trade-offs

- [给现有大表增加非空列会锁表] → 先加可空列、分批回填、建立索引，再添加 NOT NULL 和约束。
- [GLOBAL 值与真实租户代码冲突] → 租户服务保留系统作用域名称，禁止租户注册保留值。
- [所有者/组织字段冗余] → 由 owner 事件维护，使用事件 ID 和版本保证投影幂等；不跨库查询。
- [过多字段降低开发速度] → 提供分类模板、Flyway 片段和代码生成器，CI 输出具体缺失字段。
- [旧表无法立即满足] → 使用有期限 waiver 和分级迁移，不允许永久静默豁免。
- [复合外键增加写入复杂度] → 核心权限关系优先数据库约束，跨服务引用由 owner API/事件契约验证。

## Migration Plan

1. 建立模型清单并给每张表标注分类、owner、租户作用域和启用能力。
2. 优先迁移权限核心表：角色、用户角色关系、资源、动作、授权与策略，补齐 `tenant_id`、审计和唯一约束。
3. 迁移受数据权限保护的业务事实，补齐类型化 owner/org 字段并回填。
4. 迁移 Global Action、工作流关联和审计表的 action/trace/idempotency 字段。
5. 为投影补齐 source event/version，并验证重放幂等。
6. 先以报告模式运行 CI，清零高风险缺口后切换为阻断模式。
7. 回滚时保留新增列和已回填数据，仅关闭新约束/门禁；不得恢复 null 表示全局的语义。

## Open Questions

- `GLOBAL` 使用保留 tenant 值还是统一引入 `scope_type`，需要结合现有表迁移成本最终确定。
- 核心权限关系是否全面采用复合外键，需要评估现有 ULID 全局唯一性与写入成本。
- 动态低代码表单的 owner/org 列是固定顶层列还是受控 projection 表，需要在低代码运行时设计中确定。
