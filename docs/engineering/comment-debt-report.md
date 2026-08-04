# 注释治理债务报告

基线日期：2026-08-04  
基线文件：`cicd/comment-governance-baseline.json`

## 当前状态

| 维度 | 数量 |
|---|---:|
| 总存量问题 | 2568 |
| BLOCKER | 2268 |
| ADVISORY | 161 |
| PROHIBITED | 139 |
| Wave 1 | 653 |
| Wave 2 | 371 |
| Wave 3 | 1544 |

| 模块 | 数量 | 默认负责人 |
|---|---:|---|
| `trio-base-services` | 1212 | service-team |
| `trio-base-frontend` | 828 | frontend-team |
| `trio-base-ai` | 248 | ai-team |
| `trio-base-common` | 179 | platform-team |
| `trio-base-data` | 45 | data-team |
| `trio-base-platform` | 26 | platform-team |
| `docker` | 13 | platform-team |
| `cicd` | 12 | platform-team |
| `docs` | 4 | platform-team |
| `scripts` | 1 | platform-team |

## 执行口径

- 当前基线是启用 Java/Python/TypeScript/Vue 公共契约、SQL、配置和禁止项规则后的首份可比基线。
- 未触达的历史问题只报告；新增 BLOCKER 或 PROHIBITED 必须为零。
- 修改包含历史问题的契约或高风险决策时，必须同步补齐注释并从下一版基线移除。
- 每完成一个 Wave，重新生成基线并在本文件追加日期、总量、各 Wave 数量和净变化。
- 不以注释行数验收；每个模块需要抽查业务准确性，并验证关键承诺对应测试。

## 趋势记录

| 日期 | 总量 | Wave 1 | Wave 2 | Wave 3 | 新增阻断 | 说明 |
|---|---:|---:|---:|---:|---:|---|
| 2026-08-04 | 2588 | 669 | 371 | 1548 | 0 | 扩展规则后的首份可比基线 |
| 2026-08-04 | 2579 | 663 | 371 | 1545 | 0 | 补齐双网关与全部 Temporal Workflow/Activity 高风险注释 |
| 2026-08-04 | 2576 | 660 | 371 | 1545 | 0 | 补齐低代码发布、权限 Outbox 与同步失败关闭注释 |
| 2026-08-04 | 2568 | 653 | 371 | 1544 | 0 | 补齐授权、租户/组织和 Global Action 核心边界注释 |
