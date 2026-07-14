package com.triobase.data.analytics.service;

import com.triobase.common.core.id.UlidGenerator;
import com.triobase.data.analytics.entity.DataQueryAudit;
import com.triobase.data.analytics.mapper.DataQueryAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class QueryAuditService {

    private final DataQueryAuditMapper auditMapper;

    public void record(String mode, String datasetKey, String operatorId, String operatorName,
                       long elapsedMs, int structuredCount, int semanticCount) {
        LocalDateTime now = LocalDateTime.now();
        DataQueryAudit audit = new DataQueryAudit();
        audit.setId(UlidGenerator.nextUlid());
        audit.setQueryMode(mode);
        audit.setDatasetKey(datasetKey);
        audit.setOperatorId(operatorId);
        audit.setOperatorName(operatorName);
        audit.setElapsedMs(elapsedMs);
        audit.setStructuredCount(structuredCount);
        audit.setSemanticCount(semanticCount);
        audit.setCreatedBy(defaultOperator(operatorName));
        audit.setUpdatedBy(defaultOperator(operatorName));
        audit.setCreatedAt(now);
        audit.setUpdatedAt(now);
        auditMapper.insert(audit);
    }

    private String defaultOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "SYSTEM";
    }
}
