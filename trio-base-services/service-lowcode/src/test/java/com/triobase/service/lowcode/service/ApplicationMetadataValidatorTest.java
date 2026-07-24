package com.triobase.service.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.lowcode.dto.ApplicationActionRequest;
import com.triobase.service.lowcode.dto.ApplicationPageRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicationMetadataValidatorTest {

    private final ApplicationMetadataValidator validator = new ApplicationMetadataValidator(new ObjectMapper());

    @Test
    void acceptsAllowlistedListDetailAndCreateMetadata() {
        assertDoesNotThrow(() -> validator.validateDraft(
                List.of(listPage(), detailPage(), createPage()),
                List.of(submitAction())));
    }

    @Test
    void rejectsScriptOrDynamicExecutionMetadata() {
        ApplicationPageRequest page = listPage();
        page.setMetadataJson("""
                {"columns":[{"fieldKey":"amount","label":"Amount"}],"script":"alert(1)"}
                """);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validateDraft(List.of(page), List.of(submitAction())));

        assertEquals("APPLICATION_METADATA_FORBIDDEN_FIELD", exception.getMessage());
    }

    @Test
    void rejectsUnknownFormFieldReference() {
        BizException exception = assertThrows(BizException.class,
                () -> validator.validateFieldReferences("""
                        {"type":"object","properties":{"amount":{"type":"number"}}}
                        """, List.of(listPage())));

        assertEquals("APPLICATION_FIELD_REFERENCE_INVALID", exception.getMessage());
    }

    @Test
    void rejectsWorkflowActionWithoutProcessKey() {
        ApplicationActionRequest action = submitAction();
        action.setActionType("RETRY_WORKFLOW");
        action.setProcessKey(null);

        BizException exception = assertThrows(BizException.class,
                () -> validator.validateDraft(List.of(listPage()), List.of(action)));

        assertEquals("APPLICATION_ACTION_PROCESS_KEY_REQUIRED", exception.getMessage());
    }

    @Test
    void rejectsProcessKeyOnNonWorkflowAction() {
        ApplicationActionRequest action = submitAction();
        action.setProcessKey("expense_report");

        BizException exception = assertThrows(BizException.class,
                () -> validator.validateDraft(List.of(listPage()), List.of(action)));

        assertEquals("APPLICATION_ACTION_PROCESS_KEY_UNSUPPORTED", exception.getMessage());
    }

    @Test
    void validatesListDesignerBoundsAndOperators() {
        ApplicationPageRequest page = listPage();
        page.setMetadataJson("""
                {"columns":[{"fieldKey":"amount","format":"money","width":140}],
                 "filters":[{"fieldKey":"reason","operator":"contains"}],
                 "defaultSort":{"fieldKey":"amount","direction":"DESC"},"pageSize":20}
                """);
        assertDoesNotThrow(() -> validator.validateDraft(List.of(page), List.of(submitAction())));

        page.setMetadataJson("""
                {"columns":[{"fieldKey":"amount","format":"html","width":140}]}
                """);
        BizException exception = assertThrows(BizException.class,
                () -> validator.validateDraft(List.of(page), List.of(submitAction())));
        assertEquals("APPLICATION_LIST_FORMAT_INVALID", exception.getMessage());
    }

    private ApplicationPageRequest listPage() {
        ApplicationPageRequest request = new ApplicationPageRequest();
        request.setPageType("LIST");
        request.setMetadataJson("""
                {"columns":[{"fieldKey":"amount","label":"Amount"},{"fieldKey":"reason","label":"Reason"}]}
                """);
        return request;
    }

    private ApplicationPageRequest detailPage() {
        ApplicationPageRequest request = new ApplicationPageRequest();
        request.setPageType("DETAIL");
        request.setMetadataJson("""
                {"sections":[{"title":"Basic","fields":[{"fieldKey":"amount"},{"fieldKey":"reason"}]}]}
                """);
        return request;
    }

    private ApplicationPageRequest createPage() {
        ApplicationPageRequest request = new ApplicationPageRequest();
        request.setPageType("CREATE");
        request.setMetadataJson("""
                {"sections":[{"title":"Create","fields":[{"fieldKey":"amount"},{"fieldKey":"reason"}]}]}
                """);
        return request;
    }

    private ApplicationActionRequest submitAction() {
        ApplicationActionRequest request = new ApplicationActionRequest();
        request.setActionCode("submit");
        request.setActionType("SUBMIT");
        request.setLabel("Submit");
        request.setPermissionCode("/api/v1/forms/*/submit:POST");
        request.setMetadataJson("{}");
        return request;
    }
}
