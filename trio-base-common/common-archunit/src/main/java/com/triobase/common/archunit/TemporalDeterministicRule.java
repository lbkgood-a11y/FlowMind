package com.triobase.common.archunit;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import java.util.Set;

/**
 * 铁律 3 & 4：Workflow 实现类中严禁使用非确定性代码和 I/O 操作。
 * Harness CI 编码期门禁 — 扫描所有 *WorkflowImpl 类，检测违禁模式直接挂单测。
 */
public class TemporalDeterministicRule {

    private static final Set<String> FORBIDDEN_PATTERNS = Set.of(
            "System.currentTimeMillis()",
            "LocalDateTime.now()",
            "UUID.randomUUID()",
            "Math.random()",
            "new Thread(",
            "CompletableFuture",
            "java.time.LocalDateTime.now",
            "java.util.UUID.randomUUID",
            "java.io.",
            "FileInputStream",
            "FileOutputStream",
            "BufferedReader",
            "FileWriter",
            "RestTemplate",
            "FeignClient",
            "JdbcTemplate",
            "MyBatis",
            "SqlSession",
            "HttpClient",
            "OkHttp",
            "RestClient"
    );

    public static void verify(String... packages) {
        JavaClasses classes = new ClassFileImporter().importPackages(packages);
        ArchRuleDefinition.classes().that(HasName.Predicates.nameMatching(".*WorkflowImpl"))
                .should(notContainForbiddenPatterns())
                .check(classes);
    }

    private static ArchCondition<JavaClass> notContainForbiddenPatterns() {
        return new ArchCondition<>("not contain forbidden non-deterministic or I/O patterns") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                String source = clazz.getSourceCodeLocation().toString();
                for (String pattern : FORBIDDEN_PATTERNS) {
                    if (source.contains(pattern)) {
                        events.add(SimpleConditionEvent.violated(clazz,
                                "铁律 3/4 违反：Workflow 类 " + clazz.getName()
                                        + " 包含了禁止的代码模式：" + pattern));
                    }
                }
            }
        };
    }
}
