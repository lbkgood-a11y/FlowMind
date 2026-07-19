package com.triobase.service.lowcode.dto;

import lombok.Data;

/**
 * Guard requirement information exposed in runtime descriptors.
 */
@Data
public class GuardRequirementResponse {
    private String guardCode;
    private String ownerService;
    private String description;
}
