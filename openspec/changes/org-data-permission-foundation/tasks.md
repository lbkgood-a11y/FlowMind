## 1. 数据库与权限种子

- [x] 1.1 新增 service-org 迁移：组织维度、组织关系、组织单元扩展字段、用户组织归属扩展字段和内置维度种子
- [x] 1.2 新增 service-auth 迁移：数据权限策略表、策略维度表、组织管理/数据权限管理 API 权限和菜单按钮种子
- [x] 1.3 确保迁移幂等，保留现有 `sys_org_unit.parent_id/tree_path` 兼容字段并初始化 `ADMIN` 维度关系

## 2. 后端组织管理能力

- [x] 2.1 新增/扩展组织维度、组织关系、组织单元、用户组织归属 Entity、Mapper、DTO
- [x] 2.2 扩展 OrgUnitService：维度列表、组织单元 CRUD、按维度树查询、关系保存/移动/删除、循环校验
- [x] 2.3 扩展用户组织归属：按用户和维度查询、保存归属、主组织校验、元数据保存
- [x] 2.4 扩展 OrgUnitController：补齐组织管理 API、`@RequirePermission` 注解和统一响应
- [x] 2.5 补充 service-org 单元测试，覆盖组织树、重复编码、循环关系、主组织校验

## 3. 后端数据权限能力

- [x] 3.1 新增数据权限 Entity、Mapper、DTO、VO，覆盖策略主表和维度范围表
- [x] 3.2 实现 DataPolicyService：角色策略列表、保存、删除、维度范围校验和原子替换
- [x] 3.3 实现数据权限运行时查询：按用户、资源、动作解析角色策略和组织上下文，返回标准 DTO
- [x] 3.4 新增 DataPolicyController：管理 API 与运行时查询 API，补齐 `@RequirePermission`
- [x] 3.5 补充 service-auth 单元测试，覆盖角色策略 CRUD、`ASSIGNED_ORGS` 校验、默认收窄结果

## 4. 统一授权运行时基础

- [x] 4.1 新增共享 AuthContext/DataScope DTO 或复用现有安全上下文，支持 tenantId、roles、权限版本、数据策略版本字段
- [x] 4.2 补齐公开端点/受保护端点约定文档和代码注解基础，保持现有权限链路兼容
- [x] 4.3 更新 RBAC/组织数据权限文档，记录实际 API 权限码、菜单 ID 和实现边界

## 5. 前端组织与数据权限页面

- [x] 5.1 新增前端 API 模块和类型：组织维度、组织单元、组织树、用户归属、数据策略
- [x] 5.2 新增组织管理页面：维度切换、组织树/列表、组织单元新增编辑删除、用户归属维护入口
- [x] 5.3 新增数据权限页面：角色选择、资源动作选择、策略列表、维度范围配置、保存删除
- [x] 5.4 将页面接入系统管理路由组件路径，按 API 权限码控制搜索/刷新/新增/保存/删除按钮
- [x] 5.5 保持表格和表单横向滚动条在页面底部，移动端和窄屏不发生文本重叠

## 6. 验证

- [x] 6.1 运行 service-org 测试
- [x] 6.2 运行 service-auth 测试
- [x] 6.3 运行 web-antd 类型检查
- [x] 6.4 如本地服务可用，启动/复用前端开发服务并做基础页面加载检查

## 7. 数据权限运行时执行

- [x] 7.1 新增 `@RequireDataScope`、`DataScopeProvider`、`DataScopeAspect` 和 `DataScopeContextHolder`
- [x] 7.2 在 `service-auth` 实现本地 `DataScopeProvider`，复用 `DataPolicyService.resolveEffective`
- [x] 7.3 将运行时解析接口拆成独立权限码 `/api/v1/data-policies/effective:GET`
- [x] 7.4 为 `USER:QUERY` 种子内置数据策略：管理员 `ALL`、普通用户 `SELF`
- [x] 7.5 用户列表接入 `@RequireDataScope(resource = "USER", action = "QUERY")` 并按范围过滤
- [x] 7.6 解析 `OWN_ORG`、`OWN_ORG_AND_CHILDREN` 和 `ASSIGNED_ORGS` 组织范围，运行时 DTO 返回可执行组织 ID 集合
