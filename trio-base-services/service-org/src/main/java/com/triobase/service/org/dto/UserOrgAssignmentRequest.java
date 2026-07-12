package com.triobase.service.org.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserOrgAssignmentRequest {
    private String dimensionCode;
    private List<String> orgUnitIds;
    private String primaryOrgUnitId;
    private List<UserOrgAssignmentItem> assignments;
}
