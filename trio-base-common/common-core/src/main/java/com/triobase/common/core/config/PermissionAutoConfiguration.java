package com.triobase.common.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.triobase.common.core.aspect.DataScopeAspect;
import com.triobase.common.core.aspect.PermissionAspect;
import com.triobase.common.core.auth.DataScopeProvider;
import com.triobase.common.core.auth.RemoteDataScopeProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@AutoConfiguration
@ConditionalOnClass(name = "org.aspectj.lang.annotation.Aspect")
@EnableAspectJAutoProxy
@EnableConfigurationProperties(InternalServiceSecurityProperties.class)
public class PermissionAutoConfiguration {

    @Bean
    public PermissionAspect permissionAspect() {
        return new PermissionAspect();
    }

    @Bean
    public DataScopeAspect dataScopeAspect(ObjectProvider<DataScopeProvider> dataScopeProvider) {
        return new DataScopeAspect(dataScopeProvider);
    }

    @Bean
    @ConditionalOnMissingBean(DataScopeProvider.class)
    public RemoteDataScopeProvider remoteDataScopeProvider(InternalServiceSecurityProperties securityProperties,
                                                            ObjectMapper objectMapper) {
        return new RemoteDataScopeProvider(securityProperties, objectMapper,
                securityProperties.getDataScope().getAuthBaseUrl(),
                securityProperties.getDataScope().getServiceName());
    }
}
