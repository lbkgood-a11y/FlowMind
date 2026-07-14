package com.triobase.data.analytics.service;

import com.triobase.data.analytics.config.DataAnalyticsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final DataAnalyticsProperties properties;

    public List<String> split(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        int maxChars = Math.max(properties.getChunking().getMaxChars(), 50);
        int overlap = Math.max(Math.min(properties.getChunking().getOverlapChars(), maxChars - 1), 0);
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + maxChars, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = end - overlap;
        }
        return chunks;
    }
}
