package com.triobase.service.openapi.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.triobase.common.core.context.SecurityContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class MybatisPlusConfig {

    private static final Set<String> TENANT_TABLES = Set.of(
            "oa_structure",
            "oa_mapping_set",
            "oa_value_map_set",
            "oa_connector_endpoint",
            "oa_route_definition",
            "oa_orchestration_definition",
            "oa_release_snapshot",
            "oa_idempotency_record",
            "oa_execution",
            "oa_audit_event",
            "oa_api_scope",
            "oa_api_product",
            "oa_application",
            "oa_application_client",
            "oa_asset_approval",
            "oa_product_subscription",
            "oa_traffic_policy_version",
            "oa_policy_enforcement_state"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    private TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenantId = SecurityContextHolder.getTenantId();
                return new StringValue(tenantId == null ? "__PLATFORM_CONTEXT__" : tenantId);
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return SecurityContextHolder.getTenantId() == null
                        || !TENANT_TABLES.contains(tableName.toLowerCase());
            }
        };
    }
}
