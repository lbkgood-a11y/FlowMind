package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.auth.service.AuthorizationCodeRegistryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/authz/codes")
@RequiredArgsConstructor
/**
 * 向受信任的内部服务提供权限编码注册状态查询。
 *
 * <p>该接口只判断编码是否由 service-auth 注册，不授予权限，也不替代运行时鉴权。
 * 调用方仍必须携带内部服务身份，并按租户边界处理查询结果。</p>
 */
public class InternalAuthorizationCodeController {

    private final AuthorizationCodeRegistryService codeRegistryService;

    @GetMapping("/missing")
    /**
     * 返回指定租户中尚未注册的权限编码，用于发布前完整性校验。
     *
     * @param tenantId 权限资源所属租户，不能从待发布编码推断
     * @param codes 待验证的权限编码集合
     * @return 未注册编码；空集合表示全部已登记，不代表调用用户拥有这些权限
     */
    public R<List<String>> missingRegisteredCodes(@RequestParam String tenantId,
                                                  @RequestParam List<String> codes) {
        return R.ok(codeRegistryService.missingRegisteredCodes(tenantId, codes));
    }
}
