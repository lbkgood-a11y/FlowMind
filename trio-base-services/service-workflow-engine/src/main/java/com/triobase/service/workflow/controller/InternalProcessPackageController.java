package com.triobase.service.workflow.controller;

import com.triobase.common.core.result.R;
import com.triobase.service.workflow.service.ProcessPackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/process-packages")
@RequiredArgsConstructor
public class InternalProcessPackageController {

    private final ProcessPackageService processPackageService;

    @GetMapping("/published/exists")
    public R<Boolean> publishedExists(@RequestParam String processKey,
                                      @RequestParam(required = false) Integer version) {
        return R.ok(processPackageService.hasPublishedPackage(processKey, version));
    }
}
