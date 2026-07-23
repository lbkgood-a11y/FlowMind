# 页面操作布局规范

- 查询区使用 `PageQueryActions`：`reset` 槽在前，`submit` 槽在后；查询按钮使用 primary。
- 表格工具栏使用 `PageToolbar`：批量操作进入 `start`，刷新、导入、导出、新增进入 `end`。
- 行操作使用 `TableActionBar`：查看、编辑、业务操作、危险操作依次排列。
- 弹窗、抽屉和表单底部使用 `FormFooterActions`：`cancel` 槽在前，`primary` 槽在后。
- 公共组件只负责布局，权限、事件、Loading、Popconfirm 和业务状态仍由页面负责。
