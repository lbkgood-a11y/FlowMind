package com.triobase.data.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_document")
public class DataDocument extends BaseEntity {
    private String datasetId;
    private String collectionKey;
    private String sourceKey;
    private String title;
    private String status;
    private Integer chunkCount;
}
