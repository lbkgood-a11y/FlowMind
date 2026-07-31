package com.triobase.service.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.internal.OrgOwnershipResponse;
import com.triobase.service.lowcode.dto.OwnershipReconciliationResponse;
import com.triobase.service.lowcode.entity.LcFormInstance;
import com.triobase.service.lowcode.mapper.FormInstanceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormOwnershipReconciliationService {

    private static final int MAX_BATCH_SIZE = 500;
    private final FormInstanceMapper formInstanceMapper;
    private final OrgOwnershipClient ownershipClient;

    public long countUnresolved(String tenantId) {
        String tenant = requiredTenant(tenantId);
        return formInstanceMapper.selectCount(unresolvedQuery(tenant));
    }

    @Transactional
    public OwnershipReconciliationResponse reconcile(String tenantId, int requestedLimit) {
        String tenant = requiredTenant(tenantId);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_BATCH_SIZE));
        long unresolvedBefore = countUnresolved(tenant);
        List<LcFormInstance> records = formInstanceMapper.selectList(
                unresolvedQuery(tenant).orderByAsc(LcFormInstance::getSubmittedAt).last("LIMIT " + limit));
        LinkedHashSet<String> users = records.stream().map(LcFormInstance::getSubmittedBy)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> unresolvedUsers = new ArrayList<>();
        List<String> failedUsers = new ArrayList<>();
        int resolvedUsers = 0;
        int updatedRecords = 0;
        for (String userId : users) {
            try {
                OrgOwnershipResponse ownership = ownershipClient.primaryOwnership(tenant, userId);
                if (ownership == null || !ownership.isResolved()
                        || !StringUtils.hasText(ownership.getPrimaryOrgUnitId())) {
                    unresolvedUsers.add(userId);
                    continue;
                }
                int updated = formInstanceMapper.update(null,
                        new LambdaUpdateWrapper<LcFormInstance>()
                                .eq(LcFormInstance::getTenantId, tenant)
                                .eq(LcFormInstance::getSubmittedBy, userId)
                                .and(query -> query.isNull(LcFormInstance::getOwnerOrgId)
                                        .or().eq(LcFormInstance::getOwnerOrgProvenance, "UNRESOLVED"))
                                .set(LcFormInstance::getOwnerOrgId, ownership.getPrimaryOrgUnitId())
                                .set(LcFormInstance::getOwnerOrgProvenance, "ORG_OWNER_RECONCILIATION"));
                resolvedUsers++;
                updatedRecords += updated;
            } catch (RuntimeException exception) {
                failedUsers.add(userId);
            }
        }
        return OwnershipReconciliationResponse.builder()
                .tenantId(tenant).unresolvedBefore(unresolvedBefore)
                .scannedRecords(records.size()).resolvedUsers(resolvedUsers)
                .updatedRecords(updatedRecords).unresolvedAfter(countUnresolved(tenant))
                .unresolvedUserIds(List.copyOf(unresolvedUsers))
                .failedUserIds(List.copyOf(failedUsers)).build();
    }

    private LambdaQueryWrapper<LcFormInstance> unresolvedQuery(String tenantId) {
        return new LambdaQueryWrapper<LcFormInstance>()
                .eq(LcFormInstance::getTenantId, tenantId)
                .and(query -> query.isNull(LcFormInstance::getOwnerOrgId)
                        .or().eq(LcFormInstance::getOwnerOrgProvenance, "UNRESOLVED"));
    }

    private String requiredTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new BizException(40093, "LOWCODE_OWNERSHIP_TENANT_REQUIRED");
        }
        return tenantId.trim();
    }
}
