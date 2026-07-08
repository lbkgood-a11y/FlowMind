package com.triobase.service.org.dto;

import lombok.Data;

import java.util.List;

@Data
public class UserOrgAssignmentRequest {
    private List<String> orgUnitIds;
}
