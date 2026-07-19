package com.triobase.service.auth.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuthorizationAdminOptionsResponse {
    private List<Option> functionActions = new ArrayList<>();
    private List<Option> dataScopes = new ArrayList<>();
    private List<Option> fieldReadModes = new ArrayList<>();
    private List<Option> fieldWriteModes = new ArrayList<>();
    private List<Option> maskStrategies = new ArrayList<>();
    private List<GuardTemplateResponse> guardTemplates = new ArrayList<>();

    @Data
    public static class Option {
        private String code;
        private String label;
        private String category;
        private String description;

        public static Option of(String code, String label, String category, String description) {
            Option option = new Option();
            option.setCode(code);
            option.setLabel(label);
            option.setCategory(category);
            option.setDescription(description);
            return option;
        }
    }
}
