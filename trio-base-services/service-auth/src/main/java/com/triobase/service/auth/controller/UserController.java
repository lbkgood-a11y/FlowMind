package com.triobase.service.auth.controller;

import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.auth.UserInfoPayload;
import com.triobase.service.auth.dto.CreateUserRequest;
import com.triobase.service.auth.dto.UpdateUserRequest;
import com.triobase.service.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public R<PageResult<UserInfoPayload>> list(@RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String username,
                                                @RequestParam(required = false) String userId,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime createdStart,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                LocalDateTime createdEnd) {
        return R.ok(userService.list(page, size, keyword, username, userId, status, createdStart, createdEnd));
    }

    @GetMapping("/{id}")
    public R<UserInfoPayload> getById(@PathVariable String id) {
        return R.ok(userService.findById(id));
    }

    @PostMapping
    public R<UserInfoPayload> create(@RequestBody CreateUserRequest request) {
        return R.ok(userService.create(request));
    }

    @PutMapping("/{id}")
    public R<UserInfoPayload> update(@PathVariable String id, @RequestBody UpdateUserRequest request) {
        return R.ok(userService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable String id) {
        userService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable String id, @RequestBody List<String> roleIds) {
        userService.assignRoles(id, roleIds);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        userService.updateStatus(id, status);
        return R.ok();
    }
}
