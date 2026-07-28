package com.triobase.service.openapi.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.interceptor.DataScopeInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class MybatisPlusConfig {

    private static final Set<String> tablesWithTenantId = ConcurrentHashMap.newKeySet();
    private static final Set<String> tablesWithoutTenantId = ConcurrentHashMap.newKeySet();

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler()));
        interceptor.addInnerInterceptor(new DataScopeInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.POSTGRE_SQL));
        return interceptor;
    }

    private TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                String tenantId = SecurityContextHolder.getTenantId();
                return tenantId != null && !tenantId.isBlank()
                        ? new StringValue(tenantId)
                        : null;
            }

            @Override
            public String getTenantIdColumn() {
                return "tenant_id";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                String tid = SecurityContextHolder.getTenantId();
                if (tid == null || tid.isBlank()) {
                    return true;
                }
                String clean = cleanTableName(tableName);
                if (tablesWithoutTenantId.contains(clean)) {
                    return true;
                }
                if (tablesWithTenantId.contains(clean)) {
                    return false;
                }
                TableInfo tableInfo = TableInfoHelper.getTableInfo(clean);
                if (tableInfo == null) {
                    tablesWithoutTenantId.add(clean);
                    return true;
                }
                boolean hasTenantId = "tenant_id".equals(tableInfo.getKeyColumn());
                if (!hasTenantId) {
                    for (TableFieldInfo field : tableInfo.getFieldList()) {
                        if ("tenant_id".equals(field.getColumn())) {
                            hasTenantId = true;
                            break;
                        }
                    }
                }
                if (hasTenantId) {
                    tablesWithTenantId.add(clean);
                    return false;
                }
                tablesWithoutTenantId.add(clean);
                return true;
            }

            private String cleanTableName(String name) {
                if (name == null) return "";
                return name.replace("\"", "").replace("`", "").toLowerCase();
            }
        };
    }
}
