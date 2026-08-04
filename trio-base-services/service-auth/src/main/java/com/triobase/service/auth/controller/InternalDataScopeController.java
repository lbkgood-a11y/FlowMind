package com.triobase.service.auth.controller;

import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.service.AuthDataScopeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/data-scopes")
@RequiredArgsConstructor
/**
 * 为 Owner 服务解析用户在特定资源与动作上的有效数据范围。
 *
 * <p>service-auth 只拥有授权事实；业务服务仍负责把返回范围应用到自己的查询。
 * 无法解析用户、组织上下文或策略时，提供方必须保持默认拒绝语义，禁止扩大为全量范围。</p>
 */
public class InternalDataScopeController {

    private final AuthDataScopeProvider dataScopeProvider;

    @GetMapping("/effective")
    /**
     * 解析授权策略，而不是直接返回业务数据。
     *
     * @param userId 被评估用户
     * @param resourceCode Owner 服务注册的资源编码
     * @param actionCode 资源上的动作编码
     * @param tenantId 可选租户；最终租户仍受内部认证上下文约束
     */
    public R<DataScope> effective(@RequestParam String userId,
                                  @RequestParam String resourceCode,
                                  @RequestParam String actionCode,
                                  @RequestParam(required = false) String tenantId) {
        return R.ok(dataScopeProvider.resolve(userId, resourceCode, actionCode, tenantId));
    }
}
