# TrioBase — AI-native 企业智能化底座

## 定位

TrioBase 是一个 AI-native 架构的企业智能化底座，解决企业业务走向智能化过程中"业务与智能割裂、难以快速集成"的痛点。

**核心特点：** 快速构建、无缝集成

**交互模式：** GUI（确定性表单/流程）+ LUI（自然语言/智能体对话）双模驱动

---

## 架构总览

整体设计为上下两层、三板块：

- **上层（轻量敏捷）：** 业务与 AI 编排微服务
- **下层（坚固高性能）：** 数据与算力基础设施

### 三大核心板块

| 板块 | 职责 | 技术倾向 |
|------|------|----------|
| 业务流程快速构建 | 低代码/工作流/领域核心(用户、权限、计费) | Java(Spring Cloud) |
| 数据驱动价值 | ETL、知识萃取与向量化、实时/离线分析 | ClickHouse/Doris、向量库、图数据库 |
| AI 赋能业务 | 意图路由、多Agent编排、LLM 抽象网关 | Python 生态(LangGraph/AutoGen) |

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

### 三板块协同流程

1. 意图路由接收用户请求，识别任务类型
2. Agent 编排服务调用工具向数据层发起查询
3. 数据层提取结构化(ClickHouse) + 非结构化(向量库)数据返回
4. Agent(大模型)交叉比对分析
5. Agent 通过工具调用触发业务层工作流(如自动创建工单)
6. 前端通过 SSE 流式展示结果

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
| 核心框架 | React 19 / Next.js 15 | AI 生态统治地位，Concurrent Rendering 处理密集流式输入 |
| LUI 智能交互 | Vercel AI SDK + Shadcn UI | `useChat`/`useCompletion` 钩子，原生 SSE 流式渲染，Tool Call 中间态可视化 |
| GUI 状态管理 | Zustand + TanStack Query | Zustand 管理 LUI/GUI 切换上下文与全局状态；TanStack Query 负责 CRUD 缓存与乐观更新 |
| 双模画布引擎 | X6 (AntV) / React Flow | 低代码工作流设计器，LUI 生成 JSON → 画布渲染可视化流程图 |

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
│ ├─ Zustand Global State (运行时上下文共享)   │
│ └─ Vercel AI SDK (流式处理与 Tool-Call 劫持) │
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

1. **第一步（核心先行）：** 业务流程基础服务（用户/权限）+ AI 侧 LLM 网关 + 基础 RAG
2. **第二步（数据补齐）：** 数据管道接入、向量与结构化混合查询
3. **第三步（全面智能体化）：** 从 RAG 问答升级为 Multi-Agent Flow，全面接管业务工作流

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
├── trio-base-frontend/                  # 前端 — Next.js 15 单项目（React 19 + TS 5.7）
│   ├── package.json                     #   next 15, react 19, zustand 5, ai 4, zod 3
│   ├── tsconfig.json                    #   TypeScript 配置（bundler 解析 + 路径别名）
│   ├── next.config.ts                   #   Next.js 配置
│   ├── .eslintrc.json                   #   ESLint（no-eval/no-new-func，铁律 10 防线）
│   └── src/
│       ├── app/
│       │   ├── layout.tsx               #   根布局
│       │   └── page.tsx                 #   双模主页面（GUI 业务区 + LUI AI 助手侧栏）
│       ├── components/
│       │   ├── Shell.tsx                #   QueryClientProvider 包裹器
│       │   └── ChatPanel.tsx            #   LUI 智能助手面板（useChat SSE 流式，铁律 9）
│       ├── stores/
│       │   └── app-store.ts             #   Zustand 全局状态（gui/lui + pendingAction，铁律 11）
│       ├── registry/
│       │   └── component-registry.ts    #   Generative UI 组件注册表 + Zod 校验（铁律 10）
│       └── lib/
│           └── utils.ts                 #   cn() 工具（clsx + tailwind-merge）
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
- 动态生成的 B 端业务组件必须在预设 Component Registry（组件注册表）中实例化，参数传递必须经过 Zod Schema 校验，防止 Prompt Injection 引发 XSS

**铁律 11：LUI 到 GUI 的单向数据流**
- 当用户通过自然语言修改界面数据时（如"帮我修改审批单金额为 5000 元"），LUI 禁止直接通过 DOM 操作修改 GUI 上的 Input 值
- 必须由 AI SDK 触发状态机动作（Action），更新 Zustand 全局状态（Store），再由 Store 驱动 GUI 视图重新渲染，确保界面变更可追溯

### CI 门禁落地方式

| 阶段 | 手段 | 拦截内容 |
|------|------|----------|
| 编码期 | ArchUnit 单测（common-archunit 包内置） | Workflow 类中出现 `java.util.UUID`、`java.io.*` 等违禁模式 |
| 编码期 | ESLint + Zod Schema Check（前端） | Generative UI 组件未注册、缺少 Schema 校验 |
| CI 门禁 | Harness Pipeline + SonarQube + Checkstyle | Activity 缺少 RetryPolicy、代码规范不合规 → 拒绝 Merge |
| CI 门禁 | 前端 Bundle Analysis + Lighthouse | Streaming 降级为阻塞请求、首屏性能不达标 |
