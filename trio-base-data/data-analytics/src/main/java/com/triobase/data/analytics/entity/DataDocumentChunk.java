package com.triobase.data.analytics.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_document_chunk")
public class DataDocumentChunk extends BaseEntity {
    private String documentId;
    private String collectionKey;
    private Integer chunkIndex;
    private String content;
    private String embeddingJson;
    private Integer tokenCount;
}
