package com.triobase.service.auth.controller;

import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<PageResult<UserInfoPayload>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return R.ok(userService.list(page, size));
    }

    @GetMapping("/{id}")
    public R<UserInfoPayload> getById(@PathVariable String id) {
        return R.ok(userService.findById(id));
    }

    @PostMapping("/{id}/roles")
    public R<String> assignRoles(@PathVariable String id, @RequestBody List<String> roleIds) {
        userService.assignRoles(id, roleIds);
        return R.ok("角色分配成功");
    }

    @PutMapping("/{id}/status")
    public R<String> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok("状态更新成功");
    }
}
