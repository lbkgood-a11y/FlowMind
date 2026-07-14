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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantResolverTest {

    @Mock
    private AuthParticipantClient authParticipantClient;

    @Mock
    private OrgParticipantClient orgParticipantClient;

    private WorkflowIntegrationProperties properties;
    private ParticipantResolver participantResolver;

    @BeforeEach
    void setUp() {
        properties = new WorkflowIntegrationProperties();
        participantResolver = new ParticipantResolver(
                authParticipantClient, orgParticipantClient, properties);
    }

    @Test
    void resolvesRoleAndDeduplicatesUsers() {
        RoleParticipantsResponse response = new RoleParticipantsResponse();
        response.setRoleCode("FINANCE");
        response.setUsers(List.of(
                new ResolvedUserDto("u1", "Alice"),
                new ResolvedUserDto("u1", "Alice Duplicate"),
                new ResolvedUserDto("u2", "Bob")));
        when(authParticipantClient.resolveRole("FINANCE")).thenReturn(R.ok(response));

        ProcessPackageDefinition.Assignment assignment = assignment("ROLE");
        assignment.setRoleCode("FINANCE");

        ResolvedParticipants result = participantResolver.resolve(assignment);

        assertEquals("ROLE", result.getAssignmentType());
        assertEquals("FINANCE", result.getAssignmentRef());
        assertEquals(List.of("u1", "u2"),
                result.getUsers().stream().map(ResolvedUserDto::getUserId).toList());
        assertEquals(64, result.getParticipantVersion().length());
    }

    @Test
    void disabledDirectUserResolvesToEmptySnapshot() {
        UserValidationResponse response = new UserValidationResponse();
        response.setEnabled(false);
        when(authParticipantClient.validateUser("disabled-user")).thenReturn(R.ok(response));

        ProcessPackageDefinition.Assignment assignment = assignment("USER");
        assignment.setUserId("disabled-user");

        assertEquals(0, participantResolver.resolve(assignment).getUsers().size());
    }

    @Test
    void resolvesDepartmentWithDimension() {
        OrgParticipantsResponse response = new OrgParticipantsResponse();
        response.setOrgUnitId("dept-1");
        response.setDimensionCode("administrative");
        response.setUsers(List.of(new ResolvedUserDto("u3", "Carol")));
        when(orgParticipantClient.resolveOrgUnit("dept-1", "administrative"))
                .thenReturn(R.ok(response));

        ProcessPackageDefinition.Assignment assignment = assignment("DEPT");
        assignment.setDeptCode("dept-1");
        assignment.setDimensionCode("administrative");

        assertEquals("u3", participantResolver.resolve(assignment).getUsers().getFirst().getUserId());
        verify(orgParticipantClient).resolveOrgUnit("dept-1", "administrative");
    }

    @Test
    void rejectsCandidateSetsOverConfiguredLimit() {
        properties.getParticipants().setMaxCandidates(1);
        RoleParticipantsResponse response = new RoleParticipantsResponse();
        response.setUsers(List.of(
                new ResolvedUserDto("u1", "Alice"),
                new ResolvedUserDto("u2", "Bob")));
        when(authParticipantClient.resolveRole("FINANCE")).thenReturn(R.ok(response));

        ProcessPackageDefinition.Assignment assignment = assignment("ROLE");
        assignment.setRoleCode("FINANCE");

        BizException exception = assertThrows(BizException.class,
                () -> participantResolver.resolve(assignment));
        assertEquals("PARTICIPANT_LIMIT_EXCEEDED", exception.getMessage());
    }

    private ProcessPackageDefinition.Assignment assignment(String type) {
        ProcessPackageDefinition.Assignment assignment = new ProcessPackageDefinition.Assignment();
        assignment.setType(type);
        return assignment;
    }
}
