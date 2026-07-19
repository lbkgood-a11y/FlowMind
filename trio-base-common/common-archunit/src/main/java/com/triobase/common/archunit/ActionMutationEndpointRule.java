package com.triobase.common.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * Global Action 治理：public mutation endpoint 必须走 Action Runtime、owner adapter
 * 或被服务侧作为非运行时管理接口显式 allowlist。
 */
public final class ActionMutationEndpointRule {

    private static final Set<String> MUTATION_MAPPING_ANNOTATIONS = Set.of(
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping");

    private ActionMutationEndpointRule() {
    }

    public static ArchRule publicMutationEndpointsMustBeActionRouted(Set<String> allowedControllerSimpleNames) {
        return publicMutationEndpointsMustBeActionRouted(allowedControllerSimpleNames, Set.of());
    }

    public static ArchRule publicMutationEndpointsMustBeActionRouted(
            Set<String> allowedControllerSimpleNames,
            Set<String> allowedMethodIds) {
        return classes()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should(notDeclareUnexpectedMutationEndpoint(allowedControllerSimpleNames, allowedMethodIds))
                .as("业务变更 public controller 必须通过 Global Action 或 owner/internal adapter");
    }

    private static ArchCondition<JavaClass> notDeclareUnexpectedMutationEndpoint(
            Set<String> allowedControllerSimpleNames,
            Set<String> allowedMethodIds) {
        return new ArchCondition<>("not declare public mutation endpoints outside Action Runtime") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                if (isAllowedController(clazz, allowedControllerSimpleNames)) {
                    return;
                }
                for (JavaMethod method : clazz.getMethods()) {
                    if (isMutationEndpoint(method) && !isAllowedMethod(clazz, method, allowedMethodIds)) {
                        events.add(SimpleConditionEvent.violated(method,
                                "Global Action 违反：public mutation endpoint "
                                        + clazz.getName() + "#" + method.getName()
                                        + " 未通过 Action Runtime/owner adapter，"
                                        + "如确属管理配置接口需在服务架构测试中显式 allowlist"));
                    }
                }
            }
        };
    }

    private static boolean isAllowedMethod(JavaClass clazz, JavaMethod method, Set<String> allowedMethodIds) {
        return allowedMethodIds.contains(clazz.getSimpleName() + "#" + method.getName());
    }

    private static boolean isAllowedController(JavaClass clazz, Set<String> allowedControllerSimpleNames) {
        String packageName = clazz.getPackageName();
        return allowedControllerSimpleNames.contains(clazz.getSimpleName())
                || packageName.contains(".action")
                || packageName.contains(".internal")
                || clazz.getSimpleName().startsWith("Internal");
    }

    private static boolean isMutationEndpoint(JavaMethod method) {
        return MUTATION_MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith);
    }
}
