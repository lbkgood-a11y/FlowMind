package com.triobase.service.workflow.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.workflow.client.LowcodeFormClient;
import com.triobase.service.workflow.dto.CreateProcessPackageRequest;
import com.triobase.service.workflow.dto.ProcessPackageResponse;
import com.triobase.service.workflow.dto.UpdateProcessPackageRequest;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.config.import=",
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.temporal.test-server.enabled=true",
                "spring.temporal.start-workers=false"
        })
class ProcessPackageRepositoryIntegrationTest {

    private static final String PROCESS_JSON = """
            {
              "flow": {
                "nodes": [
                  {"id":"start","type":"START","name":"Start"},
                  {"id":"end","type":"END","name":"End"}
                ]
              }
            }
            """;

    private static final DockerImageName POSTGRES_IMAGE = DockerImageName
            .parse(System.getProperty("test.postgres.image", "postgres:15-alpine"))
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName("triobase_test")
            .withUsername("triobase")
            .withPassword("triobase");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private ProcessPackageService processPackageService;

    @Autowired
    private ProcessPackageMapper processPackageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LowcodeFormClient lowcodeFormClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM wf_task");
        jdbcTemplate.update("DELETE FROM wf_node_record");
        jdbcTemplate.update("DELETE FROM wf_process_instance");
        jdbcTemplate.update("DELETE FROM wf_process_package");
    }

    @Test
    void draftLifecycleCreatesImmutablePublishedVersionAndDerivedDraft() {
        CreateProcessPackageRequest create = new CreateProcessPackageRequest();
        create.setProcessKey("expense_test");
        create.setName("Expense Test");
        create.setProcessJson(PROCESS_JSON);

        ProcessPackageResponse versionOne = processPackageService.create(create);
        assertEquals(1, versionOne.getVersion());
        assertEquals("DRAFT", versionOne.getStatus());

        UpdateProcessPackageRequest update = new UpdateProcessPackageRequest();
        update.setName("Expense Test Updated");
        ProcessPackageResponse updated = processPackageService.update(versionOne.getId(), update);
        assertEquals("Expense Test Updated", updated.getName());

        ProcessPackageResponse published = processPackageService.publish(versionOne.getId());
        assertEquals("PUBLISHED", published.getStatus());
        assertNotNull(published.getPublishedAt());

        BizException immutable = assertThrows(BizException.class,
                () -> processPackageService.update(versionOne.getId(), update));
        assertEquals("ONLY_DRAFT_CAN_BE_MODIFIED", immutable.getMessage());

        ProcessPackageResponse versionTwo = processPackageService.createNewVersion(versionOne.getId());
        assertEquals(2, versionTwo.getVersion());
        assertEquals("DRAFT", versionTwo.getStatus());
        assertEquals(versionOne.getId(), versionTwo.getSourcePackageId());

        BizException duplicateDraft = assertThrows(BizException.class,
                () -> processPackageService.createNewVersion(versionOne.getId()));
        assertEquals("PROCESS_DRAFT_ALREADY_EXISTS", duplicateDraft.getMessage());
    }

    @Test
    void databasePreventsTwoDraftsForOneProcessKey() {
        ProcessPackage first = packageEntity("PKG_A", "same_key", 1, "DRAFT");
        processPackageMapper.insert(first);

        ProcessPackage second = packageEntity("PKG_B", "same_key", 2, "DRAFT");
        assertThrows(DuplicateKeyException.class, () -> processPackageMapper.insert(second));
    }

    @Test
    void databaseAllowsMultiplePublishedVersions() {
        processPackageMapper.insert(packageEntity("PKG_A", "same_key", 1, "PUBLISHED"));
        processPackageMapper.insert(packageEntity("PKG_B", "same_key", 2, "PUBLISHED"));

        assertEquals(2L, processPackageMapper.selectCount(null));
    }

    @Test
    void publishSnapshotsReferencedLowcodeFormInSameTransaction() {
        PublishedFormSnapshotResponse form = new PublishedFormSnapshotResponse();
        form.setFormDefinitionId("form-1");
        form.setFormKey("expense-form");
        form.setVersion(7);
        form.setSchemaJson("""
                {"type":"object","properties":{"amount":{"type":"number"}}}
                """);
        form.setUiSchemaJson("""
                {"amount":{"ui:widget":"money"}}
                """);
        when(lowcodeFormClient.getPublishedForm("form-1")).thenReturn(R.ok(form));

        CreateProcessPackageRequest create = new CreateProcessPackageRequest();
        create.setProcessKey("lowcode_snapshot");
        create.setName("Lowcode Snapshot");
        create.setFormDefinitionId("form-1");
        create.setProcessJson(PROCESS_JSON);

        ProcessPackageResponse draft = processPackageService.create(create);
        ProcessPackageResponse published = processPackageService.publish(draft.getId());

        assertEquals("PUBLISHED", published.getStatus());
        assertEquals(7, published.getFormDefinitionVersion());
        assertEquals(form.getSchemaJson(), published.getFormSchema());
        assertEquals(form.getUiSchemaJson(), published.getFormUiSchema());
    }

    @Test
    void publishedLowcodeSnapshotRemainsImmutableWhenNewFormVersionIsPublished() {
        PublishedFormSnapshotResponse versionOneForm = publishedForm(
                "form-v1",
                "expense",
                1,
                "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"}}}",
                "{\"amount\":{\"ui:widget\":\"money\"}}");
        PublishedFormSnapshotResponse versionTwoForm = publishedForm(
                "form-v2",
                "expense",
                2,
                "{\"type\":\"object\",\"properties\":{\"amount\":{\"type\":\"number\"},\"reason\":{\"type\":\"string\"}}}",
                "{\"amount\":{\"ui:widget\":\"money\"},\"reason\":{\"ui:widget\":\"textarea\"}}");
        when(lowcodeFormClient.getPublishedForm("form-v1")).thenReturn(R.ok(versionOneForm));
        when(lowcodeFormClient.getPublishedForm("form-v2")).thenReturn(R.ok(versionTwoForm));

        CreateProcessPackageRequest create = new CreateProcessPackageRequest();
        create.setProcessKey("lowcode_snapshot_immutable");
        create.setName("Lowcode Snapshot Immutable");
        create.setFormDefinitionId("form-v1");
        create.setProcessJson(PROCESS_JSON);

        ProcessPackageResponse draftOne = processPackageService.create(create);
        ProcessPackageResponse publishedOne = processPackageService.publish(draftOne.getId());

        ProcessPackageResponse draftTwo = processPackageService.createNewVersion(publishedOne.getId());
        UpdateProcessPackageRequest update = new UpdateProcessPackageRequest();
        update.setFormDefinitionId("form-v2");
        processPackageService.update(draftTwo.getId(), update);
        ProcessPackageResponse publishedTwo = processPackageService.publish(draftTwo.getId());

        ProcessPackage storedVersionOne = processPackageMapper.selectById(publishedOne.getId());
        assertEquals(1, storedVersionOne.getFormDefinitionVersion());
        assertEquals(versionOneForm.getSchemaJson(), storedVersionOne.getFormSchema());
        assertEquals(versionOneForm.getUiSchemaJson(), storedVersionOne.getFormUiSchema());
        assertEquals(2, publishedTwo.getFormDefinitionVersion());
        assertEquals(versionTwoForm.getSchemaJson(), publishedTwo.getFormSchema());
    }

    @Test
    void workflowPublicationRejectsDraftOrOfflineLowcodeFormReferences() {
        when(lowcodeFormClient.getPublishedForm("draft-form"))
                .thenReturn(R.fail(40901, "FORM_DEFINITION_NOT_PUBLISHED"));
        when(lowcodeFormClient.getPublishedForm("offline-form"))
                .thenReturn(R.fail(40901, "FORM_DEFINITION_NOT_PUBLISHED"));

        ProcessPackageResponse draftReference = createDraftWithForm(
                "reject_draft_form",
                "draft-form");
        BizException draftException = assertThrows(BizException.class,
                () -> processPackageService.publish(draftReference.getId()));
        assertEquals("FORM_DEFINITION_NOT_PUBLISHED", draftException.getMessage());
        assertEquals("DRAFT", processPackageMapper.selectById(draftReference.getId()).getStatus());

        ProcessPackageResponse offlineReference = createDraftWithForm(
                "reject_offline_form",
                "offline-form");
        BizException offlineException = assertThrows(BizException.class,
                () -> processPackageService.publish(offlineReference.getId()));
        assertEquals("FORM_DEFINITION_NOT_PUBLISHED", offlineException.getMessage());
        assertEquals("DRAFT", processPackageMapper.selectById(offlineReference.getId()).getStatus());
    }

    @Test
    void failedPublishLeavesDraftUnchanged() {
        CreateProcessPackageRequest create = new CreateProcessPackageRequest();
        create.setProcessKey("invalid_publish");
        create.setName("Invalid Publish");
        create.setProcessJson("""
                {"flow":{"nodes":[
                  {"id":"start","type":"START"},
                  {"id":"unsupported","type":"SERVICE_TASK"},
                  {"id":"end","type":"END"}
                ]}}
                """);

        ProcessPackageResponse draft = processPackageService.create(create);
        assertThrows(BizException.class, () -> processPackageService.publish(draft.getId()));

        assertEquals("DRAFT", processPackageMapper.selectById(draft.getId()).getStatus());
    }

    private ProcessPackage packageEntity(String id, String processKey, int version, String status) {
        ProcessPackage pkg = new ProcessPackage();
        pkg.setId(id + UlidGenerator.nextUlid().substring(0, 4));
        pkg.setProcessKey(processKey);
        pkg.setName("Test Process");
        pkg.setCategory("approval");
        pkg.setVersion(version);
        pkg.setStatus(status);
        pkg.setProcessJson(PROCESS_JSON);
        return pkg;
    }

    private ProcessPackageResponse createDraftWithForm(String processKey, String formDefinitionId) {
        CreateProcessPackageRequest create = new CreateProcessPackageRequest();
        create.setProcessKey(processKey);
        create.setName("Referenced Form Process");
        create.setFormDefinitionId(formDefinitionId);
        create.setProcessJson(PROCESS_JSON);
        return processPackageService.create(create);
    }

    private PublishedFormSnapshotResponse publishedForm(String id,
                                                        String formKey,
                                                        int version,
                                                        String schemaJson,
                                                        String uiSchemaJson) {
        PublishedFormSnapshotResponse form = new PublishedFormSnapshotResponse();
        form.setFormDefinitionId(id);
        form.setTenantId("TENANT_A");
        form.setFormKey(formKey);
        form.setVersion(version);
        form.setSchemaHash("sha256:" + version);
        form.setSchemaJson(schemaJson);
        form.setUiSchemaJson(uiSchemaJson);
        return form;
    }
}
