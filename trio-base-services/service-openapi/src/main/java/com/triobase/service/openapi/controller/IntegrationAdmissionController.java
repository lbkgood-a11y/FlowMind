package com.triobase.service.openapi.controller;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.integration.IntegrationAdmissionDecision;
import com.triobase.common.dto.integration.IntegrationAdmissionRequest;
import com.triobase.service.openapi.service.IntegrationAdmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/v1/openapi/internal") @RequiredArgsConstructor
public class IntegrationAdmissionController {
 private final IntegrationAdmissionService service;
 @PostMapping("/admission") public R<IntegrationAdmissionDecision> admit(@RequestBody IntegrationAdmissionRequest request){return R.ok(service.admit(request));}
}
