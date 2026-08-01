# Owner 字段治理接入规范

所有注册授权字段的业务资源必须由 Owner 在数据离开或进入 Owner 边界时执行字段规则。网关、前端和授权中心不得替代 Owner 修改业务 DTO。

## 强制接入顺序

1. Owner 声明 `FieldEnforcementManifest`：Owner、资源编码、字段集合、读取隐藏、读取脱敏、写入拒绝能力和覆盖边界。
2. 列表、详情、导出、事件投影、AI/Tool 读取统一获取有效字段规则，并调用 `FieldAuthorizationAdapter.filterRead`。
3. 新增、更新、导入、流程动作、AI/Tool 写入只对请求实际提交字段调用 `validateWrite`，校验完成后才能持久化。
4. Owner 发布 Manifest 后，授权资源才能把对应 enforcement flag 标记为 true。
5. 资源或字段未注册、规则缺失、远程决策失败时必须 fail closed。

## CI 门禁

- 注册字段但没有 Manifest：失败。
- Manifest Owner 与资源 Owner 不一致：失败。
- 声明可执行但缺少列表/详情/写入契约测试：失败。
- 字段规则可保存但资源未 READY：失败。
- Owner API 或 AI 工具绕过应用服务直接读写业务表：失败。

## 存量覆盖

| 资源 | Owner | 必须覆盖 |
|---|---|---|
| USER | service-auth | 列表、详情、新增、更新 |
| ORG_UNIT | service-org | 树、详情、新增、更新 |
| LOWCODE_FORM:* | service-lowcode | 实例读取、保存、提交、运行时工具 |
| CUSTOM_DOC:* | 对应业务 Owner | 读取、编辑及 Owner Action |
