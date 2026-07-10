package com.triobase.service.auth.controller;

import com.triobase.common.core.result.R;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.auth.dto.MenuRouteResponse;
import com.triobase.service.auth.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
public class MenuRuntimeController {

    private final MenuService menuService;

    @GetMapping("/all")
    public R<List<MenuRouteResponse>> all() {
        return R.ok(menuService.listRoutesForUser(SecurityContextHolder.getUserId()));
    }
}
