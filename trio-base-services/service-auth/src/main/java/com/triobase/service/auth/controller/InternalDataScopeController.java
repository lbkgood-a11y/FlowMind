package com.triobase.service.auth.controller;

import com.triobase.common.core.auth.DataScope;
import com.triobase.common.core.result.R;
import com.triobase.service.auth.service.AuthDataScopeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/data-scopes")
@RequiredArgsConstructor
public class InternalDataScopeController {

    private final AuthDataScopeProvider dataScopeProvider;

    @GetMapping("/effective")
    public R<DataScope> effective(@RequestParam String userId,
                                  @RequestParam String resourceCode,
                                  @RequestParam String actionCode) {
        return R.ok(dataScopeProvider.resolve(userId, resourceCode, actionCode));
    }
}
