# 快速开发闭环验收手册

## 目标路径

```text
表单管理 → 定义并发布主/子/孙表单
应用管理 → 选择主表单 → 设计列表 → 配置关系 → 发布
角色管理 → 授权应用 VIEW 与各表单动作
应用中心 → 填写、查询、查看主子孙数据
```

## 单表请假应用验收

1. 在“快速开发 → 表单管理”创建 `LEAVE_REQUEST`，配置字段并发布。
2. 在“快速开发 → 应用管理”新建 `LEAVE_APP`，选择已发布表单。
3. 在“列表设计”勾选展示列、筛选字段、默认排序和每页条数。
4. 保存草稿并发布。
5. 在“系统管理 → 角色管理 → 角色授权 → 功能授权”授予：
   - `/api/v1/lowcode-runtime/apps:GET`
   - `/api/v1/lowcode-runtime/apps/*:GET`
   - `/api/v1/lowcode-runtime/apps/*/instances:GET`
   - `/api/v1/lowcode-runtime/apps/*/instances/*:GET`
   - `/api/v1/lowcode-runtime/apps/*/actions/*:POST`
   - `LOWCODE_APP:LEAVE_APP` 的 `VIEW`
   - `LOWCODE_FORM:LEAVE_REQUEST` 的 `VIEW`、`CREATE`、`EDIT`、`SUBMIT`
6. 给目标用户分配该角色，重新登录后从“快速开发 → 应用中心”打开应用。
7. 验证列表列、筛选、排序、分页、新建、详情和权限限制。

## 主子孙应用验收

示例表单：

- 主表：`PURCHASE_ORDER`
- 子表：`PURCHASE_ITEM`，包含 `orderId` 外键字段
- 孙表：`PURCHASE_ITEM_DELIVERY`，包含 `itemId` 外键字段

步骤：

1. 分别发布三个表单。
2. 新建应用并选择 `PURCHASE_ORDER` 为主表单。
3. 添加关系 `ORDER_ITEMS`：主表 → 子表，父键 `id`，子外键 `orderId`，一对多。
4. 添加关系 `ITEM_DELIVERIES`：子表 → 孙表，父键 `id`，子外键 `itemId`，一对多。
5. 发布应用；验证环、自引用、第四级、未发布表单和缺失外键会被拒绝。
6. 为角色授予应用 VIEW，以及三个表单各自所需的 VIEW/CREATE 等权限。
7. 在应用中心新建一张主单、两条明细及明细下的交付计划。
8. 验证任一孙表校验失败时整张实例图不落库。
9. 打开详情，验证主、子、孙数据按关系和顺序显示。
10. 撤销某个子表 VIEW，验证该子区域和数据不再返回。

## 回归要求

- 已有无关系应用继续按单表运行。
- 费用报销兼容入口继续跳转通用运行时。
- 所有写操作继续通过 Global Action Runtime。
- 发布资源编码稳定且同步幂等。
- 未授权、未知资源、无效关系和不支持元数据均 fail-closed。
