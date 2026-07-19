package com.triobase.service.action.support;

import com.triobase.common.action.enums.ActionActorType;
import com.triobase.common.action.enums.ActionSource;
import com.triobase.common.action.model.ActionActor;
import com.triobase.common.action.model.ActionContext;
import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.util.ActionCorrelationIds;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.trace.TraceUtil;
import org.springframework.stereotype.Component;

@Component
public class ActionSecurityContextPropagator {

    public GlobalActionRequest propagate(GlobalActionRequest request) {
        if (request == null) {
            return null;
        }
        SecurityContextHolder.SecurityContext security = SecurityContextHolder.get();
        ActionActor actor = request.getActor() != null ? request.getActor() : new ActionActor();
        ActionContext context = request.getContext() != null ? request.getContext() : new ActionContext();

        if (request.getSource() == null) {
            request.setSource(ActionSource.API);
        }
        if (actor.getType() == null && notBlank(security != null ? security.userId() : null)) {
            actor.setType(ActionActorType.USER);
        }
        if (!notBlank(actor.getId()) && security != null) {
            actor.setId(security.userId());
        }
        if (!notBlank(actor.getDisplayName()) && security != null) {
            actor.setDisplayName(security.username());
        }
        if (!notBlank(actor.getTenantId()) && security != null) {
            actor.setTenantId(security.tenantId());
        }
        if (!notBlank(context.getTenantId())) {
            context.setTenantId(firstNonBlank(security != null ? security.tenantId() : null,
                    actor.getTenantId()));
        }
        if (!notBlank(context.getTraceId())) {
            context.setTraceId(TraceUtil.getTraceId());
        }
        if (!notBlank(context.getRequestId())) {
            context.setRequestId(ActionCorrelationIds.newRequestId());
        }
        if (!notBlank(context.getCorrelationId())) {
            context.setCorrelationId(ActionCorrelationIds.newCorrelationId());
        }
        if (security != null) {
            fillPolicyVersions(context, security);
        }
        request.setActor(actor);
        request.setContext(context);
        return request;
    }

    private void fillPolicyVersions(ActionContext context, SecurityContextHolder.SecurityContext security) {
        if (context.getAuthVersion() == null) {
            context.setAuthVersion(security.authVersion());
        }
        if (context.getRoleVersion() == null) {
            context.setRoleVersion(security.roleVersion());
        }
        if (context.getDataPolicyVersion() == null) {
            context.setDataPolicyVersion(security.dataPolicyVersion());
        }
        if (context.getAuthorizationVersion() == null) {
            context.setAuthorizationVersion(security.authorizationVersion());
        }
        if (context.getFieldPolicyVersion() == null) {
            context.setFieldPolicyVersion(security.fieldPolicyVersion());
        }
        if (context.getGuardTemplateVersion() == null) {
            context.setGuardTemplateVersion(security.guardTemplateVersion());
        }
    }

    private String firstNonBlank(String first, String fallback) {
        return notBlank(first) ? first.trim() : fallback;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
