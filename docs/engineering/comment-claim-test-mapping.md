# 关键注释承诺与测试映射

该映射用于验证注释中的安全、幂等、降级、顺序和兼容承诺。注释发生语义变化时，必须同步更新对应测试和本表；只有职责背景说明不要求单独测试。

| 承诺 | 实现位置 | 验证证据 |
|---|---|---|
| 无法解析数据范围时保持 restrictive，不能扩大读取 | `AuthDataScopeProvider`、`OrgUnitService` | `AuthDataScopeProviderTest`、`OrgUnitServiceTest` |
| 授权决策无有效授权时默认拒绝，DENY 优先 | `AuthorizationDecisionService`、`DataPolicyService` | `AuthorizationDecisionServiceTest` |
| 运行时只读取角色当前生效发布版本 | `RoleAuthorizationDataService`、`RolePageCapabilityStore` | `RoleAuthorizationDataServiceTest`、`RolePageCapabilityStoreTest` |
| 非平台管理员不能枚举或修改其他租户 | `TenantService` | `TenantServiceTest` |
| 组织主归属查询必须绑定租户 | `InternalOrgOwnershipController`、`OrgUnitService` | `OrgUnitServiceTest.resolvePrimaryOwnership*` |
| Global Action 在 Owner Guard 后执行，重复请求返回首次结果 | `OwnerHostedActionRuntime` | `OwnerHostedActionRuntimeTest`、`LowcodeOwnerHostedActionControllerTest` |
| Owner Action 执行结果写入审计且不绕过 Owner | 各 Owner Action controller/audit sink | `WorkflowOwnerHostedActionControllerTest`、`WorkflowOwnerActionAuditSinkTest`、`LowcodeOwnerActionAuditSinkTest` |
| API 网关发现明文敏感信息时失败关闭且不转发 | `DataMaskingFilter` | `DataMaskingFilterTest` |
| LLM 网关二次脱敏按长标识符优先且不返回原文 | `ai_llm_gateway.middleware.data_masking` | `tests/test_security_and_cache.py` |
| Workflow 重放保持确定性 | `ProcessWorkflowImpl` | `ProcessWorkflowTest` 中的 `WorkflowReplayer.replayWorkflowExecution` |
| Workflow 等待在 Worker 轮询重启后继续 | `ProcessWorkflowImpl.awaitRelevantCommand` | `ProcessWorkflowTest.waitingWorkflowContinuesAfterWorkerPollingRestarts` |
| Activity/业务闭环使用稳定幂等键 | `ProcessActivityImpl`、业务闭环服务 | `BusinessLaunchRuntimeServiceTest`、`LowcodeOwnerHostedActionControllerTest` |
| 权限注册中心不可用时低代码发布失败关闭 | `ApplicationReferenceValidator` | `ApplicationReferenceValidatorTest` |
| 发布事务创建授权 Outbox，并以快照同步权限资源 | `AuthorizationPublicationService`、`AuthorizationResourceSyncClient` | `ApplicationServiceTest`、`FormDefinitionServiceTest` |

## 评审要求

- 新增上述类别的注释承诺时，PR 必须在本表增加一行或引用已覆盖的参数化测试。
- 如果现有测试不能证明注释中的边界，任务不得仅靠文字说明完成。
- 删除或重命名测试时，必须同步修正映射；CI 通过路径/类名存在性检查防止悬空引用。
