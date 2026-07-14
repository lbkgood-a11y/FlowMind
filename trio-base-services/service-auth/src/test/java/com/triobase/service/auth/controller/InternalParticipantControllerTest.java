package com.triobase.service.auth.controller;

import com.triobase.common.core.config.InternalServiceSecurityProperties;
import com.triobase.common.core.filter.InternalServiceTokenFilter;
import com.triobase.common.dto.internal.ResolvedUserDto;
import com.triobase.common.dto.internal.RoleParticipantsResponse;
import com.triobase.service.auth.service.InternalParticipantQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InternalParticipantControllerTest {

    private InternalParticipantQueryService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(InternalParticipantQueryService.class);
        InternalServiceSecurityProperties properties = new InternalServiceSecurityProperties();
        properties.setToken("test-token");
        properties.setAllowedCallers(List.of("service-workflow-engine"));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalParticipantController(service))
                .addFilters(new InternalServiceTokenFilter(properties))
                .build();
    }

    @Test
    void rejectsMissingInternalCredentials() throws Exception {
        mockMvc.perform(get("/internal/v1/process-participants/roles/DEPT_HEAD"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsResolvedUsersForValidCredentials() throws Exception {
        RoleParticipantsResponse response = new RoleParticipantsResponse();
        response.setRoleCode("DEPT_HEAD");
        response.setUsers(List.of(new ResolvedUserDto("U001", "dept-head")));
        when(service.resolveRole("DEPT_HEAD")).thenReturn(response);

        mockMvc.perform(get("/internal/v1/process-participants/roles/DEPT_HEAD")
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_NAME, "service-workflow-engine")
                        .header(InternalServiceTokenFilter.HEADER_SERVICE_TOKEN, "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users[0].userId").value("U001"));
    }
}
