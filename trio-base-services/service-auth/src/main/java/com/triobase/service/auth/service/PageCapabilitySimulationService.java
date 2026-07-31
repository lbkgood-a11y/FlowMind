package com.triobase.service.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.authz.AuthorizationDecisionRequest;
import com.triobase.common.dto.authz.AuthorizationDecisionResponse;
import com.triobase.common.dto.authz.RoleSimulationDecisionRequest;
import com.triobase.service.auth.dto.PageCapabilitySimulationRequest;
import com.triobase.service.auth.dto.PageCapabilitySimulationResponse;
import com.triobase.service.auth.entity.SysAuthPageCapability;
import com.triobase.service.auth.entity.SysAuthPageCapabilityTarget;
import com.triobase.service.auth.entity.SysAuthPageCatalog;
import com.triobase.service.auth.mapper.AuthPageCapabilityMapper;
import com.triobase.service.auth.mapper.AuthPageCapabilityTargetMapper;
import com.triobase.service.auth.mapper.AuthPageCatalogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PageCapabilitySimulationService {

    private final AuthPageCatalogMapper catalogMapper;
    private final AuthPageCapabilityMapper capabilityMapper;
    private final AuthPageCapabilityTargetMapper targetMapper;
    private final AuthorizationRegistryService registryService;
    private final AuthorizationDecisionService decisionService;

    public PageCapabilitySimulationResponse simulate(String capabilityId,
                                                     PageCapabilitySimulationRequest request) {
        if (request == null || !StringUtils.hasText(request.getMode())) {
            throw new BizException(40086, "请选择实际用户验证或当前角色模拟");
        }
        String tenantId = registryService.effectiveTenant(request.getTenantId());
        SysAuthPageCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<SysAuthPageCatalog>()
                .eq(SysAuthPageCatalog::getTenantId, tenantId)
                .eq(SysAuthPageCatalog::getLifecycleStatus, "ACTIVE")
                .orderByDesc(SysAuthPageCatalog::getCatalogVersion)
                .last("LIMIT 1"));
        if (catalog == null) {
            throw new BizException(40998, "页面功能目录尚未就绪");
        }
        SysAuthPageCapability capability = capabilityMapper.selectOne(
                new LambdaQueryWrapper<SysAuthPageCapability>()
                        .eq(SysAuthPageCapability::getTenantId, tenantId)
                        .eq(SysAuthPageCapability::getCatalogId, catalog.getId())
                        .eq(SysAuthPageCapability::getId, capabilityId)
                        .eq(SysAuthPageCapability::getReadinessStatus, "READY")
                        .eq(SysAuthPageCapability::getStatus, (short) 1));
        if (capability == null) {
            throw new BizException(40498, "所选页面功能不存在或尚未就绪");
        }
        SysAuthPageCapabilityTarget target = targetMapper.selectOne(
                new LambdaQueryWrapper<SysAuthPageCapabilityTarget>()
                        .eq(SysAuthPageCapabilityTarget::getTenantId, tenantId)
                        .eq(SysAuthPageCapabilityTarget::getCapabilityId, capabilityId)
                        .eq(SysAuthPageCapabilityTarget::getTargetKind, "GRANT")
                        .eq(SysAuthPageCapabilityTarget::getStatus, (short) 1)
                        .orderByDesc(SysAuthPageCapabilityTarget::getRequiredFlag)
                        .last("LIMIT 1"));
        if (target == null) {
            throw new BizException(40998, "所选页面功能尚未连接到可执行权限");
        }

        AuthorizationDecisionResponse decision = "ROLE".equalsIgnoreCase(request.getMode())
                ? simulateRole(tenantId, target, request)
                : simulateUser(tenantId, target, request);
        return PageCapabilitySimulationResponse.builder()
                .allowed(decision.isAllowed())
                .outcome(decision.isAllowed() ? "允许" : "拒绝")
                .evaluationMode(decision.getEvaluationMode())
                .pageName(capability.getPageName())
                .capabilityName(capability.getCapabilityName())
                .dataScopeSummary(scopeSummary(decision))
                .fieldSummaries(decision.getFieldRules().stream()
                        .map(field -> field.getFieldKey() + "：读取" + field.getReadMode()
                                + "，写入" + field.getWriteMode()).toList())
                .guardSummaries(decision.getGuardRequirements().stream()
                        .map(guard -> StringUtils.hasText(guard.getDescription())
                                ? guard.getDescription() : guard.getGuardCode()).toList())
                .reasons(decision.getReasons().stream()
                        .map(reason -> StringUtils.hasText(reason.getMessage())
                                ? reason.getMessage() : reason.getCode()).toList())
                .build();
    }

    private AuthorizationDecisionResponse simulateUser(
            String tenantId, SysAuthPageCapabilityTarget target,
            PageCapabilitySimulationRequest request) {
        if (!StringUtils.hasText(request.getUserId())) {
            throw new BizException(40084, "请选择要验证的实际用户");
        }
        AuthorizationDecisionRequest decision = new AuthorizationDecisionRequest();
        fill(decision, tenantId, target, request);
        decision.setUserId(request.getUserId().trim());
        return decisionService.decide(decision);
    }

    private AuthorizationDecisionResponse simulateRole(
            String tenantId, SysAuthPageCapabilityTarget target,
            PageCapabilitySimulationRequest request) {
        if (!StringUtils.hasText(request.getRoleId())) {
            throw new BizException(40086, "当前角色信息缺失");
        }
        RoleSimulationDecisionRequest decision = new RoleSimulationDecisionRequest();
        fill(decision, tenantId, target, request);
        decision.setRoleId(request.getRoleId().trim());
        decision.setOrganizationIds(request.getOrganizationIds());
        return decisionService.simulateRole(decision);
    }

    private void fill(AuthorizationDecisionRequest decision, String tenantId,
                      SysAuthPageCapabilityTarget target,
                      PageCapabilitySimulationRequest request) {
        decision.setTenantId(tenantId);
        decision.setResourceCode(target.getResourceCode());
        decision.setActionCode(target.getActionCode());
        decision.setBusinessObjectId(request.getBusinessObjectId());
        decision.setPreviewMode(true);
        decision.setEnforcementMode(false);
    }

    private String scopeSummary(AuthorizationDecisionResponse decision) {
        if (decision.getDataScope() == null || decision.getDataScope().getScopeTypes() == null
                || decision.getDataScope().getScopeTypes().isEmpty()) {
            return decision.isAllowed() ? "未额外限制数据范围" : "无可用数据范围";
        }
        return decision.getDataScope().getScopeTypes().stream().map(this::scopeLabel)
                .distinct().reduce((left, right) -> left + "、" + right).orElse("无可用数据范围");
    }

    private String scopeLabel(String value) {
        return switch (value) {
            case "SELF" -> "仅本人数据";
            case "OWN_ORG" -> "本部门数据";
            case "OWN_ORG_AND_CHILDREN" -> "本部门及下级部门数据";
            case "ASSIGNED_ORGS" -> "指定组织数据";
            case "ALL" -> "全部数据";
            case "NONE" -> "不能查看数据";
            default -> "受限数据";
        };
    }
}
