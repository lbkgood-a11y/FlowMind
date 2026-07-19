package com.triobase.service.action.controller;

import com.triobase.common.action.model.GlobalActionRequest;
import com.triobase.common.action.model.GlobalActionResult;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.service.action.dto.ActionEventResponse;
import com.triobase.service.action.dto.ActionExecutionResponse;
import com.triobase.service.action.dto.ActionQueryCriteria;
import com.triobase.service.action.service.ActionQueryService;
import com.triobase.service.action.service.ActionRuntimePipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/actions")
@RequiredArgsConstructor
public class ActionController {

    private static final long SSE_TIMEOUT_MILLIS = 30_000L;

    private final ActionRuntimePipeline runtimePipeline;
    private final ActionQueryService queryService;

    @PostMapping
    public R<GlobalActionResult> submit(@RequestBody GlobalActionRequest request) {
        return R.ok(runtimePipeline.submit(request));
    }

    @GetMapping("/{actionId}")
    public R<ActionExecutionResponse> detail(@PathVariable String actionId) {
        return R.ok(queryService.detail(actionId));
    }

    @GetMapping("/{actionId}/events")
    public R<List<ActionEventResponse>> events(@PathVariable String actionId) {
        return R.ok(queryService.events(actionId));
    }

    @GetMapping(value = "/{actionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String actionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        try {
            for (ActionEventResponse event : queryService.events(actionId)) {
                emitter.send(SseEmitter.event()
                        .id(event.getEventId())
                        .name(event.getEventType())
                        .data(event));
            }
            emitter.complete();
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    @GetMapping
    public R<PageResult<ActionExecutionResponse>> query(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int size,
                                                        @RequestParam(required = false) String tenantId,
                                                        @RequestParam(required = false) String actionType,
                                                        @RequestParam(required = false) String actorId,
                                                        @RequestParam(required = false) String actorType,
                                                        @RequestParam(required = false) String source,
                                                        @RequestParam(required = false) String targetType,
                                                        @RequestParam(required = false) String targetId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String traceId,
                                                        @RequestParam(required = false) String correlationId,
                                                        @RequestParam(required = false) String idempotencyKey) {
        ActionQueryCriteria criteria = new ActionQueryCriteria();
        criteria.setPage(page);
        criteria.setSize(size);
        criteria.setTenantId(tenantId);
        criteria.setActionType(actionType);
        criteria.setActorId(actorId);
        criteria.setActorType(actorType);
        criteria.setSource(source);
        criteria.setTargetType(targetType);
        criteria.setTargetId(targetId);
        criteria.setStatus(status);
        criteria.setTraceId(traceId);
        criteria.setCorrelationId(correlationId);
        criteria.setIdempotencyKey(idempotencyKey);
        return R.ok(queryService.query(criteria));
    }
}
