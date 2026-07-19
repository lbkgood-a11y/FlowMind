package com.triobase.service.action.controller;

import com.triobase.common.action.definition.ActionDefinition;
import com.triobase.common.core.result.R;
import com.triobase.service.action.dto.ActionDefinitionDiagnostic;
import com.triobase.service.action.dto.ActionDefinitionSyncRequest;
import com.triobase.service.action.dto.ActionDefinitionSyncResponse;
import com.triobase.service.action.service.ActionDefinitionRegistry;
import com.triobase.service.action.service.ActionDefinitionSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/actions/definitions")
@RequiredArgsConstructor
public class ActionDefinitionController {

    private final ActionDefinitionRegistry registry;
    private final ActionDefinitionSyncService syncService;

    @GetMapping
    public R<List<ActionDefinition>> list() {
        return R.ok(registry.all());
    }

    @GetMapping("/diagnostics")
    public R<List<ActionDefinitionDiagnostic>> diagnostics() {
        return R.ok(syncService.diagnostics());
    }

    @GetMapping("/{actionType}/diagnostics")
    public R<ActionDefinitionDiagnostic> diagnostic(@PathVariable String actionType) {
        return R.ok(syncService.diagnostic(actionType));
    }

    @PostMapping("/sync")
    public R<ActionDefinitionSyncResponse> sync(@RequestBody ActionDefinitionSyncRequest request) {
        return R.ok(syncService.sync(request));
    }
}
