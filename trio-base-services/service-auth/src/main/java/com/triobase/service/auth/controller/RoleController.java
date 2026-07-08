package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.auth.entity.SysRole;
import com.triobase.service.auth.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public R<List<SysRole>> list() {
        return R.ok(roleService.list());
    }

    @PostMapping
    public R<SysRole> create(@RequestParam String roleCode,
                             @RequestParam String roleName,
                             @RequestParam(required = false) String description,
                             @RequestBody(required = false) List<String> permissionIds) {
        return R.ok(roleService.create(roleCode, roleName, description, permissionIds));
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        roleService.delete(id);
        return R.ok("角色已删除");
    }
}
