package com.triobase.service.workflow.client;

import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.PublishedFormSnapshotResponse;
import com.triobase.service.workflow.config.InternalFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "workflow-lowcode-internal",
        url = "${workflow.services.lowcode-url}",
        configuration = InternalFeignConfiguration.class)
public interface LowcodeFormClient {

    @GetMapping("/internal/v1/process-forms/{id}")
    R<PublishedFormSnapshotResponse> getPublishedForm(@PathVariable String id);
}
