package com.triobase.service.ops.announcement.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.dto.notification.AuthorizedAudienceResolver;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 只暴露启动时注册的受众解析器，解析器缺失时失败关闭。
 *
 * <p>禁止使用通用 HTTP 客户端按请求中的 URL 动态解析受众；所有实现都必须由对应 Owner
 * 作为 Bean 注册并执行租户与数据范围过滤。</p>
 */
@Component
public class AuthorizedAudienceResolverRegistry {

    private final Map<String, AuthorizedAudienceResolver> resolvers;

    public AuthorizedAudienceResolverRegistry(Collection<AuthorizedAudienceResolver> resolvers) {
        this.resolvers = resolvers.stream().collect(Collectors.toUnmodifiableMap(
                AuthorizedAudienceResolver::resolverKey, Function.identity()));
    }

    public AuthorizedAudienceResolver require(String key) {
        AuthorizedAudienceResolver resolver = resolvers.get(key);
        if (resolver == null) {
            throw new BizException(45210, "ANNOUNCEMENT_AUDIENCE_RESOLVER_NOT_REGISTERED");
        }
        return resolver;
    }

    public Set<String> registeredKeys() {
        return resolvers.keySet();
    }
}
