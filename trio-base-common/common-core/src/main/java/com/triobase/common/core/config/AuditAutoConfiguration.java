package com.triobase.common.core.config;

import com.triobase.common.core.entity.AuditMetaObjectHandler;
import com.triobase.common.core.filter.AuditSecurityFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = {
    "com.baomidou.mybatisplus.core.handlers.MetaObjectHandler",
    "jakarta.servlet.Filter"
})
public class AuditAutoConfiguration {

    @Bean
    public AuditMetaObjectHandler auditMetaObjectHandler() {
        return new AuditMetaObjectHandler();
    }

    @Bean
    public AuditSecurityFilter auditSecurityFilter() {
        return new AuditSecurityFilter();
    }
}
