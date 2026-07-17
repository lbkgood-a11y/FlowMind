package com.triobase.service.openapi.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.id.UlidGenerator;
import com.triobase.service.openapi.domain.entity.AssetApproval;
import com.triobase.service.openapi.domain.enums.ApprovalDecision;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.infrastructure.mapper.AssetApprovalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class AssetApprovalService {
    private final AssetApprovalMapper mapper;
    private final IntegrationAuditService auditService;

    @Transactional
    public List<AssetApproval> submit(String assetType, String assetId, Environment environment, Set<String> roles) {
        if (!StringUtils.hasText(assetType) || !StringUtils.hasText(assetId) || roles == null || roles.isEmpty()) {
            throw new BizException(40050, "OPENAPI_APPROVAL_REQUEST_INVALID");
        }
        return roles.stream().sorted().map(role -> {
            AssetApproval approval = new AssetApproval();
            approval.setId(UlidGenerator.nextUlid()); approval.setTenantId(SecurityContextHolder.getTenantId());
            approval.setAssetType(assetType); approval.setAssetId(assetId); approval.setEnvironment(environment);
            approval.setApprovalRole(role); approval.setSubmittedBy(operator()); approval.setDecision(ApprovalDecision.PENDING);
            approval.setEvidence(JsonNodeFactory.instance.objectNode()); touchNew(approval); mapper.insert(approval); return approval;
        }).toList();
    }

    @Transactional
    public AssetApproval decide(String approvalId, boolean approved, JsonNode evidence) {
        AssetApproval approval = mapper.selectById(approvalId);
        if (approval == null || approval.getDecision() != ApprovalDecision.PENDING) {
            throw new BizException(40450, "OPENAPI_APPROVAL_NOT_PENDING");
        }
        if (operator().equals(approval.getSubmittedBy())) {
            throw new BizException(40950, "OPENAPI_SELF_APPROVAL_FORBIDDEN");
        }
        approval.setDecision(approved ? ApprovalDecision.APPROVED : ApprovalDecision.REJECTED);
        approval.setDecidedBy(operator()); approval.setDecidedAt(LocalDateTime.now());
        approval.setEvidence(evidence == null ? JsonNodeFactory.instance.objectNode() : evidence.deepCopy());
        approval.setUpdatedBy(operator()); approval.setUpdatedAt(LocalDateTime.now()); mapper.updateById(approval);
        auditService.success(approved ? "ASSET_APPROVED" : "ASSET_REJECTED", approval.getAssetType(),
                approval.getAssetId(), approval.getEvidence());
        return approval;
    }

    public void requireApproved(String assetType, String assetId, Set<String> requiredRoles) {
        List<AssetApproval> approvals = mapper.selectList(new LambdaQueryWrapper<AssetApproval>()
                .eq(AssetApproval::getAssetType, assetType).eq(AssetApproval::getAssetId, assetId)
                .eq(AssetApproval::getDecision, ApprovalDecision.APPROVED));
        Set<String> roles = approvals.stream().map(AssetApproval::getApprovalRole).collect(Collectors.toSet());
        long actors = approvals.stream().map(AssetApproval::getDecidedBy).filter(StringUtils::hasText).distinct().count();
        if (!roles.containsAll(requiredRoles) || (requiredRoles.size() > 1 && actors < requiredRoles.size())) {
            throw new BizException(40950, "OPENAPI_REQUIRED_APPROVALS_MISSING");
        }
    }

    private void touchNew(AssetApproval approval) {
        LocalDateTime now=LocalDateTime.now(); approval.setCreatedBy(operator()); approval.setCreatedAt(now);
        approval.setUpdatedBy(operator()); approval.setUpdatedAt(now);
    }
    private String operator(){ return StringUtils.hasText(SecurityContextHolder.getUserId())?SecurityContextHolder.getUserId():"SYSTEM"; }
}
