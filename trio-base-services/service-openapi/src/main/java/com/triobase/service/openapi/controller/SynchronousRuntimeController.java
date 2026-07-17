package com.triobase.service.openapi.controller;
import com.fasterxml.jackson.databind.JsonNode;
import com.triobase.common.core.result.R;
import com.triobase.service.openapi.domain.enums.Environment;
import com.triobase.service.openapi.dto.SyncInvocationResponse;
import com.triobase.service.openapi.service.SynchronousInvocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/openapi/runtime") @RequiredArgsConstructor
public class SynchronousRuntimeController {
 private final SynchronousInvocationService service;
 @RequestMapping(value="/{routeKey}",method={RequestMethod.GET,RequestMethod.POST,RequestMethod.PUT,RequestMethod.PATCH,RequestMethod.DELETE})
 public R<SyncInvocationResponse> invoke(@PathVariable String routeKey,@RequestHeader("X-Environment")Environment environment,
  @RequestHeader("X-Application-Client-Id")String clientId,@RequestHeader("X-Subscription-Id")String subscriptionId,
  @RequestHeader(value="X-Max-Concurrency",defaultValue="100")long maxConcurrency,@RequestBody(required=false)JsonNode body){return R.ok(service.invoke(routeKey,environment,clientId,subscriptionId,maxConcurrency,body));}
}
