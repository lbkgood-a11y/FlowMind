package com.triobase.service.org.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class UserOrgAssignmentItem {
    private String orgUnitId;
    private Boolean primary;
    private String positionId;
    private String positionName;
    private Boolean leader;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private Integer status;
}
