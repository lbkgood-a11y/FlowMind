package com.triobase.common.archunit;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * CI 门禁规则：Activity 必须配置 RetryPolicy（铁律 7）。
 * SonarQube 扫描阶段检查 — 所有 @ActivityInterface 的实现类必须包含 RetryOptions 引用。
 */
public final class ActivityRetryPolicyRule {

    private ActivityRetryPolicyRule() {
    }

    public static final ArchRule ACTIVITY_MUST_HAVE_RETRY = classes()
            .that().areAnnotatedWith("io.temporal.activity.ActivityInterface")
            .should().dependOnClassesThat()
            .haveNameMatching(".*RetryOptions.*")
            .as("铁律 7 违反：Activity 实现类缺少 RetryPolicy 配置，CI 门禁拒绝 Merge");
}
