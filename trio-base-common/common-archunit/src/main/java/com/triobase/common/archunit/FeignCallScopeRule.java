package com.triobase.common.archunit;

import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 铁律 1：Feign 调用范围约束。
 * Feign Client 仅允许用于只读数据查询，禁止在 Workflow 层中被直接调用。
 */
public final class FeignCallScopeRule {

    private FeignCallScopeRule() {
    }

    public static final ArchRule NO_FEIGN_IN_WORKFLOW = classes()
            .that().haveNameMatching(".*WorkflowImpl")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("org.springframework.cloud.openfeign..")
            .as("铁律 1 违反：Workflow 中禁止直接使用 Feign 调用，必须通过 Activity 封装");
}
