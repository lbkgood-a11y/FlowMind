package com.triobase.service.org.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.OrgOwnershipResponse;
import com.triobase.service.org.service.OrgUnitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 为受信任内部服务解析用户的主组织事实。
 *
 * <p>tenantId 必须参与查询，防止相同 userId 在不同租户间串用组织上下文。该接口不返回授权
 * 结论；service-auth 使用组织事实解析策略后，业务 Owner 仍需执行最终数据过滤。</p>
 */
@RestController
@RequestMapping("/internal/v1/org-ownership")
@RequiredArgsConstructor
public class InternalOrgOwnershipController {

    private final OrgUnitService orgUnitService;

    @GetMapping("/users/{userId}/primary")
    public R<OrgOwnershipResponse> primaryOwnership(@PathVariable String userId,
                                                    @RequestParam String tenantId) {
        return R.ok(orgUnitService.resolvePrimaryOwnership(tenantId, userId));
    }
}
