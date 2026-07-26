package com.triobase.service.openapi.action;

import com.triobase.common.action.enums.ActionErrorCategory;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.enums.ActionStatus;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.owner.ActionOwnerGuardResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiActionOwnerExecutionServiceTest {

    private final OpenApiActionOwnerExecutionService service = new OpenApiActionOwnerExecutionService();

    @Test
    void runtimeBackedActionsFailClosedUntilRuntimeAdapterIsEnabled() {
        var response = service.execute(base("integration.orchestration.start"));

        assertThat(response.getOwnerService()).isEqualTo("service-openapi");
        assertThat(response.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(response.isRetryable()).isFalse();
        assertThat(response.getMessage()).isEqualTo("OPENAPI_RUNTIME_ADAPTER_NOT_ENABLED");
        assertThat(response.getErrors()).extracting("code")
                .containsExactly("OPENAPI_RUNTIME_ADAPTER_NOT_ENABLED");
        assertThat(response.getErrors()).extracting("category")
                .containsExactly(ActionErrorCategory.EXECUTION);
        assertThat(response.getData()).containsEntry("runtimeAdapterEnabled", false);
    }

    @Test
    void guardDeniesRuntimeBackedActionsWhenRuntimeAdapterIsDisabled() {
        ActionOwnerGuardResponse guard = service.guard(base("integration.callback.signal"));

        assertThat(guard.isAllowed()).isFalse();
        assertThat(guard.getGuardCode()).isEqualTo("OPENAPI_RUNTIME_ADAPTER_NOT_ENABLED");
        assertThat(guard.getErrors()).extracting("category")
                .containsExactly(ActionErrorCategory.GUARD);
    }

    @Test
    void unsupportedActionsAreRejectedAsValidationErrors() {
        var response = service.execute(base("integration.unknown"));

        assertThat(response.getStatus()).isEqualTo(ActionStatus.REJECTED);
        assertThat(response.getMessage()).isEqualTo("OPENAPI_ACTION_UNSUPPORTED");
        assertThat(response.getErrors()).extracting("category")
                .containsExactly(ActionErrorCategory.VALIDATION);
    }

    private GlobalActionRequest base(String actionType) {
        GlobalActionRequest request = new GlobalActionRequest();
        request.setActionId("act_openapi_001");
        request.setActionType(actionType);
        request.setSource(ActionSource.API);
        request.getTarget().setOwnerService("service-openapi");
        return request;
    }
}
