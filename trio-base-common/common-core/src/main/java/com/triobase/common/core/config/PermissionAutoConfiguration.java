package com.triobase.common.core.config;

import com.triobase.common.core.aspect.PermissionAspect;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@EnableAspectJAutoProxy
public class PermissionAutoConfiguration {

    @Bean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }
}
