package com.triobase.data.analytics.controller;

import com.triobase.common.core.result.R;
import com.triobase.data.analytics.dto.DocumentIngestResponse;
import com.triobase.data.analytics.dto.IngestDocumentRequest;
import com.triobase.data.analytics.service.DocumentIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/data/documents")
@RequiredArgsConstructor
public class DataDocumentController {

    private final DocumentIngestionService ingestionService;

    @PostMapping
    public R<DocumentIngestResponse> ingest(@Valid @RequestBody IngestDocumentRequest request,
                                            @RequestHeader(value = "X-Username", required = false) String operator) {
        return R.ok(ingestionService.ingest(request, operator));
    }
}
