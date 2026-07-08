# ADR-001: Spring Cloud + Temporal 二元引擎选型

**日期:** 2026-07-08
**状态:** Accepted
**决策者:** TrioBase 架构组

## 背景

TrioBase 的核心场景需要同时解决两个不同层面的问题：
1. **微服务间通信、路由、治理** — 属于"外部"网络层面
2. **跨服务业务流程编排、重试、最终一致性** — 属于"内部"状态层面

## 决策

采用 Spring Cloud 治"外"、Temporal 治"内"的二元引擎架构。

- **Spring Cloud：** API 网关、服务注册发现、配置中心、负载均衡
- **Temporal：** 分布式 Workflow 状态编排，处理长链路事务的失败恢复与重试

## 关键设计约束

- Temporal Worker 必须嵌入 Spring Boot 微服务（铁律 5），不作为独立远程服务
- Task Queue 名称与 spring.application.name 强绑定

## 后果

- 正面：微服务职责清晰，外部通信与内部编排正交解耦
- 负面：团队需同时掌握 Spring Cloud 和 Temporal 两套范式
- 需要 ArchUnit + Checkstyle 在 CI 阶段强制检查（铁律 3/4）
