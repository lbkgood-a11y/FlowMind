package com.triobase.data.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_dataset")
public class DataDataset extends BaseEntity {
    private String datasetKey;
    private String name;
    private String datasetType;
    private String ownerId;
    private String ownerName;
    private String status;
    private String backingTable;
    private String description;
}
