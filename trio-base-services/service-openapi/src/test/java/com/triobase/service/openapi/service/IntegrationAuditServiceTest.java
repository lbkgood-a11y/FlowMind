package com.triobase.service.openapi.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.triobase.common.core.context.SecurityContextHolder;
import com.triobase.service.openapi.domain.entity.AuditEvent;
import com.triobase.service.openapi.infrastructure.mapper.AuditEventMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class IntegrationAuditServiceTest {

    @Mock
    private AuditEventMapper mapper;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clear();
    }

    @Test
    void recordsSanitizedTenantScopedLifecycleEvent() {
        SecurityContextHolder.set(new SecurityContextHolder.SecurityContext(
                "user-1", "owner", "tenant-a", List.of(), List.of(), 1L, 1L, 1L));
        IntegrationAuditService service = new IntegrationAuditService(mapper);

        service.success("STRUCTURE_CREATED", "STRUCTURE", "structure-1",
                JsonNodeFactory.instance.objectNode().put("version", 1));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo("tenant-a");
        assertThat(captor.getValue().getActorId()).isEqualTo("user-1");
        assertThat(captor.getValue().getOutcome()).isEqualTo("SUCCESS");
        assertThat(captor.getValue().getChangeSummary().has("version")).isTrue();
    }
}
