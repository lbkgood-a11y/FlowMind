# Vben ERP 高密度页面布局规范

> 适用于 `trio-base-frontend/apps/web-antd`。后续业务页面默认遵循本规范，目标是企业级 ERP/财务系统的信息密度与操作效率。

## 1. 基本原则

| 原则 | 执行要求 |
| --- | --- |
| 一屏优先 | 页面主体使用 `Page auto-content-height`，内部用 `h-full/flex/min-height:0/overflow:hidden` 控制滚动。 |
| 少卡片 | 查询区、工具栏、表格不要多层 Card 嵌套；只在重复对象、弹窗、详情面板中使用 Card。 |
| 紧凑密度 | 外层间距默认 `p-2/gap-2`，表格行高默认 32-36px，表单项间距默认 8px。 |
| 轻边界 | 内容区外边距 8px，模块间距 8px，模块边框 1px，圆角 4px；用浅灰底区分模块，不使用厚边框或大留白。 |
| 上下文驱动 | 树、主表、Tab 只负责切换上下文；下级表格通过状态和 API reload 联动。 |
| 编辑外置 | 新增/编辑使用 Drawer 或 Modal，不在列表中展开大表单。复杂详情才进入独立详情页。 |

## 2. 统一样式入口

所有管理页根容器挂载 `erp-compact-page`：

```vue
<Page auto-content-height>
  <div class="erp-compact-page xxx-page">
    ...
  </div>
</Page>
```

该类统一收紧 Ant Design Vue 的 Table、Tabs、Pagination、FormItem、Tag 等基础密度。页面局部 CSS 只处理业务布局，不重复写通用单元格 padding。

## 3. 工具栏图标

管理页工具栏图标必须从 `#/constants/erp-toolbar` 的 `ERP_TOOLBAR_ICONS` 引入，禁止在页面内散落 `lucide:*` 字面量。

| 操作 | 图标键 |
| --- | --- |
| 查询/收起查询后快捷查询 | `search` |
| 刷新 | `refresh` |
| 列设置 | `columnSettings` |
| 全屏 | `fullscreen` |
| 还原 | `fullscreenExit` |
| 查询区展开 | `expand` |
| 查询区收起 | `collapse` |
| 树/表展开全部 | `expandAll` |
| 重置视图 | `reset` |
| 列拖拽 | `drag` |
| 固定列 | `pin` |

## 4. 页面类型

| 类型 | 默认布局 | 组件组合 |
| --- | --- | --- |
| 单表 | 查询工具区 + 表格 + 分页 + 编辑弹层 | `Page` + `VbenVxeGrid` 优先；存量可用 `Table` + `Pagination` |
| 多表 | 主表控制上下文，下方 Tabs 或左右子孙表 | `Table/VbenVxeGrid` + `Tabs` + `Drawer/Modal` |
| 树表 | 左树右表，树节点作为查询上下文 | `ResizablePanelGroup` + `VbenTree/Tree` + `Table/VbenVxeGrid` |
| 树形表格 | 单表承载层级数据 | `Table` tree data 或 `VxeGrid` tree config |

## 5. 单表操作逻辑

```text
查询条件 -> reload table
新增/编辑 -> Drawer/Modal -> 保存成功 -> reload current page
删除/启停 -> confirm -> mutate -> reload current page
详情 -> Drawer
```

默认要求：

- 查询字段超过 4 个时折叠非核心条件。
- 表格显式设置 `size="small"`，VxeGrid 设置 `size: 'small'` 与 `rowConfig.height = 36`。
- 独立分页显式设置 `size="small"`；表格内分页配置 `size: 'small'`。
- 行操作按钮使用 `type="link" size="small"`，同一行超过 3 个操作时收进“更多”。

## 6. 多表联动逻辑

主子表：

```text
Master row-click
  -> selectedMasterId
  -> clear selectedChildId
  -> reload child table
```

主子孙表：

```text
Master row-click -> reload child table
Child row-click  -> reload grandchild table
Tab active       -> lazy load non-core detail data
```

空态必须明确：

- 未选主表：提示“请选择主表记录”。
- 已选主表但无子表：提示“暂无明细”。
- 未激活 Tab：不预加载大数据。

## 7. 树表联动逻辑

```ts
function onTreeSelect(node) {
  selectedTreeId.value = node.id;
  selectedRows.value = [];
  gridApi.reload({ ...queryForm, treeId: node.id });
}
```

默认要求：

- 左树默认宽度 260px，范围 220-360px。
- 树顶部放搜索和根节点操作，节点级操作放右键菜单或更多按钮。
- 右侧新增必须继承当前树节点上下文；无节点时按业务决定禁用新增或加载全部。

## 8. 尺寸基线

| 元素 | 基线 |
| --- | --- |
| 页面间距 | `p-2` / `gap-2` |
| 内容边界 | 外层 8px / 模块间 8px / `1px` border / `4px` radius |
| 查询区 | 单行优先，`gap: 8px` |
| 表格行高 | 32-36px |
| 表头高度 | 32-36px |
| 横向滚动条 | 表格内容区撑满面板，横向滚动条固定在表格面板底部，不随少量数据停在中间。 |
| 左树宽度 | 260px |
| Drawer 宽度 | 普通 640-760px，复杂 860-960px |
| Modal 宽度 | 少量字段 560-720px，复杂字段 760-860px |
| 标题字号 | `text-sm font-semibold` |

## 9. 后续验收清单

- 页面根容器是否挂载 `erp-compact-page`。
- 是否避免 Card 套 Card。
- 表格是否显式 small/mini，行高是否在 36px 左右。
- 查询项是否单行优先，非核心项是否折叠。
- 工具栏图标是否统一引用 `ERP_TOOLBAR_ICONS`。
- 多表/树表是否通过状态驱动 reload，而不是 DOM 操作。
- Drawer/Modal 保存后是否只刷新相关表格，不整页刷新。
