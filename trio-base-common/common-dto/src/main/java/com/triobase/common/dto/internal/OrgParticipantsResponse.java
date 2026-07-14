package com.triobase.common.dto.internal;

import lombok.Data;

import java.util.List;

@Data
public class OrgParticipantsResponse {
    private String orgUnitId;
    private String dimensionCode;
    private List<ResolvedUserDto> users;
}
