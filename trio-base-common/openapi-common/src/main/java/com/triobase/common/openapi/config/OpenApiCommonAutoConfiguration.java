package com.triobase.common.openapi.config;

import com.triobase.common.openapi.credential.OAuth2TokenProvider;
import com.triobase.common.openapi.credential.OutboundAuthenticationResolver;
import com.triobase.common.openapi.integration.GatewayTrustVerifier;
import com.triobase.common.openapi.integration.SensitiveDataRedactor;
import com.triobase.common.openapi.mapping.JsonPayloadValidator;
import com.triobase.common.openapi.mapping.JsonTreeAccess;
import com.triobase.common.openapi.mapping.MappingSecurityValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class OpenApiCommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    JsonPayloadValidator jsonPayloadValidator() {
        return new JsonPayloadValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    JsonTreeAccess jsonTreeAccess() {
        return new JsonTreeAccess();
    }

    @Bean
    @ConditionalOnMissingBean
    MappingSecurityValidator mappingSecurityValidator() {
        return new MappingSecurityValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    SensitiveDataRedactor sensitiveDataRedactor() {
        return new SensitiveDataRedactor();
    }

    @Bean
    @ConditionalOnMissingBean
    GatewayTrustVerifier gatewayTrustVerifier(
            @Value("${triobase.openapi.gateway-auth-secret:}") String gatewayAuthSecret) {
        return new GatewayTrustVerifier(gatewayAuthSecret);
    }

    @Bean
    @ConditionalOnBean(OAuth2TokenProvider.class)
    @ConditionalOnMissingBean
    OutboundAuthenticationResolver outboundAuthenticationResolver(OAuth2TokenProvider tokenProvider) {
        return new OutboundAuthenticationResolver(tokenProvider);
    }
}
