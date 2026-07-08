package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lc_form_field_definition")
public class LcFormFieldDefinition {
    @TableId
    private String id;
    private String formDefinitionId;
    private String fieldKey;
    private String label;
    private String fieldType;
    private Integer requiredFlag;
    private String defaultValue;
    private String placeholder;
    private String optionsJson;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
