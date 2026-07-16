package com.triobase.service.openapi.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("oa_value_map_entry")
public class ValueMapEntry {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String valueMapVersionId;
    private String canonicalValue;
    private String externalValue;
    private Integer entryOrder;
    private LocalDateTime createdAt;
}
