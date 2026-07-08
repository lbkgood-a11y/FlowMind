package com.triobase.service.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("lc_form_instance")
public class LcFormInstance {
    @TableId
    private String id;
    private String formDefinitionId;
    private String formKey;
    private String status;
    private String dataJson;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
