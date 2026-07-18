package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/permissions")
@RequiredArgsConstructor
public class InternalPermissionRegistryController {

    private final PermissionService permissionService;

    @GetMapping("/missing")
    public R<List<String>> missingRegisteredCodes(@RequestParam List<String> codes) {
        return R.ok(permissionService.missingRegisteredCodes(codes));
    }
}
