package com.triobase.service.workflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.service.workflow.entity.ProcessInstance;
import com.triobase.service.workflow.entity.ProcessPackage;
import com.triobase.service.workflow.mapper.ProcessPackageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProcessBusinessAuthorizationService {

    private final ProcessPackageMapper processPackageMapper;
    private final ObjectMapper objectMapper;

    public void requireCanView(ProcessInstance instance) {
        if (instance == null) {
            throw new BizException(40400, "PROCESS_INSTANCE_NOT_FOUND");
        }
        if (!sameTenant(instance)) {
            throw new BizException(40300, "PROCESS_INSTANCE_TENANT_DENIED");
        }
        ProcessPackage pkg = processPackageMapper.selectById(instance.getProcessPackageId());
        if (pkg == null) {
            throw new BizException(40400, "PROCESS_PACKAGE_NOT_FOUND");
        }
        String viewPermission = viewPermissionCode(pkg);
        if (StringUtils.hasText(viewPermission)
                && !SecurityContextHolder.getPermissions().contains(viewPermission)) {
            throw new BizException(40300, "BUSINESS_VIEW_PERMISSION_DENIED");
        }
    }

    private boolean sameTenant(ProcessInstance instance) {
        String requestTenant = SecurityContextHolder.getTenantId();
        String instanceTenant = instance.getTenantId();
        return !StringUtils.hasText(requestTenant)
                || !StringUtils.hasText(instanceTenant)
                || requestTenant.trim().equals(instanceTenant.trim());
    }

    private String viewPermissionCode(ProcessPackage pkg) {
        if (!StringUtils.hasText(pkg.getPermissionPlanJson())) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(pkg.getPermissionPlanJson());
            return root.path("resolvedPermissions")
                    .path("view")
                    .path("permissionCode")
                    .asText(null);
        } catch (Exception e) {
            throw new BizException(50000, "INVALID_PERMISSION_PLAN_JSON");
        }
    }
}
