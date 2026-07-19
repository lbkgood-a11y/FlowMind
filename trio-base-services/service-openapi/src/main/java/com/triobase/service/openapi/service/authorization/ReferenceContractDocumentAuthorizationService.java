package com.triobase.service.openapi.service.authorization;

import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthzGuardResult;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ReferenceContractDocumentAuthorizationService {

    private static final String DEFAULT_TENANT_ID = "default";

    private final CustomDocumentDecisionClient decisionClient;

    public ReferenceContractAuthorizationResult authorize(
            ReferenceContractDocument document,
            String actionCode,
            Collection<String> fieldKeys) {
        AuthorizationDecisionRequest request = request(document, actionCode, fieldKeys);
        AuthorizationDecisionResponse centralDecision = decisionClient.decide(request);
        List<AuthzGuardResult> guardResults = localGuards(document, request.getActionCode());
        ReferenceContractAuthorizationResult result = new ReferenceContractAuthorizationResult();
        result.setCentralDecision(centralDecision);
        result.setGuardResults(guardResults);
        result.setAllowed(centralDecision != null && centralDecision.isAllowed()
                && guardResults.stream().noneMatch(guard -> !guard.isAllowed()));
        return result;
    }

    public void requireAllowed(ReferenceContractDocument document, String actionCode, Collection<String> fieldKeys) {
        ReferenceContractAuthorizationResult result = authorize(document, actionCode, fieldKeys);
        if (!result.isAllowed()) {
            throw new BizException(40390, "CUSTOM_DOC_AUTHZ_DECISION_DENIED");
        }
    }

    AuthorizationDecisionRequest request(
            ReferenceContractDocument document,
            String actionCode,
            Collection<String> fieldKeys) {
        String userId = SecurityContextHolder.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException(40310, "CUSTOM_DOC_LOGIN_REQUIRED");
        }
        AuthorizationDecisionRequest request = new AuthorizationDecisionRequest();
        request.setTenantId(currentTenantId());
        request.setUserId(userId);
        request.setResourceCode(ReferenceContractAuthorizationManifest.RESOURCE_CODE);
        request.setActionCode(normalize(actionCode));
        request.setOwnerService(ReferenceContractAuthorizationManifest.SERVICE_NAME);
        request.setBusinessObjectId(document != null ? document.getId() : null);
        request.setFieldKeys(fieldKeys == null ? List.of() : fieldKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList());
        request.setEnforcementMode(true);
        return request;
    }

    private List<AuthzGuardResult> localGuards(ReferenceContractDocument document, String actionCode) {
        if (document == null) {
            return List.of(denied("DOCUMENT_STATUS", "CUSTOM_DOC_NOT_FOUND", "合同不存在"));
        }
        String status = normalize(document.getStatus());
        if (List.of("EDIT", "DELETE", "SUBMIT").contains(actionCode) && "ARCHIVED".equals(status)) {
            return List.of(denied("ARCHIVED_LOCK", "CUSTOM_DOC_ARCHIVED", "已归档合同不可修改"));
        }
        if ("APPROVE".equals(actionCode)) {
            if (!"PENDING_APPROVAL".equals(status)) {
                return List.of(denied("DOCUMENT_STATUS", "CUSTOM_DOC_STATUS_DENIED", "合同状态不允许审批"));
            }
            String userId = SecurityContextHolder.getUserId();
            if (StringUtils.hasText(document.getSubmittedBy()) && document.getSubmittedBy().equals(userId)) {
                return List.of(denied("NO_SELF_APPROVAL", "SELF_APPROVAL_DENIED", "发起人不可审批自己的合同"));
            }
        }
        return List.of();
    }

    private AuthzGuardResult denied(String guardCode, String reasonCode, String reasonMessage) {
        AuthzGuardResult result = new AuthzGuardResult();
        result.setGuardCode(guardCode);
        result.setAllowed(false);
        result.setReasonCode(reasonCode);
        result.setReasonMessage(reasonMessage);
        return result;
    }

    private String currentTenantId() {
        String tenantId = SecurityContextHolder.getTenantId();
        return StringUtils.hasText(tenantId) ? tenantId : DEFAULT_TENANT_ID;
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
