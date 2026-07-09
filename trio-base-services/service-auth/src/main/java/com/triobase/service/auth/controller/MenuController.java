package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.dto.UpdateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public R<List<SysMenu>> list() {
        return R.ok(menuService.list());
    }

    @PostMapping
    public R<SysMenu> create(@RequestBody CreateMenuRequest request) {
        return R.ok(menuService.create(request));
    }

    @PutMapping("/{id}")
    public R<SysMenu> update(@PathVariable String id, @RequestBody UpdateMenuRequest request) {
        return R.ok(menuService.update(id, request));
    }

    @PutMapping("/{id}/status")
    public R<SysMenu> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        return R.ok(menuService.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        menuService.delete(id);
        return R.ok("菜单已删除");
    }
}
