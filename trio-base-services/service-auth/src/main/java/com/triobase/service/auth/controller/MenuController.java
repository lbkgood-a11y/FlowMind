package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.auth.dto.CreateMenuRequest;
import com.triobase.service.auth.entity.SysMenu;
import com.triobase.service.auth.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @DeleteMapping("/{id}")
    public R<String> delete(@PathVariable String id) {
        menuService.delete(id);
        return R.ok("菜单已删除");
    }
}
