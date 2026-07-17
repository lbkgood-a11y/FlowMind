package com.triobase.service.openapi.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(
        packages = "com.triobase.service.openapi",
        importOptions = ImportOption.DoNotIncludeTests.class)
class OpenApiArchitectureTest {

    @org.junit.jupiter.api.Test
    void temporalWorkflowsRemainDeterministicAndFreeOfIo() {
        com.triobase.common.archunit.TemporalDeterministicRule.verify(
                "com.triobase.service.openapi.temporal");
    }

    @ArchTest
    static final ArchRule REST_CONTROLLERS_STAY_IN_CONTROLLER_PACKAGE = classes()
            .that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule APPLICATION_ENTRY_POINT_STAYS_AT_ROOT = classes()
            .that().haveSimpleName("OpenApiApplication")
            .should().resideInAPackage("com.triobase.service.openapi");
}
