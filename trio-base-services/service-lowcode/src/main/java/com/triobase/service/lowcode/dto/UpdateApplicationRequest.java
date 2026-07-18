package com.triobase.service.lowcode.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateApplicationRequest {
    private String name;
    private String description;
    private String primaryFormDefinitionId;
    private String viewPermissionCode;
    private List<ApplicationPageRequest> pages;
    private List<ApplicationActionRequest> actions;
}
