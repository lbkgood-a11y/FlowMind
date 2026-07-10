package com.triobase.service.auth.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.CreateRoleRequest;
import com.triobase.service.auth.dto.RoleDetailResponse;
import com.triobase.service.auth.dto.UpdateRoleRequest;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @RequirePermission("/api/v1/roles:GET")
    public R<List<SysRole>> list(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Integer status) {
        return R.ok(roleService.list(keyword, status));
    }

    @GetMapping("/page")
    @RequirePermission("/api/v1/roles:GET")
    public R<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String roleCode,
                                       @RequestParam(required = false) String roleName,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       LocalDateTime createdStart,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                       LocalDateTime createdEnd) {
        return R.ok(roleService.page(page, size, keyword, roleCode, roleName, status, createdStart, createdEnd));
    }

    @GetMapping("/exists/code")
    @RequirePermission("/api/v1/roles:GET")
    public R<Boolean> existsRoleCode(@RequestParam String roleCode,
                                     @RequestParam(required = false) String excludeId) {
        return R.ok(roleService.existsRoleCode(roleCode, excludeId));
    }

    @GetMapping("/{id}")
    @RequirePermission("/api/v1/roles:GET")
    public R<RoleDetailResponse> detail(@PathVariable String id) {
        return R.ok(roleService.findById(id));
    }

    @PostMapping
    @RequirePermission("/api/v1/roles:POST")
    public R<SysRole> create(@RequestBody CreateRoleRequest request) {
        return R.ok(roleService.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("/api/v1/roles/*:PUT")
    public R<SysRole> update(@PathVariable String id, @RequestBody UpdateRoleRequest request) {
        return R.ok(roleService.update(id, request));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("/api/v1/roles/*:PUT")
    public R<SysRole> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        return R.ok(roleService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @RequirePermission("/api/v1/roles/*:DELETE")
    public R<String> delete(@PathVariable String id) {
        roleService.delete(id);
        return R.ok("角色已删除");
    }
}
