package com.triobase.data.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_query_audit")
public class DataQueryAudit extends BaseEntity {
    private String queryMode;
    private String datasetKey;
    private String operatorId;
    private String operatorName;
    private Long elapsedMs;
    private Integer structuredCount;
    private Integer semanticCount;
}
