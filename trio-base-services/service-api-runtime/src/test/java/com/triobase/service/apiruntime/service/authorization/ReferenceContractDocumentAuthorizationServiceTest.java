package com.triobase.service.apiruntime.service.authorization;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceContractDocumentAuthorizationServiceTest {

    @Mock private CustomDocumentDecisionClient decisionClient;
    private ReferenceContractDocumentAuthorizationService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "U001", "alice", "tenant-a", List.of(), List.of(), null, null, null));
        service = new ReferenceContractDocumentAuthorizationService(
                decisionClient, new ReferenceContractFieldAuthorizationAdapter());
        AuthorizationDecisionResponse allow = new AuthorizationDecisionResponse();
        allow.setAllowed(true);
        when(decisionClient.decide(any())).thenReturn(allow);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void approvalGuardRejectsSelfApprovalEvenWhenCentralGrantAllows() {
        ReferenceContractDocument document = document("tenant-a", "PENDING_APPROVAL");
        document.setSubmittedBy("U001");

        ReferenceContractAuthorizationResult result = service.authorize(document, "APPROVE", List.of());

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getGuardResults()).extracting("guardCode").containsExactly("NO_SELF_APPROVAL");
    }

    @Test
    void ownerServiceRejectsCrossTenantDocumentEvenWhenCentralGrantAllows() {
        ReferenceContractDocument document = document("tenant-b", "DRAFT");

        ReferenceContractAuthorizationResult result = service.authorize(document, "EDIT", List.of());

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getGuardResults()).extracting("reasonCode")
                .containsExactly("CUSTOM_DOC_CROSS_TENANT_DENIED");
    }

    private ReferenceContractDocument document(String tenantId, String status) {
        ReferenceContractDocument document = new ReferenceContractDocument();
        document.setId("contract-1");
        document.setTenantId(tenantId);
        document.setStatus(status);
        return document;
    }
}
