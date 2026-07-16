package com.triobase.service.openapi.domain.entity;
import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.service.openapi.domain.enums.AssetLifecycleState;
import com.triobase.service.openapi.domain.model.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_route_definition")
public class RouteDefinition extends TenantEntity {
    private String routeKey;
    private String displayName;
    private String ownerId;
    private AssetLifecycleState lifecycleState;
}
