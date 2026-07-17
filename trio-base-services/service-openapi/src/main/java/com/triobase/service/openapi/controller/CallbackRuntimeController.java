package com.triobase.service.openapi.controller;

import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.CallbackAcknowledgement;
import com.triobase.service.openapi.service.CallbackRuntimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/openapi/callbacks")
@RequiredArgsConstructor
public class CallbackRuntimeController {
    private final CallbackRuntimeService service;

    @PostMapping("/{callbackKey}")
    public ResponseEntity<String> receive(
            @PathVariable String callbackKey,
            @RequestHeader("X-Tenant-Id") String tenantId,
            @RequestHeader("X-Environment") Environment environment,
            @RequestHeader("X-Application-Client-Id") String applicationClientId,
            @RequestHeader(value = "X-Signature-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Signature-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestHeader(value = "X-Content-SHA256", required = false) String bodyHash,
            @RequestHeader(value = "X-Gateway-Authenticated", defaultValue = "false") boolean gatewayAuthenticated,
            @RequestBody(required = false) byte[] rawBody) {
        CallbackAcknowledgement acknowledgement = service.receive(
                callbackKey, tenantId, environment, applicationClientId, rawBody,
                new CallbackRuntimeService.CallbackHeaders(
                        timestamp, nonce, signature, bodyHash, gatewayAuthenticated));
        return ResponseEntity.status(acknowledgement.status())
                .contentType(MediaType.parseMediaType(acknowledgement.contentType()))
                .header("X-Callback-Inbox-Id", acknowledgement.inboxId())
                .header("X-Callback-Duplicate", Boolean.toString(acknowledgement.duplicate()))
                .body(acknowledgement.body());
    }
}
