package com.triobase.service.openapi.controller;

import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.entity.CallbackInbox;
import com.triobase.service.openapi.dto.ResolveCallbackQuarantineRequest;
import com.triobase.service.openapi.service.CallbackQuarantineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/openapi/management/callback-quarantine")
@RequiredArgsConstructor
public class CallbackQuarantineController {
    private static final String READ = "/api/v1/openapi/management/callback-quarantine:GET";
    private static final String WRITE = "/api/v1/openapi/management/callback-quarantine:POST";
    private final CallbackQuarantineService service;

    @GetMapping
    @RequirePermission(READ)
    public R<List<CallbackInbox>> list(@RequestParam(defaultValue = "50") int limit) {
        return R.ok(service.list(limit));
    }

    @PostMapping("/{inboxId}/resolve")
    @RequirePermission(WRITE)
    public R<CallbackInbox> resolve(@PathVariable String inboxId,
                                    @Valid @RequestBody ResolveCallbackQuarantineRequest request) {
        return R.ok(service.resolve(inboxId, request));
    }
}
