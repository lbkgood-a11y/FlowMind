package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data @EqualsAndHashCode(callSuper=true) @TableName("oa_api_product_access_grant")
public class ApiProductAccessGrant extends BaseEntity { private String apiProductId; private String granteeType; private String granteeId; }
