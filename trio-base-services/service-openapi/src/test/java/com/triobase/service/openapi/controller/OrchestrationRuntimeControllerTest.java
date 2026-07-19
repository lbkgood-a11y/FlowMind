package com.triobase.service.openapi.controller;

import com.triobase.service.openapi.action.OpenApiActionDispatchService;
import com.triobase.service.openapi.domain.enums.ExecutionState;
import com.triobase.service.openapi.dto.CancelOrchestrationRequest;
import com.triobase.service.openapi.dto.OrchestrationExecutionResponse;
import com.triobase.service.openapi.service.OrchestrationRuntimeService;
import com.triobase.service.openapi.service.RuntimeAdmissionContextResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrchestrationRuntimeControllerTest {

    private final OrchestrationRuntimeService runtimeService = mock(OrchestrationRuntimeService.class);
    private final RuntimeAdmissionContextResolver admissionContextResolver =
            mock(RuntimeAdmissionContextResolver.class);
    private final OpenApiActionDispatchService actionDispatchService =
            mock(OpenApiActionDispatchService.class);
    private final OrchestrationRuntimeController controller = new OrchestrationRuntimeController(
            runtimeService, admissionContextResolver, actionDispatchService);

    @Test
    void cancelCompatibilityEndpointDispatchesGlobalAction() {
        OrchestrationExecutionResponse dispatched = new OrchestrationExecutionResponse(
                "EXEC001", "wf-1", "run-1", ExecutionState.RUNNING, true,
                "trace-1", null);
        when(actionDispatchService.cancelOrchestration(
                "EXEC001", "client-a", "idem-cancel", "operator requested"))
                .thenReturn(dispatched);

        var response = controller.cancel(
                "EXEC001",
                "client-a",
                "idem-cancel",
                new CancelOrchestrationRequest("operator requested"));

        assertThat(response.getData()).isSameAs(dispatched);
        verify(actionDispatchService).cancelOrchestration(
                "EXEC001", "client-a", "idem-cancel", "operator requested");
        verifyNoInteractions(runtimeService);
    }
}
