package com.triobase.service.auth.dto;

import lombok.Data;

@Data
public class CreatePermissionRequest {
    private String resource;
    private String action;
    private String description;
}
