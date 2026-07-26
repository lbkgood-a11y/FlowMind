package com.triobase.service.apiruntime.config;

import com.triobase.common.archunit.TemporalDeterministicRule;
import com.triobase.service.apiruntime.temporal.IntegrationOrchestrationActivitiesImpl;
import com.triobase.service.apiruntime.temporal.IntegrationOrchestrationWorkflowImpl;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.spring.boot.WorkflowImpl;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiRuntimeTemporalWorkerConfigTest {

    @Test
    void temporalWorkerTaskQueuesBindToRuntimeApplicationName() {
        assertThat(IntegrationOrchestrationWorkflowImpl.class
                .getAnnotation(WorkflowImpl.class)
                .taskQueues()).containsExactly("service-api-runtime");
        assertThat(IntegrationOrchestrationActivitiesImpl.class
                .getAnnotation(ActivityImpl.class)
                .taskQueues()).containsExactly("service-api-runtime");
    }

    @Test
    void taskQueueConfigurationMustMatchRuntimeApplicationName() {
        assertThatCode(() -> new OpenApiTemporalWorkerConfig(
                "service-api-runtime", "service-api-runtime", 100, 50)
                .validateTaskQueueBinding()).doesNotThrowAnyException();
        assertThatThrownBy(() -> new OpenApiTemporalWorkerConfig(
                "service-api-runtime", "service-openapi", 100, 50)
                .validateTaskQueueBinding())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("OPENAPI_TEMPORAL_TASK_QUEUE_MUST_MATCH_APPLICATION_NAME");
    }

    @Test
    void runtimeWorkflowsRemainDeterministicAndFreeOfIo() {
        TemporalDeterministicRule.verify("com.triobase.service.apiruntime.temporal");
    }

    @Test
    void runtimeDoesNotDependOnOpenApiControlPlanePackages() {
        noClasses()
                .should().dependOnClassesThat().resideInAnyPackage("com.triobase.service.openapi..")
                .check(new ClassFileImporter().importPackages("com.triobase.service.apiruntime"));
    }

    @Test
    void sharedRuntimePrimitivesStayInOpenApiCommon() {
        Set<String> forbiddenLocalCopies = Set.of(
                "JsonPayloadValidator",
                "JsonTreeAccess",
                "MappingSecurityValidator",
                "MappingOperation",
                "MappingRuleRequest",
                "TransformationResult",
                "SensitiveDataRedactor",
                "GatewayTrustVerifier",
                "CredentialMaterial",
                "CredentialProvider",
                "OAuth2TokenProvider",
                "OutboundAuthentication",
                "OutboundAuthenticationResolver"
        );
        assertThat(new ClassFileImporter()
                .importPackages("com.triobase.service.apiruntime")
                .stream()
                .map(JavaClass::getSimpleName))
                .doesNotContainAnyElementsOf(forbiddenLocalCopies);
    }
}
