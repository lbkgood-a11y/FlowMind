package com.triobase.service.auth.service;

import com.triobase.service.auth.mapper.AuthActionMapper;
import com.triobase.service.auth.mapper.AuthResourceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationCodeRegistryServiceTest {

    private final AuthResourceMapper resourceMapper = mock(AuthResourceMapper.class);
    private final AuthActionMapper actionMapper = mock(AuthActionMapper.class);
    private final AuthorizationCodeRegistryService service =
            new AuthorizationCodeRegistryService(resourceMapper, actionMapper);

    @Test
    void missingRegisteredCodesReturnsOnlyCodesWithoutActiveResourceAction() {
        when(resourceMapper.selectCount(any())).thenReturn(1L, 0L, 1L);
        when(actionMapper.selectCount(any())).thenReturn(1L, 0L, 1L);

        List<String> missing = service.missingRegisteredCodes(List.of(
                "/api/v1/forms:GET",
                "FORM:EXPENSE:CREATE",
                "/api/v1/unknown:GET"));

        assertThat(missing).containsExactly("/api/v1/unknown:GET");
    }

    @Test
    void missingRegisteredCodesTreatsInvalidCodesAsMissing() {
        List<String> missing = service.missingRegisteredCodes(List.of("BROKEN"));

        assertThat(missing).containsExactly("BROKEN");
    }
}
