# TrioBase — AI 原生智能化平台

## 定位

TrioBase 是一个**流程 + 数据 + AI 三核驱动的 AI 原生智能化平台**。它不只是"知识库问答"，而是以 AI 为编排中枢，串联流程基座与数据基座，实现从业务表单构建、流程编排、数据分析挖掘到智能决策的完整闭环。

**核心能力三角：**

```
                         ┌─────────────────────────────┐
                         │       AI 驱动编排层          │
                         │  意图路由 → Agent 编排 → 执行 │
                         │  （知识库只是 AI 的其中一项   │
                         │   子能力，而非全部）          │
                         └──────────┬──────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
        ┌───────────────────┐ ┌───────────┐ ┌───────────────────┐
        │    流程基座         │ │  AI 大脑  │ │    数据基座         │
        │                    │ │           │ │                    │
        │ • 业务表单快速构建  │ │ • LLM 网关│ │ • 数据分析         │
        │ • 流程编排引擎      │ │ • 知识库  │ │ • 数据挖掘         │
        │ • 权限/租户/计费    │ │ • 智能对话│ │ • 数据可视化       │
        │ • 无代码/低代码闭环 │ │ • Agent   │ │ • 向量化检索       │
        │ • 表单→流程→发布    │ │ • 自动决策│ │ • 结构化/非结构混合│
        └──────────┬─────────┘ └─────┬─────┘ └──────────┬────────┘
                   │                 │                   │
                   └─────────────────┼───────────────────┘
                                     ▼
                         AI 自然语言 → 理解意图
                            → 调取数据基座分析结果
                            → 触发流程基座创建工单/审批
                            → 结果以 GUI + LUI 双模呈现
```

**核心特点：** 流程自闭环、数据可分析、AI 全串联

**交互模式：** GUI（确定性表单/流程）+ LUI（自然语言/智能体对话）双模驱动

---

## 架构总览

TrioBase 是**三核协同**架构，不是三个独立板块的并列，而是以 AI 为编排中枢、流程与数据为两大执行基座的有机整体。

- **AI 驱动编排层（大脑）：** 意图理解 → 任务拆解 → 调度执行 → 结果呈现，是平台的"中枢神经系统"
- **流程基座（双手）：** 从表单设计到流程发布的自闭环低代码引擎，是平台的"执行系统"
- **数据基座（记忆）：** 数据分析、挖掘、可视化的完整链路，是平台的"认知系统"

### 三核定位

| 核心 | 角色 | 职责 | 技术倾向 |
|------|------|------|----------|
| 流程基座 | 执行系统 | 低代码表单构建、工作流编排、权限/租户/计费、表单→流程自闭环 | Java (Spring Cloud + Temporal) |
| 数据基座 | 认知系统 | 数据集成 ETL、分析挖掘、可视化呈现、向量化检索、混合查询 | ClickHouse/Doris、向量库、图数据库 |
| AI 编排层 | 中枢神经 | 意图路由、多 Agent 编排、LLM 抽象网关、知识库、自动决策 | Python (LangGraph/AutoGen/LiteLLM) |

**关键关系：** AI 编排层不是与流程、数据并列的"第三个功能模块"，而是**驱动和串联**流程与数据的核心引擎。用户通过自然语言与 AI 交互，AI 理解意图后在数据层做分析、在流程层做执行，最终将结果以双模（GUI + LUI）呈现给用户。

### 双网关架构

```
                    请求进入
                       │
              ┌────────▼────────┐
              │  API 网关        │  ← Spring Cloud Gateway（流量治理）
              │  + AI 脱敏过滤   │    路由/鉴权/限流/TraceId 注入/敏感数据扫描
              └────────┬────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
    业务服务        AI 层入口     数据服务
                     │
            ┌────────▼────────┐
            │  LLM 抽象网关    │  ← Python FastAPI（模型治理）
            │                  │    模型路由(LiteLLM)/语义缓存(GPTCache)/
            │                  │    Prompt 追踪/成本监控/脱敏二次校验
            └────────┬────────┘
                     │
              外部 LLM 供应商
```

| | API 网关 (platform-gateway) | LLM 网关 (ai-llm-gateway) |
|---|---|---|
| 关注层面 | 流量治理 | 模型治理 |
| 核心技术 | Spring Cloud Gateway | Python + LiteLLM |
| 脱敏角色 | 第一道防线 — 敏感字段直接拒 | 第二道防线 — Prompt 内容再清洗 |
| 所属模块 | trio-base-platform | trio-base-ai |

### 存储层

混合存储：PostgreSQL + Vector DB + Neo4j

### 三核协同流程

以"销售主管问：'华南区 Q2 回款率下降，帮我看下原因并给客户发催款函'"为例：

```
用户自然语言请求
        │
        ▼
┌──────────────────────────────┐
│  1. AI 意图路由              │
│  识别：数据分析 + 业务执行    │
│  拆解为两个子任务             │
└──────────┬───────────────────┘
           │
   ┌───────┴───────┐
   ▼               ▼
┌──────────┐ ┌──────────────┐
│ 2a. 数据基座│ │ 2b. 流程基座  │
│ 查询华南区  │ │ 查询催款流程  │
│ Q2 回款数据 │ │ 模板和权限    │
│ 同比/环比  │ │ 校验用户身份  │
│ 挖掘下降   │ │              │
│ 根因       │ │              │
└─────┬─────┘ └──────┬───────┘
      │              │
      └──────┬───────┘
             ▼
┌──────────────────────────────┐
│  3. AI 交叉分析               │
│  大模型综合：回款数据 + 客户画像│
│  生成分析报告 + 催款函草稿     │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│  4. 流程基座执行              │
│  Agent 触发工作流：           │
│  创建催款任务 → 填入表单      │
│  → 发起审批 → 发送催款函     │
└──────────┬───────────────────┘
           │
           ▼
┌──────────────────────────────┐
│  5. 前端双模呈现              │
│  GUI：回款分析图表 + 审批表单 │
│  LUI：自然语言总结 + 操作建议 │
│  （SSE 流式渲染）             │
└──────────────────────────────┘
```

**核心要点：** AI 始终是编排中枢，数据基座提供认知能力，流程基座提供执行能力。知识库（RAG）只是 2a 步骤中"查询历史文档/客户信息"的一个子环节，不是 AI 的全部能力。

---

## 技术底座

### 后端核心二元引擎

- **Spring Cloud（治外）：** 微服务间通信、API 网关、配置中心、服务注册发现
- **Temporal（治内）：** 跨服务业务流程编排、重试、超时、最终一致性

### 关键设计约束

- **Temporal Worker 必须嵌入 Spring Boot 微服务**，不作为独立远程服务调用
- API 网关：Spring Cloud Gateway，内嵌 AI 数据脱敏过滤器
- LLM 网关：Python FastAPI + LiteLLM，负责模型抽象与 Prompt 治理

### 前端技术栈

| 维度 | 选型 | 定位 |
|------|------|------|
| 核心框架 | Vue 3 / Vite 6 | Composition API + `<script setup>` 语法，极速 HMR 开发体验 |
| UI 组件库 | Ant Design Vue 4 | 企业级后台管理台组件体系，与 Vue 深度整合 |
| GUI 状态管理 | Pinia 3 | Vue 官方状态管理，模块化 Store 设计，DevTools 支持 |
| 路由 | Vue Router 4 | 嵌套路由 + 路由守卫鉴权，与 AdminLayout 配合 |
| 双模画布引擎 | X6 (AntV) / React Flow | 低代码工作流设计器（后续集成），LUI 生成 JSON → 画布渲染可视化流程图 |

### 前端双模交互架构

```
┌──────────────────────────────────────────────┐
│               TrioBase 前端表现层             │
│   ┌──────────────────────┐┌──────────────┐   │
│   │  传统业务结构化表单   ││  智能 AI 助手  │   │
│   │     (GUI 确定性)     ││ (LUI 泛化性)  │   │
│   └──────────┬───────────┘└──────┬───────┘   │
└──────────────┼───────────────────┼────────────┘
               │                   │
               ▼                   ▼
┌──────────────────────────────────────────────┐
│               前端双模状态调度中心            │
│ ├─ Pinia Store (运行时上下文共享)            │
│ └─ Vercel AI SDK / fetch SSE (流式处理)      │
└──────────────┬───────────────────┬────────────┘
               │                   │
 HTTP / REST   │                   │ SSE (流式)
               ▼                   ▼
┌──────────────────────────────────────────────┐
│    Spring Cloud Gateway (后端网关层)          │
└──────────────────────────────────────────────┘
```

---

## 开发路线图

三核并行推进，每阶段同时覆盖流程、数据、AI 三个维度，确保 AI 从第一天起就是串联三者的编排中枢。

1. **第一阶段（地基）：**
   - 流程基座：用户/权限/租户核心领域服务 + 基础表单引擎
   - 数据基座：数据管道接入（CDC/ETL）+ 基础存储（PG + Redis）
   - AI 编排层：LLM 抽象网关（多模型路由 + 脱敏）+ 基础意图识别
2. **第二阶段（贯通）：**
   - 流程基座：低代码表单设计器 + 工作流编排引擎 + 审批流
   - 数据基座：向量化存储 + 结构化/非结构化混合查询 + 基础分析看板
   - AI 编排层：多 Agent 编排 + 工具调用（连接流程和数据 API）+ 知识库（RAG）
3. **第三阶段（智能化）：**
   - 流程基座：LUI 自然语言驱动表单/流程自动生成
   - 数据基座：智能数据挖掘 + 预测分析 + 高级可视化
   - AI 编排层：全平台 Agent 自主决策与执行闭环

---

## 工程结构

```
TrioBase/
├── pom.xml                              # Root POM — 统一依赖与插件版本管理
├── trio-base-dependencies/              # BOM — 全项目版本对齐
│
├── trio-base-common/                    # 通用基础层（所有 Java 微服务强制依赖）
│   ├── common-core/                     #   基础工具、异常枚举、Result 响应体、TraceId 工具
│   ├── common-dto/                      #   跨服务共享 DTO / 事件载荷
│   ├── common-temporal/                 #   Temporal 公共抽象
│   │                                     #     - BaseActivity（幂等基类）
│   │                                     #     - ContextPropagationInterceptor（TraceId 透传）
│   │                                     #     - RetryPolicy 预设
│   └── common-archunit/                 #   铁律检查规则包（所有服务 test scope 引用）
│
├── trio-base-platform/                  # 基础设施层
│   ├── platform-gateway/                #   Spring Cloud Gateway + AI 脱敏过滤器
│   └── platform-registry/               #   Nacos 注册中心 + 配置中心
│
├── trio-base-services/                  # 业务微服务层（Spring Boot + Temporal Worker 内嵌）
│   ├── service-auth/                    #   用户认证中心（Auth + RBAC）
│   ├── service-org/                     #   组织架构
│   ├── service-tenant/                  #   租户管理
│   ├── service-billing/                 #   计费中心
│   ├── service-lowcode/                 #   低代码引擎
│   └── service-workflow-engine/         #   工作流引擎（Temporal 核心宿主）
│
├── trio-base-data/                      # 数据层
│   ├── data-pipeline/                   #   CDC 数据集成 & ETL
│   ├── data-embedding/                  #   向量化 & 知识萃取
│   └── data-analytics/                  #   分析查询服务（ClickHouse/Doris）
│
├── trio-base-ai/                        # AI 层（Python 独立项目，Poetry 管理）
│   ├── ai-intent-router/                #   意图路由（FastAPI）
│   ├── ai-agent-orchestrator/           #   多智能体编排（LangGraph + Temporal Python SDK）
│   └── ai-llm-gateway/                  #   LLM 抽象网关（LiteLLM + GPTCache）
│
├── trio-base-frontend/                  # 前端 — Vue 3 + Vite 6 单项目（TS 5.7）
│   ├── package.json                     #   vue 3, pinia 3, vue-router 4, ant-design-vue 4
│   ├── tsconfig.json                    #   TypeScript 配置（路径别名 @/）
│   ├── vite.config.ts                   #   Vite 配置（端口 5666，API 代理到 8080）
│   ├── index.html                       #   入口 HTML
│   └── src/
│       ├── main.ts                      #   应用入口（createApp + Pinia + Router + Antd）
│       ├── App.vue                      #   根组件（RouterView）
│       ├── styles.css                   #   全局样式
│       ├── router/
│       │   └── index.ts                 #   Vue Router（路由守卫鉴权，铁律 11）
│       ├── stores/
│       │   └── auth.ts                  #   Pinia Auth Store（token/session 管理）
│       ├── layouts/
│       │   └── AdminLayout.vue          #   管理台布局（侧栏菜单 + 顶栏用户信息）
│       ├── views/
│       │   ├── login/LoginView.vue      #   登录页
│       │   ├── dashboard/DashboardView.vue  #   仪表盘
│       │   ├── forms/                   #   低代码表单（列表/创建/提交）
│       │   └── admin/                   #   后台管理（用户/角色/菜单权限）
│       └── lib/
│           ├── api.ts                   #   fetch 封装（JWT 注入 + 401 拦截）
│           ├── admin.ts                 #   管理 API（用户/角色/权限/菜单）
│           └── lowcode.ts              #   低代码 API（表单定义/实例提交）
│
├── docker/
│   ├── docker-compose.yml               #   本地全套开发环境
│   └── Dockerfile.*                     #   各服务镜像
│
├── cicd/
│   ├── harness-pipeline.yaml            #   Harness Pipeline（铁律门禁）
│   └── sonar-project.properties         #   SonarQube 质量门禁
│
└── docs/
    ├── adr/                             #   架构决策记录（ADR）
    └── api/                             #   服务间 API 契约（OpenAPI / Protobuf）
```

### 技术决策记录

| # | 决策项 | 选型 | 理由 |
|---|--------|------|------|
| 1 | 构建工具 | Maven | BOM 依赖管理成熟，SonarQube/Checkstyle 插件生态完善 |
| 2 | 注册/配置中心 | Nacos | Spring Cloud Alibaba 生态，国内企业级主流 |
| 3 | 消息/事件总线 | Apache Kafka | 高吞吐，适合 CDC 数据管道 + 领域事件广播 |
| 4 | AI 层语言 | Python + Poetry + FastAPI | AI 生态（LangGraph/AutoGen/LiteLLM）原生 Python |
| 5 | 仓库策略 | Monorepo | 现阶段统一演进，后续按需拆分独立仓库 |
| 6 | 版本基线 | Spring Boot 3.x + Spring Cloud 2024.x | 当前主流稳定版本线 |

---

## 工程治理：十一条铁律

所有接入 TrioBase 的子工程必须在 Harness CI 阶段通过静态检查、单测覆盖率和架构依赖检查。违反任何一条，Pipeline 直接 Block。

### 后端铁律（8 条）

#### 一、跨服务调用军规

**铁律 1：严禁长链路同步 Feign 调用**
- OpenFeign 仅限耗时 <500ms、无状态变更的纯粹跨服务数据查询
- 涉及跨服务状态变更或执行超 1 秒的业务链，必须转为 Temporal Workflow 编排，禁止 Feign 串联

**铁律 2：AI 网关脱敏强制性**
- 所有流向外部 LLM 供应商或内部私有化大模型的请求，必须强制经过 API 网关脱敏过滤器 + LLM 网关双重校验
- 业务微服务内部严禁直接拼装含明文敏感数据（手机号、身份证、财务秘钥）的 Prompt 直连大模型

#### 二、Temporal 确定性军规（最高代码红线）

**铁律 3：Workflow 代码绝对确定性**
- 所有 `@WorkflowInterface` 实现类方法中，严禁使用随机或时变结果代码
- 禁止清单：`System.currentTimeMillis()`、`LocalDateTime.now()`、`UUID.randomUUID()`、`Math.random()`、`new Thread()`、`CompletableFuture`
- 正确替代：`Workflow.currentTimeMillis()`、`Workflow.newRandom()`、`Workflow.sideEffect()`

**铁律 4：Workflow 内部严禁 I/O 操作**
- Workflow 方法体内严禁 JDBC/MyBatis、Feign 调用、本地文件读写、网络请求
- 所有 I/O 及业务逻辑必须封装在 `@ActivityInterface` 中，由 Workflow 通过 Stub 调用

#### 三、微服务解耦与 Worker 部署军规

**铁律 5：Worker 与微服务生命周期共生死**
- 禁止创建孤立、无业务上下文的"通用 Worker 集群"
- Temporal Worker 必须作为宿主 Spring Boot 应用的 Bean/进程组件随应用一同启动
- Worker 监听的 Task Queue 名称必须与 `spring.application.name` 强绑定或配置化关联

**铁律 6：多语言 Agent 契约标准化**
- Python 服务（AI/Agent 编排）与 Java 服务（传统业务）在 Temporal 空间协同时的参数及返回值，必须序列化为标准 JSON 字符串或 Protobuf 字节流
- 严禁使用任何语言特有的序列化格式

#### 四、数据与可观测性军规

**铁律 7：Activity 必须幂等**
- 所有 Activity（尤其涉及扣款、发信、下单）必须实现业务级幂等控制
- 手段：数据库唯一索引、Redis 分布式锁、状态机前置校验

**铁律 8：TraceId 链路全贯通**
- 网关生成的 `X-B3-TraceId` 或 W3C `traceparent` 必须通过 Temporal Header 上下文无损透传至 Activity 和下游 Python AI 引擎
- 确保 SkyWalking/Jaeger 可一览到底

### 前端铁律（3 条）

**铁律 9：流式渲染（Streaming）标准规范**
- 所有涉及 LLM/Agent 的文本、思考过程输出，严禁使用传统"等待全部执行完再返回 JSON"的 HTTP 阻塞请求
- 必须使用 Web Standard ReadableStream（前端 SSE 协议接收），处理断线重连（Keep-Alive）与流缓冲区截断

**铁律 10：Generative UI 组件安全沙箱**
- LUI 触发 Tool Call 渲染低代码表单/审批流按钮时，严禁直接使用 `eval()` 或无沙箱的动态标签注入
- 动态生成的 B 端业务组件必须在预设 Component Registry（组件注册表）中实例化，参数传递必须经过 Schema 校验，防止 Prompt Injection 引发 XSS

**铁律 11：LUI 到 GUI 的单向数据流**
- 当用户通过自然语言修改界面数据时（如"帮我修改审批单金额为 5000 元"），LUI 禁止直接通过 DOM 操作修改 GUI 上的 Input 值
- 必须由 AI SDK 触发状态机动作（Action），更新 Pinia Store 全局状态，再由 Store 驱动 GUI 视图重新渲染，确保界面变更可追溯

### CI 门禁落地方式

| 阶段 | 手段 | 拦截内容 |
|------|------|----------|
| 编码期 | ArchUnit 单测（common-archunit 包内置） | Workflow 类中出现 `java.util.UUID`、`java.io.*` 等违禁模式 |
| 编码期 | ESLint + Schema Check（前端） | Generative UI 组件未注册、缺少 Schema 校验 |
| CI 门禁 | Harness Pipeline + SonarQube + Checkstyle | Activity 缺少 RetryPolicy、代码规范不合规 → 拒绝 Merge |
| CI 门禁 | 前端 Bundle Analysis + Lighthouse | Streaming 降级为阻塞请求、首屏性能不达标 |
