package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.CreatePermissionRequest;
import com.triobase.service.auth.entity.SysPermission;
import com.triobase.service.auth.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @RequirePermission("/api/v1/permissions:GET")
    public R<List<SysPermission>> list() {
        return R.ok(permissionService.list());
    }

    @GetMapping("/page")
    @RequirePermission("/api/v1/permissions:GET")
    public R<PageResult<SysPermission>> page(@RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return R.ok(permissionService.page(page, size));
    }

    @PostMapping
    @RequirePermission("/api/v1/permissions:POST")
    public R<SysPermission> create(@RequestBody CreatePermissionRequest request) {
        return R.ok(permissionService.create(request));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/permissions/*:DELETE")
    public R<String> delete(@PathVariable String id) {
        permissionService.delete(id);
        return R.ok("权限已删除");
    }
}
