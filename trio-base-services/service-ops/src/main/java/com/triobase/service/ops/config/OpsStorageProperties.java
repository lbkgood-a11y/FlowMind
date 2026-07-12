package com.triobase.service.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "ops.storage")
public class OpsStorageProperties {
    private String basePath;
    private List<String> allowedExtensions = new ArrayList<>();
    private long maxFileSizeBytes = 52_428_800L;
}
