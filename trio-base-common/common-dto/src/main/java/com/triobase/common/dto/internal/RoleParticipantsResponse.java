package com.triobase.common.dto.internal;

import lombok.Data;

import java.util.List;

@Data
public class RoleParticipantsResponse {
    private String roleCode;
    private List<ResolvedUserDto> users;
}
