package com.triobase.service.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplaceRoleFunctionGrantsResponse {
    private String roleId;
    private int persistedCount;
    private long grantVersion;
    private long authorizationVersion;
}
