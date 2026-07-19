package com.triobase.common.dto.catalog;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BusinessPageMetadata {
    private String pagePattern;
    private String compactMode = "ERP";
    private List<PageSectionMetadata> sections = new ArrayList<>();

    @Data
    public static class PageSectionMetadata {
        private String sectionKey;
        private String displayName;
        private String sectionType;
        private Integer sortOrder;
        private List<String> fieldKeys = new ArrayList<>();
    }
}
