package com.triobase.data.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_dataset_field")
public class DataDatasetField extends BaseEntity {
    private String datasetId;
    private String fieldKey;
    private String label;
    private String fieldType;
    private Integer searchable;
    private Integer sortable;
    private Integer sortOrder;
}
