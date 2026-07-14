package com.triobase.service.workflow.dto;

import com.triobase.common.dto.internal.ResolvedUserDto;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResolvedParticipants {
    private String assignmentType;
    private String assignmentRef;
    private String participantVersion;
    private List<ResolvedUserDto> users = new ArrayList<>();
}
