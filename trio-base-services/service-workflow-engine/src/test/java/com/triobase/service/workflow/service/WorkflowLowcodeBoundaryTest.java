package com.triobase.service.workflow.service;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.triobase.service.workflow.client.LowcodeFormClient;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class WorkflowLowcodeBoundaryTest {

    private static final Set<String> BUSINESS_CLOSURE_RUNTIME_TYPES = Set.of(
            "ProcessOutcomeService",
            "ClosureEffectExecutionService",
            "ClosureEffectOperationService",
            "ClosureOutboxDispatcher",
            "ProcessClosureQueryService");

    @Test
    void businessClosureRuntimeDoesNotCallLowcodeClientForSideEffects() {
        JavaClasses classes = new ClassFileImporter()
                .importPackages("com.triobase.service.workflow");

        noClasses()
                .that(businessClosureRuntimeTypes())
                .should().dependOnClassesThat().areAssignableTo(LowcodeFormClient.class)
                .as("Business closure side effects remain owned by workflow-engine executors, "
                        + "not lowcode service calls")
                .check(classes);
    }

    private DescribedPredicate<JavaClass> businessClosureRuntimeTypes() {
        return new DescribedPredicate<>("business closure runtime service types") {
            @Override
            public boolean test(JavaClass input) {
                return input.getPackageName().contains(".service")
                        && BUSINESS_CLOSURE_RUNTIME_TYPES.contains(input.getSimpleName());
            }
        };
    }
}
