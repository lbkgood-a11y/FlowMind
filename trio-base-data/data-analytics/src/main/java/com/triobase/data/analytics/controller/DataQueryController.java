package com.triobase.data.analytics.controller;

import com.triobase.common.core.result.R;
import com.triobase.data.analytics.dto.HybridQueryRequest;
import com.triobase.data.analytics.dto.HybridQueryResponse;
import com.triobase.data.analytics.dto.SemanticQueryRequest;
import com.triobase.data.analytics.dto.SemanticQueryResponse;
import com.triobase.data.analytics.dto.StructuredQueryRequest;
import com.triobase.data.analytics.dto.StructuredQueryResponse;
import com.triobase.data.analytics.service.DataQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/query")
@RequiredArgsConstructor
public class DataQueryController {

    private final DataQueryService queryService;

    @PostMapping("/structured")
    public R<StructuredQueryResponse> structured(@Valid @RequestBody StructuredQueryRequest request) {
        return R.ok(queryService.structured(request));
    }

    @PostMapping("/semantic")
    public R<SemanticQueryResponse> semantic(@Valid @RequestBody SemanticQueryRequest request) {
        return R.ok(queryService.semantic(request));
    }

    @PostMapping("/hybrid")
    public R<HybridQueryResponse> hybrid(@Valid @RequestBody HybridQueryRequest request,
                                         @RequestHeader(value = "X-User-Id", required = false) String operatorId,
                                         @RequestHeader(value = "X-Username", required = false) String operatorName) {
        return R.ok(queryService.hybrid(request, operatorId, operatorName));
    }
}
