# TrioBase 前端页面设计标准

> **DEPRECATED — 本文档描述的是早期 Next.js + React 架构，已于 2026-07-10 废弃。**
>
> 当前前端已迁移至 **Vue Vben Admin v5.7** (Vue 3 + Ant Design Vue 4 + Vite)。
> 实际开发规范见根目录 [CLAUDE.md](../CLAUDE.md) 中"前端开发规范"章节。
> RBAC/权限体系设计见 [rbac-design.md](rbac-design.md)。

> 版本: v1.0 (deprecated)
> 适用于: `trio-base-frontend` (Next.js 15 + React 19 + shadcn/ui + Tailwind CSS v4) — 已废弃
> 最后更新: 2026-07-09

---

## 目录

1. [设计原则](#1-设计原则)
2. [布局体系](#2-布局体系)
3. [颜色体系](#3-颜色体系)
4. [排版规范](#4-排版规范)
5. [间距与密度](#5-间距与密度)
6. [组件使用规范](#6-组件使用规范)
7. [页面模式](#7-页面模式)
8. [数据展示](#8-数据展示)
9. [表单设计](#9-表单设计)
10. [反馈与错误处理](#10-反馈与错误处理)
11. [国际化](#11-国际化)
12. [验收检查清单](#12-验收检查清单)

---

## 1. 设计原则

### 1.1 核心理念

| 原则 | 说明 |
|------|------|
| **一致性** | 所有页面共享同一个壳层布局（Shell）、组件体系和交互模式 |
| **效率优先** | B 端后台追求信息密度与操作效率，不等于"拥挤"，而是"不浪费空间" |
| **渐进呈现** | 默认只展示最核心信息，次要内容通过 Tab、展开、弹窗等次级模式承载 |
| **安全可控** | 所有 CRUD 操作明确反馈，删除/禁用等危险操作必须有确认步骤 |

### 1.2 交互模式

```
GUI（确定性操作）── 表单填写、表格浏览、权限配置、菜单导航
                         │
                   同一工作台内切换
                         │
LUI（智能助手）────── 侧栏 AI 会话面板，与 GUI 双向联动
```

- **GUI** 负责结构化、可预测的操作路径
- **LUI** 负责自然语言查询、智能分析、自动填表
- 两者共享同一套 Zustand 状态，LUI → Store → GUI 的单向数据流（铁律 11）

---

## 2. 布局体系

### 2.1 全局布局层级

```
┌──────────────────────────────────────────────────────┐
│  RootLayout                                          │
│  ├── ThemeProvider (主题/密度/圆角)                   │
│  ├── TooltipProvider                                  │
│  ├── Toaster (sonner 通知)                            │
│  └── ClientShell                                      │
│       ├── AUTH_ROUTES (/login, /register) → 空壳层    │
│       └── 其余路由 → AppShell                        │
│            ├── SidebarProvider                        │
│            │   ├── AppSidebar (左侧导航)              │
│            │   └── SidebarInset                       │
│            │       ├── Header (顶部粘性栏)            │
│            │       └── Main (内容区)                  │
│            └── QueryClientProvider (TanStack Query)   │
└──────────────────────────────────────────────────────┘
```

### 2.2 页面壳层

每个**内部页面**使用 `<AppPage>` 包裹器：

```tsx
<AppPage
  topbarActions={(
    // 可选的顶部右侧按钮组
    <Link href="/admin/roles">
      <Button variant="outline" size="sm">角色管理</Button>
    </Link>
  )}
>
  <PageHeader
    breadcrumb="Admin Console"          // 面包屑前缀（全大写，跟踪间距）
    title="用户管理"                     // 页面标题（text-2xl font-bold）
    subtitle="维护用户状态、角色分配..."  // 页面副标题（text-sm text-muted-foreground）
    actions={
      <Button size="sm">               // 页头右侧主要操作按钮
        <Plus /> 新建用户
      </Button>
    }
  />

  {/* 页面主体内容 */}
</AppPage>
```

### 2.3 布局规格

| 区域 | 尺寸 | 说明 |
|------|------|------|
| Sidebar 展开 | 16rem (256px) | `SIDEBAR_WIDTH` |
| Sidebar 折叠 | 3rem (48px) | `SIDEBAR_WIDTH_ICON` — 仅显示图标 |
| Header 高度 | 4rem (64px) | `h-16`，`sticky top-0` |
| 内容区最大宽度 | 80rem (1280px) | `@7xl/content:max-w-7xl`，路由为 `/admin/*` 时通常启用 |
| 内容区 padding | 1rem / 1.5rem | `px-4 py-6` |
| 移动端 Sidebar | 18rem | `Sheet` 抽屉覆盖 |

### 2.4 响应式断点

| 断点 | 宽度 | 行为 |
|------|------|------|
| `max-md` | < 768px | Sidebar 折叠为 Sheet 抽屉，内容占满 |
| `md` | ≥ 768px | 可选展开/折叠图标模式 |
| `lg` | ≥ 1024px | 两列网格展开 (`lg:grid-cols-[…]`) |
| `xl` | ≥ 1280px | 三列或更宽布局 |
| `@7xl/content` | ≥ 1280px | 内容区居中定宽 |

---

## 3. 颜色体系

### 3.1 语义色板

| Token | 用途 | 浅色值 | 深色值 |
|-------|------|--------|--------|
| `--background` | 页面背景 | oklch(1 0 0) | oklch(0.129 0.042 264.695) |
| `--foreground` | 主文字 | oklch(0.129 0.042 264.695) | oklch(0.984 0.003 247.858) |
| `--card` | 卡片背景 | oklch(1 0 0) | oklch(0.14 0.04 259.21) |
| `--primary` | 主色、强调 | #1677ff (品牌蓝) | #1677ff |
| `--secondary` | 次要背景 | oklch(0.968 0.007 247.896) | oklch(0.279 0.041 260.031) |
| `--muted` | 弱化背景 | oklch(0.968 0.007 247.896) | oklch(0.279 0.041 260.031) |
| `--muted-foreground` | 次要/占位文字 | oklch(0.554 0.046 257.417) | oklch(0.704 0.04 256.788) |
| `--destructive` | 危险操作 | oklch(0.577 0.245 27.325) | oklch(0.704 0.191 22.216) |
| `--border` | 边框/分割线 | oklch(0.929 0.013 255.508) | oklch(1 0 0 / 10%) |
| `--ring` | 聚焦环 | oklch(0.704 0.04 256.788) | oklch(0.551 0.027 264.364) |

### 3.2 强调色预设

用户可通过 Theme Settings 切换强调色：

| 预设 | `--primary` 浅色 | `--primary` 深色 |
|------|------------------|------------------|
| `blue` (默认) | #1677ff | #1677ff |
| `teal` | #0f766e | #14b8a6 |
| `slate` | #334155 | #cbd5e1 |
| `rose` | #e11d48 | #fb7185 |

### 3.3 状态色（StatusBadge）

| 状态 | CSS 类 | 典型场景 |
|------|--------|----------|
| `success` | `bg-success-bg` + `text-success-fg` | 已发布、启用、已完成 |
| `processing` | `bg-processing-bg` + `text-processing-fg` | 处理中、进行中 |
| `warning` | `bg-warning-bg` + `text-warning-fg` | 草稿、待审核、待处理 |
| `danger` | `bg-danger-bg` + `text-danger-fg` | 禁用、已失败、已拒绝 |

```tsx
<StatusBadge status="success" label="已发布" />
<StatusBadge status="warning" label="草稿" />
```

### 3.4 图表色板

| Token | 用途 |
|-------|------|
| `--chart-1` | 主系列色 |
| `--chart-2` | 第二系列色 |
| `--chart-3` | 第三系列色 |
| `--chart-4` | 第四系列色 |
| `--chart-5` | 第五系列色 |

---

## 4. 排版规范

### 4.1 字体栈

```css
--font-sans: Intel, -apple-system, BlinkMacSystemFont, "Segoe UI",
  Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif;
```

所有文本使用 `font-sans`，Body 默认 `text-sm`。

### 4.2 字号层级

| 层级 | Class | 使用场景 |
|------|-------|----------|
| 页面标题 | `text-2xl font-bold tracking-tight` | `<PageHeader title>` |
| 卡片标题 | `font-semibold leading-none tracking-tight` | `<CardTitle>` |
| 节标题 | `text-sm font-medium` | 表单 field label、表格列头 |
| Body | `text-sm` | 默认正文、表格内容 |
| 辅助 | `text-xs` | 副标题、日期、编码、计数 |
| 小号辅助 | `text-xs text-muted-foreground` | 提示文字、占位注释 |

### 4.3 面包屑

```tsx
<p className="text-xs font-medium uppercase tracking-[0.2em] text-muted-foreground">
  Admin Console
</p>
```

- `uppercase` + `tracking-[0.2em]` 宽间距
- 与标题之间保留 `mt-2`

---

## 5. 间距与密度

### 5.1 密度模式

用户可在 Theme Settings 中切换舒适/紧凑模式：

| 元素 | 舒适 (默认) | 紧凑 |
|------|-------------|------|
| Card header padding | `1.5rem` | `1rem` |
| Card content padding-x | `1.5rem` | `1rem` |
| Table cell padding-y | `0.75rem` | `0.55rem` |
| Tabs 高度 | 默认 | `2.125rem` |

```css
/* 紧凑模式通过 CSS 变量覆盖实现 */
[data-density="compact"] [data-slot="card-header"] { padding: 1rem; }
[data-density="compact"] th, [data-density="compact"] td { padding-top: 0.55rem; padding-bottom: 0.55rem; }
```

### 5.2 圆角预设

| 预设 | `--radius` | 典型效果 |
|------|-----------|----------|
| `sharp` | `0.35rem` | 棱角分明 |
| `medium` (默认) | `0.625rem` | 平衡 |
| `soft` | `0.95rem` | 圆润 |

派生变量：`--radius-sm` (-4px), `--radius-md` (-2px), `--radius-lg` (0), `--radius-xl` (+4px)

### 5.3 常用间距速查

| 间距 | 值 | 场景 |
|------|-----|------|
| 页面内容 padding | `px-4 py-6` | `<Main>` 组件 |
| Card 内边距 | `p-6` | `<CardContent>` |
| Card 头部 | `px-6 py-4` | Card border-bottom 头部区域 |
| 表格单元格 | `px-4 py-3` | `<Th>`, `<Td>` |
| 两列布局间距 | `gap-6` | `grid gap-6 lg:grid-cols-[…]` |
| 表单 field 间距 | `gap-2` | Label + Input 之间 |
| 表单 field 组间距 | `space-y-4` | form 内的 field 垂直间距 |
| 按钮组间距 | `gap-2` | 行内按钮之间 |

---

## 6. 组件使用规范

### 6.1 Card

两种使用方式：

**方式一：Legacy API — 简洁声明（推荐）**
```tsx
<Card title="用户列表" subtitle="全部用户数据" actions={<Button>导出</Button>}>
  {/* 内容直接作为 children */}
  <Table>…</Table>
</Card>
```

**方式二：Shadcn API — 需要嵌套结构时**
```tsx
<Card>
  <CardHeader>
    <CardTitle>用户列表</CardTitle>
    <CardDescription>全部用户数据</CardDescription>
  </CardHeader>
  <CardContent>
    <Table>…</Table>
  </CardContent>
  <CardFooter>
    <Button>导出</Button>
  </CardFooter>
</Card>
```

**约束条件：**
- 一个 Card 不应在整个页面作为独立容器超过总宽度的 70%
- Card 之间通过 `grid gap-6` 排列
- Card 不应嵌套 Card

### 6.2 Table

使用内置 `<Table>` + `<THead>` + `<Th>` + `<Tr>` + `<Td>` 组件：

```tsx
<Table>
  <THead>
    <tr>
      <Th>用户名</Th>
      <Th>邮箱</Th>
      <Th>状态</Th>
      <Th>操作</Th>
    </tr>
  </THead>
  <tbody>
    {items.map(item => (
      <Tr key={item.id}>
        <Td className="font-medium text-foreground">{item.name}</Td>
        <Td className="text-muted-foreground">{item.email}</Td>
        <Td>
          <StatusBadge status={item.status} />
        </Td>
        <Td>
          <Button variant="destructive" size="xs">删除</Button>
        </Td>
      </Tr>
    ))}
  </tbody>
</Table>
```

**样式规则：**
- `<Td>` 默认 `align-top` — 多行内容时顶部对齐
- 偶数行着色 `even:bg-muted/20`
- 悬停 `hover:bg-accent/40`
- `<Th>` 始终 `whitespace-nowrap`

### 6.3 Button

| Variant | 用途 |
|---------|------|
| `default` | 主要操作（创建、提交、发布） |
| `outline` | 次要操作（取消、返回、导航） |
| `destructive` | 危险操作（删除、禁用、撤销） |
| `ghost` | 最弱视觉权重（仅图标按钮） |
| `secondary` | 备选强调 |
| `link` | 文字链接式 |

| Size | 高度 | 用途 |
|------|------|------|
| `default` | 2rem (32px) | 默认 |
| `xs` | 1.5rem (24px) | 表格内操作、小按钮 |
| `sm` | 1.75rem (28px) | Card 内次要操作 |
| `lg` | 2.25rem (36px) | 主按钮 |
| `icon` | 2rem | 图标按钮 |

**按钮与图标组合：**
```tsx
<Button size="sm"><Plus /> 新建</Button>
<Button size="xs"><Trash2 className="size-3.5" /> 删除</Button>
```

### 6.4 Badge

| Variant | 用途 |
|---------|------|
| `default` (`primary`) | 选中状态、计数 |
| `secondary` | 标签、角色标识、轻量分类 |
| `outline` | 组织/分类标签 |
| `destructive` | 错误/告警标签 |

### 6.5 Dialog（弹窗）

用于新建/编辑等需要"临时聚焦"的场景：

```tsx
<Dialog open={open} onOpenChange={setOpen}>
  <Button onClick={() => setOpen(true)}>新建</Button>
  <DialogContent>
    <DialogHeader>
      <DialogTitle>新建用户</DialogTitle>
      <DialogDescription>创建新用户，密码需满足强度要求</DialogDescription>
    </DialogHeader>
    <form onSubmit={handleSubmit}>
      <div className="grid gap-4 py-4">
        {/* fields */}
      </div>
      <DialogFooter>
        <Button variant="outline" type="button" onClick={() => setOpen(false)}>取消</Button>
        <Button type="submit">创建</Button>
      </DialogFooter>
    </form>
  </DialogContent>
</Dialog>
```

**约束：**
- 弹窗内表单不应超过 5 个字段，超过时考虑分步或独立页面
- DialogFooter 对齐方式：`justify-end`
- "取消"按钮必须存在

### 6.6 Select（下拉选择）

```tsx
<Select value={value} onValueChange={setValue}>
  <SelectTrigger>
    <SelectValue placeholder="请选择…" />
  </SelectTrigger>
  <SelectContent>
    <SelectItem value="option1">选项一</SelectItem>
    <SelectItem value="option2">选项二</SelectItem>
  </SelectContent>
</Select>
```

### 6.7 Tabs（选项卡）

用于同一卡片内切换不同信息维度：

```tsx
<Tabs defaultValue="unit">
  <TabsList>
    <TabsTrigger value="unit">新增组织单元</TabsTrigger>
    <TabsTrigger value="members">用户组织归属</TabsTrigger>
  </TabsList>
  <TabsContent value="unit">…</TabsContent>
  <TabsContent value="members">…</TabsContent>
</Tabs>
```

---

## 7. 页面模式

### 7.1 列表页（Pagination）

```tsx
// 状态
const [page, setPage] = useState(1);
const [total, setTotal] = useState(0);
const [loading, setLoading] = useState(true);
const [error, setError] = useState("");

// 数据加载
useEffect(() => { void loadData(); }, [router, page]);

async function loadData() { … }

// 分页控件
{totalPages > 1 && (
  <div className="flex items-center justify-between border-t px-4 py-3">
    <p className="text-xs text-muted-foreground">
      共 {total} 条，第 {page}/{totalPages} 页
    </p>
    <div className="flex gap-2">
      <Button variant="outline" size="xs" disabled={page <= 1}
        onClick={() => setPage(p => Math.max(1, p - 1))}>
        <ChevronLeft /> 上一页
      </Button>
      <Button variant="outline" size="xs" disabled={page >= totalPages}
        onClick={() => setPage(p => p + 1)}>
        下一页 <ChevronRight />
      </Button>
    </div>
  </div>
)}
```

### 7.2 两栏组合页（左树 + 右详情）

```
┌─────────────────┬──────────────────────────────┐
│  卡片: 组织树     │  卡片: 详情 / 表单             │
│  ┌─────────────┐ │  ┌──────────────────────────┐ │
│  │ ▼ 技术部     │ │  │  选中节点信息             │ │
│  │   ├ 前端组   │ │  │  ┌────────────────────┐ │ │
│  │   └ 后端组   │ │  │  │ 名称 | 编码 | 路径  │ │ │
│  │  前端组      │ │  │  └────────────────────┘ │ │
│  │  (成员: 8)   │ │  │                          │ │
│  └─────────────┘ │  │  创建表单                 │ │
│                  │  │  ┌────────────────────┐ │ │
│                  │  │  │ 字段1 | 字段2 | …  │ │ │
│                  │  │  └────────────────────┘ │ │
│                  │  └──────────────────────────┘ │
└─────────────────┴──────────────────────────────┘
                    ↑ grid gap-6
```

- 左栏宽度：`xl:grid-cols-[0.38fr_0.62fr]` 或 `lg:grid-cols-[0.95fr_1.05fr]`
- 左栏选中的节点驱动右栏详情渲染
- 右栏 Tab 切换不同操作视图

### 7.3 仪表盘（Dashboard）

```
┌──────┬──────┬──────┬──────┐
│ 表单  │ 用户  │ 角色  │ 菜单  │  ← 4 张统计卡片
│ 总数  │ 总数  │ 数量  │ 权限  │    sm:grid-cols-2
│  —   │  —   │  —   │  —   │    lg:grid-cols-4
└──────┴──────┴──────┴──────┘

┌─────────────────────┬─────────┐
│                     │ 最近活动 │
│  柱状图 / 折线图     │  item 1 │
│                     │  item 2 │
│  lg:col-span-4      │  item 3 │
│                     │  lp:col-3│
└─────────────────────┴─────────┘
```

- 统计卡片使用 `CardHeader` + `CardContent` 布局
- 图表使用 Recharts `<ResponsiveContainer>`

### 7.4 独立页（登录/注册）

- 不使用 `AppShell`/`AppPage`，直接在 `ClientShell` 的 AUTH_ROUTES 分支返回
- 全屏居中布局（flex min-h-screen items-center justify-center）
- 不显示 Sidebar 和 Header

---

## 8. 数据展示

### 8.1 空状态

```tsx
<div className="py-10 text-center text-sm text-muted-foreground">
  暂无数据
</div>
```

三态覆盖：
```tsx
{loading ? (
  <div className="py-10 text-center text-sm text-muted-foreground">加载中…</div>
) : items.length === 0 ? (
  <div className="py-10 text-center text-sm text-muted-foreground">暂无数据</div>
) : (
  <Table>…</Table>
)}
```

### 8.2 搜索过滤

```tsx
// 客户端过滤（适用于小数据量）
const filtered = search
  ? items.filter(i => i.name.includes(search) || i.email.includes(search))
  : items;

// 搜索输入框
<Input
  placeholder="搜索…"
  value={search}
  onChange={(e) => setSearch(e.target.value)}
  className="pl-8 h-8"  // 左侧留出图标位置
/>
<Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
```

### 8.3 Pagination 分页

分页控件位于 Table 底部，border-t 分隔。详见 [7.1 列表页](#71-列表页pagination)

---

## 9. 表单设计

### 9.1 标准表单布局

```tsx
<form className="space-y-4">
  <div className="grid gap-2">
    <Label htmlFor="field-id">字段名</Label>
    <Input id="field-id" value={value} onChange={…} placeholder="占位文本" />
  </div>

  {/* 两列表单 */}
  <div className="grid gap-4 md:grid-cols-2">
    <div className="grid gap-2">
      <Label htmlFor="a">A</Label>
      <Input id="a" />
    </div>
    <div className="grid gap-2">
      <Label htmlFor="b">B</Label>
      <Input id="b" />
    </div>
  </div>
</form>
```

### 9.2 Textarea 风格

```tsx
<textarea
  rows={3}
  className="w-full rounded-lg border border-input bg-transparent px-3 py-2 text-sm
    outline-none focus-visible:border-ring focus-visible:ring-3
    focus-visible:ring-ring/50 placeholder:text-muted-foreground"
/>
```

### 9.3 表单 + 列表组合页

```
┌─────────────────────┬────────────────────────────┐
│  左侧：新建表单       │  右侧：数据列表            │
│                     │                            │
│  field 1            │  ┌──┬──┬──┬──┐            │
│  field 2            │  │A │B │C │操作│            │
│  field 3            │  ├──┼──┼──┼──┤            │
│                     │  │  │  │  │  │            │
│  [  创建  ]         │  │  │  │  │  │            │
│                     │  └──┴──┴──┴──┘            │
│                     │  [上一页] [下一页]          │
└─────────────────────┴────────────────────────────┘
```

- 布局：`grid gap-6 lg:grid-cols-[0.95fr_1.05fr]`
- 左侧宽度稍小于右侧（暗示"创建"操作的频率通常低于"查看"）

---

## 10. 反馈与错误处理

### 10.1 Error Banner

```tsx
{error && (
  <div className="rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive">
    {error}
  </div>
)}
```

- 置于页面 `<PageHeader>` 下方或表单内
- 使用 `border-destructive/30`、`bg-destructive/10` 配色
- 永不使用 `alert()` 或 `window.alert()`

### 10.2 操作反馈

```tsx
import { toast } from "sonner";
toast.success("创建成功");
toast.error("创建失败");
```

### 10.3 Loading Button

```tsx
<Button disabled={saving}>
  {saving ? "创建中…" : "创建"}
</Button>
```

按钮禁用态 (`disabled:opacity-50`) 已通过 CSS 内置。

### 10.4 取消/确认模式

```tsx
async function handleDelete(id: string) {
  setError("");
  try {
    await api.delete(id);
    await loadData();  // 刷新列表
  } catch (e) {
    setError(e instanceof Error ? e.message : "删除失败");
  }
}
```

- 删除操作直接触发，**无需二次确认弹窗**（B 端约定，低频且可以撤回）
- 如果业务需要确认，使用 `window.confirm()` 或 Dialog

### 10.5 状态保存指示

```tsx
const [savingId, setSavingId] = useState<string | null>(null);

<Button
  disabled={savingId === item.id}
  onClick={() => void handleAction(item)}
>
  {savingId === item.id ? "…" : "操作"}
</Button>
```

---

## 11. 国际化

### 11.1 使用方式

```tsx
const { messages } = useI18n();

// 简单字段
<Label>{messages.common.username}</Label>

// 包含参数的字符串
{messages.pages.users.total
  .replace("{total}", String(total))
  .replace("{page}", String(page))
  .replace("{pages}", String(totalPages))}
```

### 11.2 添加新 i18n 键

在 `src/lib/i18n.ts` 文件的 `zh-CN` 和 `en-US` 两个对象中**同时**添加：

```ts
// zh-CN
pages: {
  myPage: {
    title: "我的页面",
    description: "这是一个新页面",
  },
}

// en-US
pages: {
  myPage: {
    title: "My Page",
    description: "A new page",
  },
}
```

### 11.3 命名约定

| 层 | 前缀 | 示例 |
|----|------|------|
| 通用 | `common.*` | `common.username`, `common.save` |
| 侧边栏 | `sidebar.*` | `sidebar.group` |
| 顶部栏 | `topbar.*` | `topbar.signOut` |
| 认证 | `auth.*` | `auth.loginTitle` |
| 仪表盘 | `dashboard.*` | `dashboard.title` |
| 各页面 | `pages.{pageKey}.*` | `pages.users.title`, `pages.orgs.fields.code` |

---

## 12. 验收检查清单

### 每个页面必须通过

- [ ] 页面使用 `<AppPage>` 包裹（登录/注册页除外）
- [ ] 认证检查：`useEffect` 中检查 `accessToken`，无则 `router.replace("/login")`
- [ ] `<PageHeader>` 包含 breadcrumb、title、subtitle
- [ ] 三态覆盖：loading / empty / data
- [ ] 操作错误通过 `error` 状态显示 Error Banner
- [ ] 所有操作按钮的 loading 状态正确处理（disable + 文字变化）
- [ ] 删除/禁用等危险操作使用 `variant="destructive"`
- [ ] 所有字符串使用 `useI18n` 而不是硬编码
- [ ] 表单字段使用 `<Label>` + `<Input>` 配对
- [ ] 列表页包含分页（分页控件或无限滚动）
- [ ] API 调用失败时 catch error 并展示

### 新增页面时

1. 在 `src/lib/i18n.ts` 中同时添加中英文 keys
2. 新建页面文件在 `src/app/{route}/page.tsx` 或 `src/app/admin/{route}/page.tsx`
3. Sidebar 导航项更新在 `src/components/layout/AppSidebar.tsx` 的 `sidebarData.navGroups`
4. Route 注册在 `src/lib/menu-registry.ts`

### CI 检查

- ESLint：无 warning/error
- TypeScript：`npx next build` 无类型错误
- 前端铁律 9（流式渲染）/ 10（组件安全沙箱）/ 11（单向数据流）

---

## 附录 A：导航数据配置

Sidebar 导航项在 `src/components/layout/AppSidebar.tsx` 中定义：

```ts
navGroups: [
  {
    title: "General",
    items: [
      { title: "Dashboard", url: "/", icon: LayoutDashboard },
      { title: "表单管理", url: "/forms", icon: FileText },
      { title: "用户管理", url: "/admin/users", icon: Users },
      { title: "角色管理", url: "/admin/roles", icon: Shield },
      { title: "组织管理", url: "/admin/orgs", icon: Building2 },
      { title: "菜单管理", url: "/admin/menus", icon: Menu },
      { title: "企业授权", url: "/system/authz", icon: KeyRound },
    ],
  },
],
```

导航项目前引用 `APP_MENU_REGISTRY` 过滤 `key` 匹配，未来可替换为后端菜单模型。

## 附录 B：认证与路由保护

```tsx
useEffect(() => {
  const token = localStorage.getItem("accessToken");
  if (!token) {
    router.replace("/login");
    return;
  }
  void loadData();
}, [router, page]);  // 注意：page 变化时重新加载
```

- `router.replace()` 而不是 `router.push()`，避免回退到登录页
- 登录成功后的 token 写入 `localStorage`：`accessToken` + `refreshToken`
- 用户信息写入 `localStorage`：`user` (JSON)
- 退出时清除三个 key 并跳转 `/login`
