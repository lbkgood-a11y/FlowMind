package com.triobase.service.openapi.service.authorization;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferenceContractDocumentAuthorizationServiceTest {

    private final CustomDocumentDecisionClient decisionClient = mock(CustomDocumentDecisionClient.class);
    private final ReferenceContractDocumentAuthorizationService service =
            new ReferenceContractDocumentAuthorizationService(decisionClient);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void customDocumentCanBeAuthorizedWithoutMenuPermission() {
        setUserWithoutMenuPermissions();
        when(decisionClient.decide(org.mockito.ArgumentMatchers.any())).thenReturn(allowedDecision("VIEW"));

        ReferenceContractAuthorizationResult result = service.authorize(contract("DRAFT", "user-2"), "view",
                List.of("amount", "customerName"));

        assertThat(result.isAllowed()).isTrue();
        ArgumentCaptor<AuthorizationDecisionRequest> captor =
                ArgumentCaptor.forClass(AuthorizationDecisionRequest.class);
        verify(decisionClient).decide(captor.capture());
        AuthorizationDecisionRequest request = captor.getValue();
        assertEquals("tenant-a", request.getTenantId());
        assertEquals("user-1", request.getUserId());
        assertEquals("CUSTOM_DOC:CONTRACT", request.getResourceCode());
        assertEquals("VIEW", request.getActionCode());
        assertEquals("service-openapi", request.getOwnerService());
        assertEquals("CONTRACT001", request.getBusinessObjectId());
        assertThat(request.getFieldKeys()).containsExactly("amount", "customerName");
    }

    @Test
    void selfApprovalDeniedByLocalGuardEvenWhenCentralDecisionAllows() {
        setUserWithoutMenuPermissions();
        when(decisionClient.decide(org.mockito.ArgumentMatchers.any())).thenReturn(allowedDecision("APPROVE"));

        ReferenceContractAuthorizationResult result = service.authorize(
                contract("PENDING_APPROVAL", "user-1"), "APPROVE", List.of());

        assertThat(result.isAllowed()).isFalse();
        assertEquals("NO_SELF_APPROVAL", result.getGuardResults().getFirst().getGuardCode());
        assertEquals("SELF_APPROVAL_DENIED", result.getGuardResults().getFirst().getReasonCode());
    }

    @Test
    void archivedContractEditDeniedByLocalGuard() {
        setUserWithoutMenuPermissions();
        when(decisionClient.decide(org.mockito.ArgumentMatchers.any())).thenReturn(allowedDecision("EDIT"));

        ReferenceContractAuthorizationResult result = service.authorize(contract("ARCHIVED", "user-2"),
                "EDIT", List.of("amount"));

        assertThat(result.isAllowed()).isFalse();
        assertEquals("ARCHIVED_LOCK", result.getGuardResults().getFirst().getGuardCode());
        assertEquals("CUSTOM_DOC_ARCHIVED", result.getGuardResults().getFirst().getReasonCode());
    }

    @Test
    void requireAllowedThrowsWhenCentralDecisionDenies() {
        setUserWithoutMenuPermissions();
        AuthorizationDecisionResponse decision = allowedDecision("EXPORT");
        decision.setAllowed(false);
        when(decisionClient.decide(org.mockito.ArgumentMatchers.any())).thenReturn(decision);

        BizException exception = assertThrows(BizException.class,
                () -> service.requireAllowed(contract("DRAFT", "user-2"), "EXPORT", List.of()));

        assertEquals("CUSTOM_DOC_AUTHZ_DECISION_DENIED", exception.getMessage());
    }

    private AuthorizationDecisionResponse allowedDecision(String actionCode) {
        AuthorizationDecisionResponse decision = new AuthorizationDecisionResponse();
        decision.setAllowed(true);
        decision.setTenantId("tenant-a");
        decision.setUserId("user-1");
        decision.setResourceCode("CUSTOM_DOC:CONTRACT");
        decision.setActionCode(actionCode);
        return decision;
    }

    private ReferenceContractDocument contract(String status, String submittedBy) {
        ReferenceContractDocument document = new ReferenceContractDocument();
        document.setId("CONTRACT001");
        document.setTenantId("tenant-a");
        document.setStatus(status);
        document.setSubmittedBy(submittedBy);
        return document;
    }

    private void setUserWithoutMenuPermissions() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "Alice", "tenant-a", List.of("CONTRACT_APPROVER"), List.of(),
                null, null, null));
    }
}
