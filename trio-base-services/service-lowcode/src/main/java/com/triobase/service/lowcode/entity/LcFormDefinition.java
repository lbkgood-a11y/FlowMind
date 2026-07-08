package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lc_form_definition")
public class LcFormDefinition {
    @TableId
    private String id;
    private String formKey;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private String schemaJson;
    private String uiSchemaJson;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
