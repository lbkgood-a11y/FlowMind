package com.triobase.service.workflow.service;

import com.triobase.common.core.exception.BizException;
import com.triobase.common.core.result.R;
import com.triobase.common.dto.internal.OrgParticipantsResponse;
import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.common.dto.internal.RoleParticipantsResponse;
import com.triobase.common.dto.internal.UserValidationResponse;
import com.triobase.service.workflow.client.AuthParticipantClient;
import com.triobase.service.workflow.client.OrgParticipantClient;
import com.triobase.service.workflow.config.WorkflowIntegrationProperties;
import com.triobase.service.workflow.dto.ProcessPackageDefinition;
import com.triobase.service.workflow.dto.ResolvedParticipants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ParticipantResolver {

    private final AuthParticipantClient authParticipantClient;
    private final OrgParticipantClient orgParticipantClient;
    private final WorkflowIntegrationProperties properties;

    public ResolvedParticipants resolve(ProcessPackageDefinition.Assignment assignment) {
        String type = normalizedType(assignment);
        String reference = assignmentReference(type, assignment);

        List<ResolvedUserDto> users = switch (type) {
            case "ROLE" -> resolveRole(reference);
            case "USER" -> resolveUser(reference);
            case "DEPT" -> resolveDepartment(reference, assignment.getDimensionCode());
            default -> throw new BizException(40000, "UNSUPPORTED_PARTICIPANT_TYPE");
        };

        Map<String, ResolvedUserDto> uniqueUsers = new LinkedHashMap<>();
        for (ResolvedUserDto user : users) {
            if (user != null && StringUtils.hasText(user.getUserId())) {
                uniqueUsers.putIfAbsent(user.getUserId(), user);
            }
        }

        if (uniqueUsers.size() > properties.getParticipants().getMaxCandidates()) {
            throw new BizException(40000, "PARTICIPANT_LIMIT_EXCEEDED");
        }

        ResolvedParticipants result = new ResolvedParticipants();
        result.setAssignmentType(type);
        result.setAssignmentRef(reference);
        result.setParticipantVersion(participantVersion(assignment));
        result.setUsers(new ArrayList<>(uniqueUsers.values()));
        return result;
    }

    public String participantVersion(ProcessPackageDefinition.Assignment assignment) {
        String type = normalizedType(assignment);
        String source = type + "|" + assignmentReference(type, assignment) + "|"
                + valueOrEmpty(assignment.getDimensionCode());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public String resolutionKey(String instanceId, String nodeId,
                                ProcessPackageDefinition.Assignment assignment) {
        return instanceId + ":" + nodeId + ":" + participantVersion(assignment);
    }

    private List<ResolvedUserDto> resolveRole(String roleCode) {
        RoleParticipantsResponse response = requireData(
                authParticipantClient.resolveRole(roleCode), "ROLE_PARTICIPANT_RESOLUTION_FAILED");
        return response.getUsers() != null ? response.getUsers() : List.of();
    }

    private List<ResolvedUserDto> resolveUser(String userId) {
        UserValidationResponse response = requireData(
                authParticipantClient.validateUser(userId), "USER_PARTICIPANT_VALIDATION_FAILED");
        if (!response.isEnabled() || response.getUser() == null) {
            return List.of();
        }
        return List.of(response.getUser());
    }

    private List<ResolvedUserDto> resolveDepartment(String departmentId, String dimensionCode) {
        OrgParticipantsResponse response = requireData(
                orgParticipantClient.resolveOrgUnit(departmentId, dimensionCode),
                "DEPT_PARTICIPANT_RESOLUTION_FAILED");
        return response.getUsers() != null ? response.getUsers() : List.of();
    }

    private String normalizedType(ProcessPackageDefinition.Assignment assignment) {
        if (assignment == null || !StringUtils.hasText(assignment.getType())) {
            throw new BizException(40000, "PARTICIPANT_ASSIGNMENT_REQUIRED");
        }
        return assignment.getType().trim().toUpperCase(Locale.ROOT);
    }

    private String assignmentReference(String type, ProcessPackageDefinition.Assignment assignment) {
        String reference = switch (type) {
            case "ROLE" -> assignment.getRoleCode();
            case "DEPT" -> assignment.getDeptCode();
            case "USER" -> assignment.getUserId();
            default -> null;
        };
        if (!StringUtils.hasText(reference)) {
            throw new BizException(40000, "PARTICIPANT_REFERENCE_REQUIRED");
        }
        return reference.trim();
    }

    private <T> T requireData(R<T> response, String errorMessage) {
        if (response == null || response.getCode() != 0 || response.getData() == null) {
            throw new BizException(50200, errorMessage);
        }
        return response.getData();
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
