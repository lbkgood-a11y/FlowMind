package com.triobase.service.catalog.controller;

import com.triobase.common.core.annotation.RequireDataScope;
import com.triobase.common.core.annotation.RequirePermission;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.PageResult;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.catalog.BusinessTimelineEntry;
import com.triobase.common.dto.catalog.BusinessTimelineEventRecord;
import com.triobase.common.dto.catalog.BusinessTimelineQuery;
import com.triobase.service.catalog.service.BusinessTimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class BusinessTimelineController {

    private final BusinessTimelineService timelineService;

    @RequirePermission("/api/v1/business-timeline:GET")
    @RequireDataScope(resource = "BUSINESS_TIMELINE", action = "QUERY")
    @GetMapping("/api/v1/business-timeline")
    public R<PageResult<BusinessTimelineEntry>> query(@RequestParam String tenantId,
                                                      @RequestParam(required = false) String targetType,
                                                      @RequestParam(required = false) String targetId,
                                                      @RequestParam(required = false) String actionId,
                                                      @RequestParam(required = false) String actionType,
                                                      @RequestParam(required = false) String actionStatus,
                                                      @RequestParam(required = false) String ownerExecutionRef,
                                                      @RequestParam(required = false) String eventSource,
                                                      @RequestParam(required = false) String traceId,
                                                      @RequestParam(required = false) String correlationId,
                                                      @RequestParam(required = false) String actorId,
                                                      @RequestParam(required = false) String eventType,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                      Instant startTime,
                                                      @RequestParam(required = false)
                                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                                                      Instant endTime,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        String currentTenantId = SecurityContextHolder.getTenantId();
        if (!"default".equals(currentTenantId) && !currentTenantId.equals(tenantId)) {
            throw new BizException(40361, "TENANT_ACCESS_DENIED");
        }
        BusinessTimelineQuery query = new BusinessTimelineQuery();
        query.setTenantId(tenantId);
        query.setTargetType(targetType);
        query.setTargetId(targetId);
        query.setActionId(actionId);
        query.setActionType(actionType);
        query.setActionStatus(actionStatus);
        query.setOwnerExecutionRef(ownerExecutionRef);
        query.setEventSource(eventSource);
        query.setTraceId(traceId);
        query.setCorrelationId(correlationId);
        query.setActorId(actorId);
        query.setEventType(eventType);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        query.setPage(page);
        query.setSize(size);
        return R.ok(timelineService.query(query));
    }

    @PostMapping("/internal/v1/business-timeline/events")
    public R<BusinessTimelineEntry> record(@RequestBody BusinessTimelineEventRecord request) {
        return R.ok(timelineService.record(request));
    }
}
